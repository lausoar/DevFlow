package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ChatMessageMapper;
import com.devflow.mapper.DocumentMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.FullTextSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全文检索实现。
 *
 * 使用 MySQL 的 MATCH ... AGAINST (IN BOOLEAN MODE) + ngram 解析器：
 * ngram 会把中文按 2 字切分建立倒排索引，因此"登录失败"这类中文词可以走索引。
 *
 * 重要限制（实测确认）：ngram_token_size 默认 2，**单字查询在 BOOLEAN MODE 下命中不了**；
 * 且英文/数字需要用 `k8*` 形式做前缀通配。因此对短查询与英文词回落 LIKE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullTextSearchServiceImpl implements FullTextSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final DocumentMapper documentMapper;
    private final ChatMessageMapper chatMessageMapper;

    /** 每个实体的检索列与表名 */
    private record EntitySpec(String table, String matchColumns) {
    }

    private static final Map<String, EntitySpec> SPECS = Map.of(
            "TASK", new EntitySpec("task", "title, description"),
            "BUG", new EntitySpec("bug", "title, description"),
            "REQUIREMENT", new EntitySpec("requirement", "title, description"),
            "DOCUMENT", new EntitySpec("document", "title, content"),
            "CHAT", new EntitySpec("chat_message", "content"));

    /** 缓存的可用性（第一次探测后复用，避免每次查询都打 information_schema） */
    private volatile Boolean available = null;

    @Override
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND index_type = 'FULLTEXT'",
                    Integer.class);
            available = n != null && n > 0;
        } catch (Exception e) {
            log.warn("全文索引可用性探测失败，回落到 LIKE: {}", e.getMessage());
            available = false;
        }
        return Boolean.TRUE.equals(available);
    }

    @Override
    public List<String> searchIds(String entity, String keyword, List<String> projectIds, int limit) {
        if (!StringUtils.hasText(keyword) || projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        EntitySpec spec = SPECS.get(entity.toUpperCase());
        if (spec == null) {
            return List.of();
        }
        String kw = keyword.trim();
        int effectiveLimit = Math.min(Math.max(limit, 1), 200);

        // 短查询（单字/单字母）与纯英文数字：ngram 默认 2 字窗口命中不了，回落 LIKE
        if (!isAvailable() || kw.length() < 2 || kw.matches("^[A-Za-z0-9\\s]+$")) {
            return likeFallback(entity, kw, projectIds, effectiveLimit);
        }

        String placeholders = String.join(",", projectIds.stream().map(p -> "?").toList());
        // BOOLEAN MODE：不加引号，按词检索；中文由 ngram 切分
        String sql = "SELECT id FROM " + spec.table()
                + " WHERE MATCH(" + spec.matchColumns() + ") AGAINST (? IN BOOLEAN MODE)"
                + " AND project_id IN (" + placeholders + ")"
                + " AND deleted = 0"
                + " ORDER BY MATCH(" + spec.matchColumns() + ") AGAINST (? IN BOOLEAN MODE) DESC"
                + " LIMIT " + effectiveLimit;
        List<Object> args = new ArrayList<>();
        args.add(kw);
        args.addAll(projectIds);
        args.add(kw);
        try {
            return jdbcTemplate.queryForList(sql, String.class, args.toArray());
        } catch (Exception e) {
            log.warn("全文检索失败，回落 LIKE: entity={}, 原因={}", entity, e.getMessage());
            return likeFallback(entity, kw, projectIds, effectiveLimit);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> fetchByIds(String entity, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Object> rows = switch (entity.toUpperCase()) {
            case "TASK" -> new ArrayList<>(taskMapper.selectBatchIds(ids));
            case "BUG" -> new ArrayList<>(bugMapper.selectBatchIds(ids));
            case "REQUIREMENT" -> new ArrayList<>(requirementMapper.selectBatchIds(ids));
            case "DOCUMENT" -> new ArrayList<>(documentMapper.selectBatchIds(ids));
            case "CHAT" -> new ArrayList<>(chatMessageMapper.selectBatchIds(ids));
            default -> List.of();
        };
        // selectBatchIds 不保证顺序，这里按传入 id 顺序重排（相关度顺序来自 searchIds）
        Map<String, Object> byId = new LinkedHashMap<>();
        for (Object row : rows) {
            byId.put(idOf(row), row);
        }
        List<T> ordered = new ArrayList<>();
        for (String id : ids) {
            Object row = byId.get(id);
            if (row != null) {
                ordered.add((T) row);
            }
        }
        return ordered;
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullTextAvailable", isAvailable());
        m.put("strategy", isAvailable() ? "FULLTEXT(ngram)，短查询回落 LIKE" : "LIKE（无全文索引）");
        m.put("entities", SPECS.keySet());
        return m;
    }

    // ==================== 内部 ====================

    /**
     * LIKE 回落：走原生 SQL 而不是 QueryWrapper。
     * 原因是 QueryWrapper<Object> 无法赋给带泛型的 selectList（类型不兼容），
     * 而这里本来就需要按实体拼列名，原生 SQL 更直接。
     */
    private List<String> likeFallback(String entity, String kw, List<String> projectIds, int limit) {
        String like = "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String placeholders = String.join(",", projectIds.stream().map(p -> "?").toList());
        String type = entity.toUpperCase();
        String sql;
        List<Object> args = new ArrayList<>();
        args.add(like);
        args.add(like);
        args.addAll(projectIds);

        switch (type) {
            case "TASK", "BUG", "REQUIREMENT" ->
                    sql = "SELECT id FROM " + tableOf(type)
                            + " WHERE (title LIKE ? OR description LIKE ?)"
                            + " AND project_id IN (" + placeholders + ") AND deleted = 0"
                            + " ORDER BY created_at DESC LIMIT " + limit;
            case "DOCUMENT" ->
                    sql = "SELECT id FROM document"
                            + " WHERE (title LIKE ? OR content LIKE ?)"
                            + " AND project_id IN (" + placeholders + ") AND deleted = 0"
                            + " ORDER BY created_at DESC LIMIT " + limit;
            case "CHAT" ->
                    sql = "SELECT id FROM chat_message"
                            + " WHERE content LIKE ?"
                            + " AND project_id IN (" + placeholders + ") AND is_deleted = 0"
                            + " ORDER BY created_at DESC LIMIT " + limit;
            default -> {
                return List.of();
            }
        }
        // CHAT 的第二个 like 参数不参与 SQL（只有 content 一列），去掉它保持参数个数一致
        if ("CHAT".equals(type)) {
            args.remove(0);
        }
        try {
            return jdbcTemplate.queryForList(sql, String.class, args.toArray());
        } catch (Exception e) {
            log.warn("LIKE 回落查询失败: entity={}, 原因={}", entity, e.getMessage());
            return List.of();
        }
    }

    private String tableOf(String entity) {
        return switch (entity) {
            case "TASK" -> "task";
            case "BUG" -> "bug";
            case "REQUIREMENT" -> "requirement";
            case "DOCUMENT" -> "document";
            case "CHAT" -> "chat_message";
            default -> entity.toLowerCase();
        };
    }

    private String idOf(Object row) {
        if (row instanceof com.devflow.entity.Task t) return t.getId();
        if (row instanceof com.devflow.entity.Bug b) return b.getId();
        if (row instanceof com.devflow.entity.Requirement r) return r.getId();
        if (row instanceof com.devflow.entity.Document d) return d.getId();
        if (row instanceof com.devflow.entity.ChatMessage c) return c.getId();
        return null;
    }
}
