-- V8: server-side session invalidation support and default demo account quarantine.

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_user'
    AND column_name = 'session_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- Match both username and the exact V2 hash: changed passwords are never quarantined.
UPDATE sys_user
SET status = 'SUSPENDED', must_change_password = 1
WHERE username IN ('admin', 'zhangsan')
  AND password = '$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale';
