package com.devflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动自检：确认 schema.sql 里"必须存在"的东西真的存在。
 *
 * 为什么需要这个：schema.sql 是 `spring.sql.init` 执行的，而它用的是 ScriptUtils，
 * 只按 ";" 切分语句，**不认识 DELIMITER**。曾经有一段幂等建索引的写法用了
 * CREATE PROCEDURE + DELIMITER $$，于是：
 *   - "DELIMITER $$" 本身是非法 SQL → 失败
 *   - "END$$" 与 "DELIMITER ;" 各自失败 → 存储过程从未创建
 *   - 后面 7 条 CALL 全部 "PROCEDURE does not exist" → 7 个索引一个都没建
 * 因为 sql.init.continue-on-error=true，这些错误全被静默吞掉，服务照常启动。
 *
 * 更糟的是集成验证脚本用 mysql 命令行客户端执行同一个文件（它认识 DELIMITER），
 * 所以验证是绿的 —— 缺陷只存在于"用应用启动"这条路径上。
 *
 * 这里在启动后主动核对关键对象，把"静默失败"变成一条明确的 ERROR 日志 + 一个
 * 非零退出码的失败项。宁可启动后立刻报警，也不要带着缺失的索引跑几个月。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaSelfCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** 必须有索引的热点查询列（与 schema.sql 末尾那批幂等建索引保持一致） */
    private static final String[][] REQUIRED_INDEXES = {
            {"task", "idx_task_fix_version"},
            {"bug", "idx_bug_fix_version"},
            {"requirement", "idx_requirement_fix_version"},
            {"test_execution", "idx_execution_cycle"},
            {"requirement", "idx_requirement_assignee"},
            {"design_asset", "idx_status"},
            {"test_case", "idx_review_status"},
            // 审计 §D3 点名的热点过滤列
            {"chat_channel", "idx_project_id"},
            {"bug", "idx_bug_assignee"},
            {"notification", "idx_user_unread"},
            {"activity_log", "idx_activity_ent_created"},
            {"chat_message", "idx_msg_channel_created"},
            {"document", "idx_doc_parent"},
    };

    /** 必须有全文索引的表（检索降级为 LIKE 时性能会塌，属于必须知道的事） */
    private static final String[] FULLTEXT_TABLES = {
            "document", "task", "bug", "requirement", "chat_message"
    };

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();

        for (String[] pair : REQUIRED_INDEXES) {
            if (!indexExists(pair[0], pair[1])) {
                problems.add("缺少索引 " + pair[0] + "." + pair[1]);
            }
        }
        for (String table : FULLTEXT_TABLES) {
            if (!hasFullTextIndex(table)) {
                problems.add("缺少全文索引 " + table + "（检索会退化成 LIKE 全表扫描）");
            }
        }

        if (problems.isEmpty()) {
            log.info("schema 自检通过：{} 个热点索引、{} 个全文索引均在位",
                    REQUIRED_INDEXES.length, FULLTEXT_TABLES.length);
            return;
        }

        log.error("""
                ============================================================
                schema 自检失败：{} 项缺失，数据库结构与代码预期不一致。
                这通常意味着 schema.sql 里有语句在启动时执行失败但被
                continue-on-error 吞掉了（例如用了 DELIMITER 的存储过程）。
                请对照上面的清单检查 schema.sql，并确认用 mysql 客户端与
                用应用启动两种方式执行的结果一致。
                ============================================================""",
                problems.size());
        problems.forEach(p -> log.error("  - {}", p));
    }

    private boolean indexExists(String table, String index) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        return n != null && n > 0;
    }

    private boolean hasFullTextIndex(String table) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_type = 'FULLTEXT'",
                Integer.class, table);
        return n != null && n > 0;
    }
}
