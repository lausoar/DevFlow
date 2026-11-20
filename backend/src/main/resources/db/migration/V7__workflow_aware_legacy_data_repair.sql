-- V7: workflow-aware repair of legacy status codes and the tenant-aware role key.
-- A project workflow wins; the global default is used only when no project workflow exists.
-- Items without a resolvable workflow are deliberately left unchanged.

-- Business columns must accept every code allowed by workflow_status.code (VARCHAR(40)).
ALTER TABLE requirement
    MODIFY COLUMN status VARCHAR(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN'
    COMMENT '状态: 取值来自项目工作流配置（默认 OPEN/COMPLETED/CLOSED）';
ALTER TABLE task
    MODIFY COLUMN status VARCHAR(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO'
    COMMENT '状态: 取值来自项目工作流配置（默认 TODO/DOING/TESTING/DONE）';

UPDATE requirement r
SET r.status = (
    SELECT ws.code
    FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = r.project_id AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1))
    ORDER BY ws.is_initial DESC, ws.sort, ws.id
    LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = r.project_id AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1))
      AND ws.code = r.status)
  AND EXISTS (
    SELECT 1 FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = r.project_id AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'REQUIREMENT' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1)));

UPDATE task t
SET t.status = (
    SELECT ws.code
    FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = t.project_id AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1))
    ORDER BY ws.is_initial DESC, ws.sort, ws.id
    LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = t.project_id AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1))
      AND ws.code = t.status)
  AND EXISTS (
    SELECT 1 FROM workflow_status ws
    WHERE ws.workflow_id = COALESCE(
        (SELECT w.id FROM workflow w
         WHERE w.project_id = t.project_id AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1),
        (SELECT w.id FROM workflow w
         WHERE w.project_id IS NULL AND w.issue_type = 'TASK' AND w.deleted = 0
         ORDER BY w.is_default DESC, w.id LIMIT 1)));

-- Older databases may have no workflow rows at all; runtime lazily creates these built-ins.
UPDATE requirement r
SET r.status = 'OPEN'
WHERE (r.status IS NULL OR r.status NOT IN ('OPEN', 'COMPLETED', 'CLOSED'))
  AND NOT EXISTS (
    SELECT 1 FROM workflow w
    JOIN workflow_status ws ON ws.workflow_id = w.id
    WHERE w.issue_type = 'REQUIREMENT' AND w.deleted = 0
      AND (w.project_id = r.project_id OR w.project_id IS NULL));

UPDATE task t
SET t.status = CASE t.status
    WHEN 'IN_PROGRESS' THEN 'DOING'
    WHEN 'IN_REVIEW' THEN 'TESTING'
    ELSE 'TODO' END
WHERE (t.status IS NULL OR t.status NOT IN ('TODO', 'DOING', 'TESTING', 'DONE'))
  AND NOT EXISTS (
    SELECT 1 FROM workflow w
    JOIN workflow_status ws ON ws.workflow_id = w.id
    WHERE w.issue_type = 'TASK' AND w.deleted = 0
      AND (w.project_id = t.project_id OR w.project_id IS NULL));

-- V3 only checked the index name. Inspect its uniqueness and complete ordered column list.
SELECT IF(COUNT(*) = 4
          AND MAX(non_unique) = 0
          AND COUNT(DISTINCT seq_in_index) = 4
          AND SUM(sub_part IS NOT NULL) = 0
          AND GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') =
              'enterprise_id,scope,role,permission_code', 1, 0)
INTO @role_perm_index_ok
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_role_permission'
  AND index_name = 'uk_role_perm';

SELECT IF(COUNT(*) > 0, 1, 0) INTO @role_perm_index_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_role_permission'
  AND index_name = 'uk_role_perm';

-- Never discard rows to make the constraint fit. A duplicate four-tuple aborts V7.
SELECT COUNT(*) INTO @role_perm_duplicates
FROM (
    SELECT enterprise_id, scope, role, permission_code
    FROM sys_role_permission
    GROUP BY enterprise_id, scope, role, permission_code
    HAVING COUNT(*) > 1
) duplicate_role_perm;

-- A named NOT NULL guard gives a clear, MySQL-8-compatible preflight abort without SIGNAL.
CREATE TEMPORARY TABLE IF NOT EXISTS v7_role_perm_preflight (
    v7_duplicate_role_perm_four_tuple INT NOT NULL
) ENGINE = MEMORY;
SET @ddl := IF(@role_perm_index_ok = 1 OR @role_perm_duplicates = 0,
    'DO 0',
    'INSERT INTO v7_role_perm_preflight (v7_duplicate_role_perm_four_tuple) VALUES (NULL)');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
DROP TEMPORARY TABLE IF EXISTS v7_role_perm_preflight;

-- Replace the same-named wrong index atomically; do not drop it before the duplicate guard.
SET @ddl := IF(@role_perm_index_ok = 1, 'DO 0',
    IF(@role_perm_index_exists = 1,
        'ALTER TABLE `sys_role_permission` DROP INDEX `uk_role_perm`, ADD UNIQUE KEY `uk_role_perm` (`enterprise_id`, `scope`, `role`, `permission_code`)',
        'ALTER TABLE `sys_role_permission` ADD UNIQUE KEY `uk_role_perm` (`enterprise_id`, `scope`, `role`, `permission_code`)'));
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
