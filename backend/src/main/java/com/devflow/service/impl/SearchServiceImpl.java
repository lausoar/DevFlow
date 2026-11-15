package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.entity.Bug;
import com.devflow.entity.Document;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.DocumentMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索实现。
 * 范围：用户可见的项目（项目成员；企业超管含本企业全部项目），口径统一走 ProjectAccessService，
 * 与项目列表一致。搜索项目名称/描述 + 各实体标题；每类至多 10 条，按创建时间倒序。
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final com.devflow.service.ProjectAccessService projectAccessService;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final DocumentMapper documentMapper;
    private final com.devflow.service.FullTextSearchService fullTextSearchService;

    /** 每类结果上限 */
    private static final int LIMIT = 10;

    @Override
    public Map<String, Object> search(String userId, String keyword) {
        Map<String, Object> result = new HashMap<>();
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return emptyGroups(result);
        }

        List<String> projectIds = projectAccessService.visibleProjectIds(userId);
        if (projectIds.isEmpty()) {
            return emptyGroups(result);
        }

        String like = escapeLike(q);

        // 项目：名称/描述
        result.put("projects", projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getId, projectIds)
                        .and(w -> w.like(Project::getName, like).or().like(Project::getDescription, like))
                        .orderByDesc(Project::getCreatedAt)
                        .last("LIMIT " + LIMIT))
                .stream().map(p -> item(p.getId(), p.getName(), p.getId(),
                        orEmpty(p.getDescription()), p.getCreatedAt())).toList());

        // 任务 / Bug / 需求 / 文档：走全文检索（标题 + 正文/描述），
        // 短查询与纯英文词在 fullTextSearchService 内部回落 LIKE。
        List<Task> tasks = fullTextSearchService.fetchByIds("TASK",
                fullTextSearchService.searchIds("TASK", q, projectIds, LIMIT));
        result.put("tasks", tasks.stream().map(t -> item(t.getId(), t.getTitle(), t.getProjectId(),
                statusLabel(t.getStatus()), t.getCreatedAt())).toList());

        List<Bug> bugs = fullTextSearchService.fetchByIds("BUG",
                fullTextSearchService.searchIds("BUG", q, projectIds, LIMIT));
        result.put("bugs", bugs.stream().map(b -> item(b.getId(), b.getTitle(), b.getProjectId(),
                statusLabel(b.getStatus()), b.getCreatedAt())).toList());

        List<Requirement> requirements = fullTextSearchService.fetchByIds("REQUIREMENT",
                fullTextSearchService.searchIds("REQUIREMENT", q, projectIds, LIMIT));
        result.put("requirements", requirements.stream().map(r -> item(r.getId(), r.getTitle(), r.getProjectId(),
                statusLabel(r.getStatus()), r.getCreatedAt())).toList());

        // 文档：同时检索正文（此前只查标题，正文完全搜不到）
        List<Document> documents = fullTextSearchService.fetchByIds("DOCUMENT",
                fullTextSearchService.searchIds("DOCUMENT", q, projectIds, LIMIT));
        result.put("documents", documents.stream().map(d -> item(d.getId(), d.getTitle(), d.getProjectId(),
                "文档", d.getCreatedAt())).toList());

        return result;
    }

    /** 统一结果条目形状：id / title / projectId / subtitle / createdAt */
    private Map<String, Object> item(String id, String title, String projectId,
                                     String subtitle, Object createdAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("projectId", projectId);
        m.put("subtitle", subtitle == null || subtitle.isBlank() ? null : subtitle);
        m.put("createdAt", createdAt);
        return m;
    }

    /** 五组全空结果 */
    private Map<String, Object> emptyGroups(Map<String, Object> result) {
        result.put("projects", List.of());
        result.put("tasks", List.of());
        result.put("bugs", List.of());
        result.put("requirements", List.of());
        result.put("documents", List.of());
        return result;
    }

    /** LIKE 通配符转义，避免用户输入 % _ 影响匹配 */
    private String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String statusLabel(String status) {
        return status == null ? null : status;
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
