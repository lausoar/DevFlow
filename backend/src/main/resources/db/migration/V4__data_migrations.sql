-- ============================================
-- V4：一次性历史数据迁移（审计报告 §C12）
-- ============================================
-- 这些语句原先住在 schema.sql 里，每次启动都执行。其中"把所有非三态的需求
-- 状态改成 OPEN"会在每次重启后把用户刚完成的/已关闭的需求重开（已复现）。
--
-- 现在它们由 Flyway 保证只执行一次，schema_migration 的标记保留是为了
-- 与第 9 批已经跑过的库兼容（那些库的标记已存在，NOT EXISTS 会跳过）。
--
-- 顺序要求：必须在 V3（结构补齐）之后 —— 本文件引用了 V3 补出来的列
-- （例如 task.completed_at）。

UPDATE sys_user SET role='MANAGER'
  WHERE role IN ('DIRECTOR','SUPERVISOR')
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-user-role-7to4');

UPDATE sys_user SET role='MEMBER'
  WHERE role='LEADER'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-user-role-7to4');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-user-role-7to4');

UPDATE notification n JOIN bug b ON SUBSTRING(n.action_url, 7) = b.id
  SET n.action_url = CONCAT('/projects/', b.project_id, '?tab=bugs')
  WHERE n.action_url LIKE '/bugs/%'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-23-notification-url');

UPDATE notification n JOIN task t ON SUBSTRING(n.action_url, 8) = t.id
  SET n.action_url = CONCAT('/projects/', t.project_id, '?tab=tasks')
  WHERE n.action_url LIKE '/tasks/%'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-23-notification-url');

UPDATE notification SET action_url = NULL
  WHERE (action_url LIKE '/bugs/%' OR action_url LIKE '/tasks/%')
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-23-notification-url');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-23-notification-url');

UPDATE requirement SET status = 'OPEN'
  WHERE (status IS NULL OR status NOT IN ('OPEN', 'COMPLETED', 'CLOSED'))
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-requirement-status-three-state');

UPDATE requirement SET reject_reason = NULL, approver_id = NULL
  WHERE (reject_reason IS NOT NULL OR approver_id IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-requirement-status-three-state');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-requirement-status-three-state');

UPDATE project SET git_webhook_secret = REPLACE(UUID(), '-', '')
  WHERE git_webhook_secret IS NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-webhook-secret-backfill');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-webhook-secret-backfill');

UPDATE document d JOIN sys_user u ON d.author_id = u.id SET d.enterprise_id = u.enterprise_id
  WHERE d.enterprise_id IS NULL AND u.enterprise_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-document-enterprise-backfill');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-document-enterprise-backfill');

UPDATE task t SET t.completed_at = t.updated_at
  WHERE t.completed_at IS NULL AND t.status IN (
      SELECT code FROM workflow_status WHERE category = 'DONE')
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-completed-at-backfill');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-11-task-completed-at-backfill');

UPDATE task SET status = 'DOING'
  WHERE status = 'IN_PROGRESS'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-status-normalize');

UPDATE task SET status = 'TESTING'
  WHERE status = 'IN_REVIEW'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-status-normalize');

INSERT IGNORE INTO schema_migration (name) VALUES ('2026-11-task-status-normalize');
