-- ============================================
-- V3：老库结构补齐（审计报告 §C11）
-- ============================================
-- V1 的基线全是 CREATE TABLE IF NOT EXISTS。对**全新库**它一步建全；
-- 但对**已经跑过历史 schema.sql 的老库**，表已存在 → CREATE 被整条跳过。
-- 于是历史上那些靠 continue-on-error 执行的 ALTER，只要失败过一次
-- （重复列、历史数据不满足约束、或干脆是 DELIMITER 解析问题），
-- 缺的列/索引就永久缺失，而且没有任何日志会告诉你。
--
-- 为什么排在数据迁移（V4）之前：V4 里有一条 `UPDATE task SET completed_at = ...`，
-- 而 completed_at 正是本迁移补的列。老库上如果先跑 V4 就会报
-- "Unknown column 't.completed_at' in 'where clause'"（真实踩到过）。
-- 结构永远先于数据。
--
-- 本迁移把历史 schema.sql 里所有 ADD COLUMN / ADD KEY 原样重放一遍，
-- 但每一条都先查 information_schema，不存在才执行。因此：
--   - 全新库（V1 刚建好）→ 全部跳过，零操作；
--   - 结构完整的老库 → 同样零操作；
--   - 缺少结构的老库 → 缺什么补什么。
--
-- 这是把「首次失败后约束永久缺失且无人知晓」真正堵住的那一步：
-- 缺失现在既**可检测**（SchemaSelfCheck 启动自检）又**可修复**（本迁移）。
--
-- 注意：这里刻意不用 DELIMITER —— Flyway 的解析器也不认它（与 Spring 的
-- ScriptUtils 同理），用 SET @ddl / PREPARE 才是两边都安全的写法。
--
-- 另一个坑：列定义里带 COMMENT '...'，把 DDL 塞进单引号字符串时必须
-- 把内部单引号双写成 ''，否则字符串会被提前截断（这条踩过一次）。

-- project.invite_code
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN invite_code VARCHAR(16) DEFAULT NULL COMMENT ''项目邀请码'' AFTER owner_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'invite_code');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- project.uk_project_invite_code
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD UNIQUE KEY uk_project_invite_code (invite_code)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'project' AND index_name = 'uk_project_invite_code');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.reporter_type
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN reporter_type VARCHAR(10) NOT NULL DEFAULT ''USER'' COMMENT ''报告人类型: USER/GUEST'' AFTER reporter_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'reporter_type');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.guest_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN guest_id VARCHAR(36) DEFAULT NULL COMMENT ''游客ID（reporter_type=GUEST时）'' AFTER reporter_type', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'guest_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.guest_nickname
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN guest_nickname VARCHAR(50) DEFAULT NULL COMMENT ''游客昵称'' AFTER guest_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'guest_nickname');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.pending
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN pending TINYINT NOT NULL DEFAULT 0 COMMENT ''游客提交待确认: 0-否 1-待确认'' AFTER guest_nickname', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'pending');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.real_name
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN real_name VARCHAR(50) DEFAULT NULL COMMENT ''真实姓名（展示用）'' AFTER username', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'real_name');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- document.version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document` ADD COLUMN version INT DEFAULT 1 COMMENT ''版本号（每次保存+1）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'document' AND column_name = 'version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- enterprise.wecom_webhook
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `enterprise` ADD COLUMN wecom_webhook VARCHAR(500) DEFAULT NULL COMMENT ''企业微信群机器人 Webhook 地址（空=不推送）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'enterprise' AND column_name = 'wecom_webhook');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- project.git_repo_url
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN git_repo_url VARCHAR(500) DEFAULT NULL COMMENT ''Git 仓库地址''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'git_repo_url');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- project.git_webhook_secret
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN git_webhook_secret VARCHAR(32) DEFAULT NULL COMMENT ''Git Webhook 密钥''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'git_webhook_secret');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- document.enterprise_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document` ADD COLUMN enterprise_id VARCHAR(36) DEFAULT NULL COMMENT ''所属企业（全局文档按企业隔离）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'document' AND column_name = 'enterprise_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_role_permission.enterprise_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_role_permission` ADD COLUMN enterprise_id VARCHAR(36) NOT NULL DEFAULT ''*'' COMMENT ''企业ID，* 为全局默认模板'' AFTER id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND column_name = 'enterprise_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_role_permission.uk_role_perm
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_role_permission` ADD UNIQUE KEY uk_role_perm (enterprise_id, scope, role, permission_code)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND index_name = 'uk_role_perm');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_channel.idx_project
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_channel` ADD KEY idx_project (project_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chat_channel' AND index_name = 'idx_project');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.idx_assignee
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD KEY idx_assignee (assignee_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_assignee');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- department_member.idx_enterprise
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `department_member` ADD KEY idx_enterprise (enterprise_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'department_member' AND index_name = 'idx_enterprise');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- notification.idx_user_read_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `notification` ADD KEY idx_user_read_created (user_id, is_read, created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'notification' AND index_name = 'idx_user_read_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.idx_enterprise_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD KEY idx_enterprise_created (enterprise_id, created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND index_name = 'idx_enterprise_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- file_item.idx_url
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `file_item` ADD KEY idx_url (url(191))', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'file_item' AND index_name = 'idx_url');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.idx_channel_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD KEY idx_channel_created (channel_id, created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_channel_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- document.idx_parent
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document` ADD KEY idx_parent (parent_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'document' AND index_name = 'idx_parent');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- task.fix_version_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT ''修复版本（project_release.id）'' AFTER sprint_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'fix_version_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.fix_version_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT ''修复版本（project_release.id）'' AFTER environment', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'fix_version_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.fix_version_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT ''交付版本（project_release.id）'' AFTER estimated_hours', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'fix_version_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.assignee_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN assignee_id VARCHAR(36) DEFAULT NULL COMMENT ''需求负责人'' AFTER requester_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'assignee_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- task.design_status
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN design_status VARCHAR(20) DEFAULT NULL COMMENT ''设计状态'' AFTER status', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'design_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.design_status
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN design_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''设计状态'' AFTER status', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'design_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_case.version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_case` ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''用例版本号'' AFTER expected_result', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'test_case' AND column_name = 'version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_case.review_status
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_case` ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT'' COMMENT ''评审状态'' AFTER version', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'test_case' AND column_name = 'review_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_execution.cycle_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_execution` ADD COLUMN cycle_id VARCHAR(36) DEFAULT NULL COMMENT ''所属测试轮次'' AFTER project_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'test_execution' AND column_name = 'cycle_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_execution.case_version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_execution` ADD COLUMN case_version INT DEFAULT NULL COMMENT ''执行时的用例版本号'' AFTER case_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'test_execution' AND column_name = 'case_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- task.completed_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN completed_at DATETIME DEFAULT NULL COMMENT ''完成时间（进入 DONE 分类时写入）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'completed_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- task.version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本号（每次更新+1）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本号（每次更新+1）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND column_name = 'version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- deployment.first_commit_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `deployment` ADD COLUMN first_commit_at DATETIME DEFAULT NULL COMMENT ''本版本首个提交时间（变更前置时间起点）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'deployment' AND column_name = 'first_commit_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- deployment.idx_env_finished
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `deployment` ADD KEY idx_env_finished (environment_id, finished_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'deployment' AND index_name = 'idx_env_finished');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.totp_secret
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN totp_secret VARCHAR(64) DEFAULT NULL COMMENT ''TOTP 密钥（Base32，启用后才写入）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'totp_secret');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.totp_enabled
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN totp_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''是否启用双因素认证''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'totp_enabled');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.totp_bound_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN totp_bound_at DATETIME DEFAULT NULL COMMENT ''绑定时间''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'totp_bound_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.must_change_password
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT ''下次登录必须修改密码''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'must_change_password');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.password_updated_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN password_updated_at DATETIME DEFAULT NULL COMMENT ''上次改密时间（密码过期策略用）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'password_updated_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.prev_hash
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD COLUMN prev_hash VARCHAR(64) DEFAULT NULL COMMENT ''上一条审计记录的哈希（链式防篡改）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND column_name = 'prev_hash');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.record_hash
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD COLUMN record_hash VARCHAR(64) DEFAULT NULL COMMENT ''本条记录的哈希''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND column_name = 'record_hash');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.ip
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT ''操作来源 IP''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND column_name = 'ip');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.user_agent
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD COLUMN user_agent VARCHAR(300) DEFAULT NULL COMMENT ''操作来源 UA''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND column_name = 'user_agent');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.idx_enterprise_id_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD KEY idx_enterprise_id_created (enterprise_id, id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND index_name = 'idx_enterprise_id_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- project.program_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN program_id VARCHAR(36) DEFAULT NULL COMMENT ''所属项目集（program.id）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'program_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- project.idx_program
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD KEY idx_program (program_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'project' AND index_name = 'idx_program');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.edited_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN edited_at DATETIME(3) DEFAULT NULL COMMENT ''编辑时间''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'edited_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.recalled_at
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN recalled_at DATETIME(3) DEFAULT NULL COMMENT ''撤回时间''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'recalled_at');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.recalled_by
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN recalled_by VARCHAR(36) DEFAULT NULL COMMENT ''撤回操作人''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'recalled_by');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.original_content
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN original_content TEXT DEFAULT NULL COMMENT ''撤回前的原文（仅审计可见）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'original_content');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.dnd_start
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN dnd_start TIME DEFAULT NULL COMMENT ''免打扰开始时间''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'dnd_start');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.dnd_end
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN dnd_end TIME DEFAULT NULL COMMENT ''免打扰结束时间''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'dnd_end');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- sys_user.dnd_enabled
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN dnd_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''是否启用免打扰''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'dnd_enabled');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- document.ft_document
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document` ADD FULLTEXT KEY ft_document (title, content) WITH PARSER ngram', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'document' AND index_name = 'ft_document');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- task.ft_task
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD FULLTEXT KEY ft_task (title, description) WITH PARSER ngram', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'ft_task');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.ft_bug
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD FULLTEXT KEY ft_bug (title, description) WITH PARSER ngram', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'ft_bug');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.ft_requirement
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD FULLTEXT KEY ft_requirement (title, description) WITH PARSER ngram', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'ft_requirement');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.ft_chat_message
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD FULLTEXT KEY ft_chat_message (content) WITH PARSER ngram', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'ft_chat_message');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;


-- ============================================
-- 类型修正（MODIFY 本身幂等，无需条件）
-- ============================================
ALTER TABLE chat_message MODIFY COLUMN created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（毫秒精度）';
ALTER TABLE task_worklog MODIFY COLUMN hours DECIMAL(5,2) NOT NULL COMMENT '工时（小时，两位小数）';
ALTER TABLE task_worklog MODIFY COLUMN note VARCHAR(500) DEFAULT NULL COMMENT '备注';
ALTER TABLE requirement MODIFY COLUMN estimated_hours DECIMAL(6,1) DEFAULT NULL COMMENT '预估工时（小时，支持小数）';

-- ============================================
-- 段落：schema.sql 里用 SET @ddl 条件创建的索引
-- ============================================
-- 这些索引历史上是通过 SET @ddl / PREPARE 创建的（第 9 批为了绕开
-- DELIMITER 才改成这样），上面的 ALTER TABLE 抽取没有覆盖到它们。
-- 老库如果缺这些索引，这里负责补回来。

-- task.idx_task_fix_version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD KEY `idx_task_fix_version` (fix_version_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_fix_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.idx_bug_fix_version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD KEY `idx_bug_fix_version` (fix_version_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_bug_fix_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.idx_requirement_fix_version
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD KEY `idx_requirement_fix_version` (fix_version_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'idx_requirement_fix_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_execution.idx_execution_cycle
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_execution` ADD KEY `idx_execution_cycle` (cycle_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'test_execution' AND index_name = 'idx_execution_cycle');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- requirement.idx_requirement_assignee
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD KEY `idx_requirement_assignee` (assignee_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'idx_requirement_assignee');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- design_asset.idx_status
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `design_asset` ADD KEY `idx_status` (status)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'design_asset' AND index_name = 'idx_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- test_case.idx_review_status
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `test_case` ADD KEY `idx_review_status` (review_status)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'test_case' AND index_name = 'idx_review_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_channel.idx_project_id
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_channel` ADD KEY `idx_project_id` (project_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chat_channel' AND index_name = 'idx_project_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- bug.idx_bug_assignee
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD KEY `idx_bug_assignee` (assignee_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_bug_assignee');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- notification.idx_user_unread
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `notification` ADD KEY `idx_user_unread` (user_id, is_read)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'notification' AND index_name = 'idx_user_unread');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- notification.idx_notification_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `notification` ADD KEY `idx_notification_created` (created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'notification' AND index_name = 'idx_notification_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- activity_log.idx_activity_ent_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `activity_log` ADD KEY `idx_activity_ent_created` (enterprise_id, created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND index_name = 'idx_activity_ent_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- file_item.idx_file_url
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `file_item` ADD KEY `idx_file_url` (url(191))', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'file_item' AND index_name = 'idx_file_url');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- chat_message.idx_msg_channel_created
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD KEY `idx_msg_channel_created` (channel_id, created_at)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_msg_channel_created');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- document.idx_doc_parent
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document` ADD KEY `idx_doc_parent` (parent_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'document' AND index_name = 'idx_doc_parent');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- department_member.idx_dept_member_ent
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `department_member` ADD KEY `idx_dept_member_ent` (enterprise_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'department_member' AND index_name = 'idx_dept_member_ent');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

