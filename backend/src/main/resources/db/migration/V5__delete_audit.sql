-- ============================================
-- V5：删除审计字段（审计报告 §D1/§D2）
-- ============================================
-- 之前只有 `deleted` 布尔标记，没有任何地方记录"谁在什么时候删的"。
-- 后果有两个，都在用的时候才暴露：
--   1. 回收站只能拿 `updated_at` 冒充"删除于" —— 那条记录在删除前如果被改过
--      标题，显示的就是改标题的时间，不是删除时间；
--   2. 出了"数据怎么没了"的事故，无法回答是哪个管理员删的。
--
-- 这里给三类工作项补两列。其余软删表（文档/评论/文件等）暂不动：
-- 它们目前没有回收站入口，补了也没有消费方，属于无用列。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''删除时间（deleted=1 时写入）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN deleted_by VARCHAR(36) DEFAULT NULL COMMENT ''删除人ID''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'deleted_by');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''删除时间（deleted=1 时写入）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN deleted_by VARCHAR(36) DEFAULT NULL COMMENT ''删除人ID''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'deleted_by');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''删除时间（deleted=1 时写入）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN deleted_by VARCHAR(36) DEFAULT NULL COMMENT ''删除人ID''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'deleted_by');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 回收站按删除时间倒序，需要索引（原查询按 updated_at 排序，走不上索引）
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD KEY `idx_task_deleted_at` (project_id, deleted, deleted_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD KEY `idx_bug_deleted_at` (project_id, deleted, deleted_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_bug_deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD KEY `idx_requirement_deleted_at` (project_id, deleted, deleted_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'idx_requirement_deleted_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 回填：历史上已删除的行没有删除时间，用 updated_at 近似
-- （与世界线一致：改不了过去，但至少不是 NULL，回收站不会显示"删除于 空"）
UPDATE task SET deleted_at = updated_at
  WHERE deleted = 1 AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-15-deleted-at-backfill');
UPDATE bug SET deleted_at = updated_at
  WHERE deleted = 1 AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-15-deleted-at-backfill');
UPDATE requirement SET deleted_at = updated_at
  WHERE deleted = 1 AND deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-15-deleted-at-backfill');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-11-15-deleted-at-backfill');
