-- ============================================
-- DevFlow 数据库结构总览（**不再由应用执行**）
-- MySQL 8.0+
-- ============================================

-- ⚠️ 本文件自第 10 批起**不再由应用执行**。
--
-- 结构迁移已交给 Flyway（backend/src/main/resources/db/migration/）：
--   V1__baseline.sql               全部表的权威定义（由本文件执行后的库导出）
--   V2__seed_data.sql              种子账号与权限矩阵
--   V3__data_migrations.sql        一次性历史数据迁移
--   V4__legacy_structure_repair.sql 老库缺失列/索引的补齐
--
-- 为什么保留这个文件：它是**人类可读的结构总览**，比 900 行 mysqldump 输出
-- 更适合查阅"某张表有哪些字段、做了什么"。改动结构时请改 V1（新库）
-- 并新增一个 Vn 迁移（老库），不要只改这里 —— 改了也不会被执行。

-- 注意：这里不再写 CREATE DATABASE / USE。
-- 原因（2026-11-15 集成验证发现）：Spring 在启动时通过 spring.sql.init 执行本脚本，
-- 其中的 USE devflow 会把**那条池化连接**的默认库切到 devflow；连接归还连接池后
-- 被后续业务查询复用，导致 DB_URL 指向别的库（如测试库）时数据仍写进 devflow。
-- 现在一律使用连接自身的当前库：JDBC URL 的 db 或 docker 初始化时的 MYSQL_DATABASE。

-- ============================================
-- 一次性数据迁移登记表
-- ============================================
-- 本脚本每次启动都执行。此前有些"历史数据归一化"写成裸 UPDATE，等于每次重启
-- 都把用户数据改回去（例如把所有需求状态重置成 OPEN）——用户改的状态活不过一次重启。
-- 需要"只跑一次"的迁移一律写成：
--     1) UPDATE ... AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name='<id>')
--     2) INSERT IGNORE INTO schema_migration (name, applied_at) VALUES ('<id>', NOW());
-- 顺序不能反：先查后用，否则标记先落库，迁移永远不执行。
CREATE TABLE IF NOT EXISTS schema_migration (
    name VARCHAR(100) PRIMARY KEY COMMENT '迁移标识（带日期，唯一）',
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间'
) ENGINE=InnoDB COMMENT='一次性数据迁移登记（保证迁移只执行一次）';

-- ============================================
-- 企业表
-- ============================================
CREATE TABLE IF NOT EXISTS enterprise (
    id VARCHAR(36) PRIMARY KEY COMMENT '企业ID',
    name VARCHAR(100) NOT NULL COMMENT '企业名称',
    logo VARCHAR(500) DEFAULT NULL COMMENT '企业Logo URL',
    invite_code VARCHAR(16) NOT NULL COMMENT '邀请码',
    owner_id VARCHAR(36) NOT NULL COMMENT '企业所有者ID',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_invite_code (invite_code)
) ENGINE=InnoDB COMMENT='企业表';

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(36) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    real_name VARCHAR(50) DEFAULT NULL COMMENT '真实姓名（展示用，空则回退用户名）',
    email VARCHAR(100) NOT NULL COMMENT '邮箱',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '角色: SUPER_ADMIN/ADMIN/MANAGER/MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/SUSPENDED',
    enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '所属企业ID',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    KEY idx_enterprise_id (enterprise_id)
) ENGINE=InnoDB COMMENT='用户表';

-- ============================================
-- 部门表
-- ============================================
CREATE TABLE IF NOT EXISTS department (
    id VARCHAR(36) PRIMARY KEY COMMENT '部门ID',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    enterprise_id VARCHAR(36) NOT NULL COMMENT '所属企业ID',
    parent_id VARCHAR(36) DEFAULT NULL COMMENT '上级部门ID',
    leader_id VARCHAR(36) DEFAULT NULL COMMENT '部门负责人ID',
    sort INT DEFAULT 0 COMMENT '排序号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_enterprise_id (enterprise_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB COMMENT='部门表';

-- ============================================
-- 部门成员关联表
-- ============================================
CREATE TABLE IF NOT EXISTS department_member (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    department_id VARCHAR(36) NOT NULL COMMENT '部门ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    enterprise_id VARCHAR(36) NOT NULL COMMENT '所属企业ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dept_user (department_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='部门成员关联表';

-- ============================================
-- 项目表
-- ============================================
CREATE TABLE IF NOT EXISTS project (
    id VARCHAR(36) PRIMARY KEY COMMENT '项目ID',
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    project_key VARCHAR(10) NOT NULL COMMENT '项目标识',
    description TEXT DEFAULT NULL COMMENT '项目描述',
    icon VARCHAR(500) DEFAULT NULL COMMENT '项目图标',
    enterprise_id VARCHAR(36) NOT NULL COMMENT '所属企业ID',
    owner_id VARCHAR(36) NOT NULL COMMENT '项目负责人ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING' COMMENT '状态: ACTIVE/ARCHIVED/PLANNING',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_project_key (enterprise_id, project_key),
    KEY idx_enterprise_id (enterprise_id)
) ENGINE=InnoDB COMMENT='项目表';

-- ============================================
-- 项目成员表
-- ============================================
CREATE TABLE IF NOT EXISTS project_member (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色: OWNER/MANAGER/MEMBER',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_project_user (project_id, user_id)
) ENGINE=InnoDB COMMENT='项目成员表';

-- ============================================
-- Sprint 表
-- ============================================
CREATE TABLE IF NOT EXISTS sprint (
    id VARCHAR(36) PRIMARY KEY COMMENT 'Sprint ID',
    name VARCHAR(100) NOT NULL COMMENT 'Sprint名称',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目ID',
    goal TEXT DEFAULT NULL COMMENT 'Sprint目标',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING/ACTIVE/COMPLETED',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_project_id (project_id)
) ENGINE=InnoDB COMMENT='Sprint表';

-- ============================================
-- 任务表
-- ============================================
CREATE TABLE IF NOT EXISTS task (
    id VARCHAR(36) PRIMARY KEY COMMENT '任务ID',
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT DEFAULT NULL COMMENT '任务描述',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目ID',
    sprint_id VARCHAR(36) DEFAULT NULL COMMENT '所属Sprint ID',
    assignee_id VARCHAR(36) DEFAULT NULL COMMENT '负责人ID',
    reporter_id VARCHAR(36) NOT NULL COMMENT '创建人ID',
    status VARCHAR(40) NOT NULL DEFAULT 'TODO' COMMENT '状态: 取值来自项目工作流配置（默认 TODO/DOING/TESTING/DONE）',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: CRITICAL/HIGH/MEDIUM/LOW',
    story_points INT DEFAULT NULL COMMENT '故事点',
    due_date DATETIME DEFAULT NULL COMMENT '截止时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_project_id (project_id),
    KEY idx_sprint_id (sprint_id),
    KEY idx_assignee_id (assignee_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='任务表';

-- ============================================
-- 任务标签表
-- ============================================
-- 任务标签：这张表目前在 Java 与前端都无任何引用（见审计 §C14 "死表"），
-- 但既然建了就补上唯一键 —— 同一任务重复打同一个标签没有业务意义，
-- 没有唯一约束时任何一次"重复提交"都会静默堆出重复行。
CREATE TABLE IF NOT EXISTS task_tag (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名',
    UNIQUE KEY uk_task_tag (task_id, tag_name)
) ENGINE=InnoDB COMMENT='任务标签表';

-- ============================================
-- 任务检查清单（同样是无引用的死表；保留是为了不破坏可能存在的历史数据，
-- 新功能的清单实现在 task_checklist 表）
-- ============================================
CREATE TABLE IF NOT EXISTS checklist_item (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    title VARCHAR(200) NOT NULL COMMENT '检查项',
    completed TINYINT DEFAULT 0 COMMENT '是否完成',
    sort INT DEFAULT 0 COMMENT '排序号',
    KEY idx_task_id (task_id)
) ENGINE=InnoDB COMMENT='任务检查清单';

-- ============================================
-- Bug 表
-- ============================================
CREATE TABLE IF NOT EXISTS bug (
    id VARCHAR(36) PRIMARY KEY COMMENT 'Bug ID',
    title VARCHAR(200) NOT NULL COMMENT 'Bug标题',
    description TEXT DEFAULT NULL COMMENT 'Bug描述',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目ID',
    assignee_id VARCHAR(36) DEFAULT NULL COMMENT '负责人ID',
    reporter_id VARCHAR(36) NOT NULL COMMENT '报告人ID',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/ASSIGNED/DOING/RESOLVED/REOPENED/CLOSED',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    severity VARCHAR(20) NOT NULL DEFAULT 'MAJOR' COMMENT '严重程度: BLOCKER/CRITICAL/MAJOR/MINOR/TRIVIAL',
    environment VARCHAR(200) DEFAULT NULL COMMENT '环境信息',
    screenshots TEXT DEFAULT NULL COMMENT '截图（JSON 数组，存储 URL 列表）',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_project_id (project_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='Bug表';

-- ============================================
-- 文档表
-- ============================================
CREATE TABLE IF NOT EXISTS document (
    id VARCHAR(36) PRIMARY KEY COMMENT '文档ID',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content LONGTEXT DEFAULT NULL COMMENT '文档内容（Markdown）',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目ID',
    author_id VARCHAR(36) NOT NULL COMMENT '作者ID',
    type VARCHAR(20) NOT NULL DEFAULT 'DOC' COMMENT '类型: DOC/WIKI/MEETING/REPORT',
    parent_id VARCHAR(36) DEFAULT NULL COMMENT '父文档ID',
    sort INT DEFAULT 0 COMMENT '排序号',
    is_published TINYINT DEFAULT 0 COMMENT '是否发布',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_id (project_id),
    KEY idx_author_id (author_id)
) ENGINE=InnoDB COMMENT='文档表';

-- ============================================
-- 聊天频道表
-- ============================================
CREATE TABLE IF NOT EXISTS chat_channel (
    id VARCHAR(36) PRIMARY KEY COMMENT '频道ID',
    name VARCHAR(100) NOT NULL COMMENT '频道名称',
    type VARCHAR(20) NOT NULL COMMENT '类型: PROJECT/DEPARTMENT/ENTERPRISE/PRIVATE',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '关联项目ID',
    department_id VARCHAR(36) DEFAULT NULL COMMENT '关联部门ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB COMMENT='聊天频道表';

-- ============================================
-- 聊天消息表
-- ============================================
CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(36) PRIMARY KEY COMMENT '消息ID',
    channel_id VARCHAR(36) NOT NULL COMMENT '频道ID',
    sender_id VARCHAR(36) NOT NULL COMMENT '发送者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '类型: TEXT/IMAGE/FILE/CODE/SYSTEM',
    reply_to_id VARCHAR(36) DEFAULT NULL COMMENT '回复的消息ID',
    is_edited TINYINT DEFAULT 0 COMMENT '是否已编辑',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否已删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（毫秒精度，避免同秒排序不稳定）',
    KEY idx_channel_id (channel_id),
    KEY idx_sender_id (sender_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='聊天消息表';

-- ============================================
-- 通知表
-- ============================================
CREATE TABLE IF NOT EXISTS notification (
    id VARCHAR(36) PRIMARY KEY COMMENT '通知ID',
    user_id VARCHAR(36) NOT NULL COMMENT '接收用户ID',
    type VARCHAR(20) NOT NULL COMMENT '类型: COMMENT/MENTION/TASK_UPDATE/BUG_UPDATE/SPRINT_UPDATE/SYSTEM/AI',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content TEXT DEFAULT NULL COMMENT '通知内容',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读',
    action_url VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id (user_id),
    KEY idx_is_read (is_read)
) ENGINE=InnoDB COMMENT='通知表';

-- ============================================
-- 文件表
-- ============================================
CREATE TABLE IF NOT EXISTS file_item (
    id VARCHAR(36) PRIMARY KEY COMMENT '文件ID',
    name VARCHAR(255) NOT NULL COMMENT '文件名',
    size BIGINT NOT NULL COMMENT '文件大小（字节）',
    mime_type VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    url VARCHAR(500) NOT NULL COMMENT '文件URL',
    thumbnail_url VARCHAR(500) DEFAULT NULL COMMENT '缩略图URL',
    uploader_id VARCHAR(36) NOT NULL COMMENT '上传者ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目ID',
    version INT DEFAULT 1 COMMENT '版本号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_uploader_id (uploader_id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB COMMENT='文件表';

-- ============================================
-- 操作日志表（Activity）
-- ============================================
CREATE TABLE IF NOT EXISTS activity_log (
    id VARCHAR(36) PRIMARY KEY COMMENT '日志ID',
    user_id VARCHAR(36) NOT NULL COMMENT '操作用户ID',
    enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '所属企业ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目ID',
    action VARCHAR(100) NOT NULL COMMENT '操作类型',
    target_type VARCHAR(50) DEFAULT NULL COMMENT '目标类型',
    target_id VARCHAR(36) DEFAULT NULL COMMENT '目标ID',
    detail TEXT DEFAULT NULL COMMENT '操作详情',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id (user_id),
    KEY idx_project_id (project_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='操作日志表';

-- ============================================
-- 需求表
-- ============================================
CREATE TABLE IF NOT EXISTS requirement (
    id VARCHAR(36) PRIMARY KEY COMMENT '需求ID',
    title VARCHAR(200) NOT NULL COMMENT '需求标题',
    description TEXT DEFAULT NULL COMMENT '需求描述',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目ID',
    requester_id VARCHAR(36) NOT NULL COMMENT '需求提出人ID',
    approver_id VARCHAR(36) DEFAULT NULL COMMENT '当前审批人ID',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN' COMMENT '状态: 取值来自项目工作流配置（默认 OPEN/COMPLETED/CLOSED）',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: CRITICAL/HIGH/MEDIUM/LOW',
    source VARCHAR(50) DEFAULT NULL COMMENT '需求来源: CUSTOMER/INTERNAL/MARKET/TECHNICAL',
    category VARCHAR(50) DEFAULT NULL COMMENT '需求分类: FEATURE/IMPROVEMENT/BUGFIX/OTHER',
    estimated_hours INT DEFAULT NULL COMMENT '预估工时（小时）',
    approved_at DATETIME DEFAULT NULL COMMENT '审批通过时间',
    rejected_at DATETIME DEFAULT NULL COMMENT '审批拒绝时间',
    reject_reason TEXT DEFAULT NULL COMMENT '拒绝原因',
    parent_id VARCHAR(36) DEFAULT NULL COMMENT '父需求ID（用于需求层级）',
    sort INT DEFAULT 0 COMMENT '排序号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_project_id (project_id),
    KEY idx_requester_id (requester_id),
    KEY idx_approver_id (approver_id),
    KEY idx_status (status),
    KEY idx_priority (priority),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB COMMENT='需求表';

-- ============================================
-- 需求审批记录表
-- ============================================
CREATE TABLE IF NOT EXISTS requirement_approval (
    id VARCHAR(36) PRIMARY KEY COMMENT '审批记录ID',
    requirement_id VARCHAR(36) NOT NULL COMMENT '需求ID',
    approver_id VARCHAR(36) NOT NULL COMMENT '审批人ID',
    approval_level INT NOT NULL DEFAULT 1 COMMENT '审批层级（1,2,3...）',
    action VARCHAR(20) NOT NULL COMMENT '审批动作: PENDING/APPROVE/REJECT',
    comment TEXT DEFAULT NULL COMMENT '审批意见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_requirement_id (requirement_id),
    KEY idx_approver_id (approver_id)
) ENGINE=InnoDB COMMENT='需求审批记录表';

-- ============================================
-- 需求任务关联表
-- ============================================
CREATE TABLE IF NOT EXISTS requirement_task (
    id VARCHAR(36) PRIMARY KEY COMMENT '关联ID',
    requirement_id VARCHAR(36) NOT NULL COMMENT '需求ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_requirement_task (requirement_id, task_id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB COMMENT='需求任务关联表';

-- ============================================
-- 需求模板表
-- ============================================
CREATE TABLE IF NOT EXISTS requirement_template (
    id VARCHAR(36) PRIMARY KEY COMMENT '模板ID',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description TEXT DEFAULT NULL COMMENT '模板描述',
    content TEXT DEFAULT NULL COMMENT '模板内容（JSON格式）',
    category VARCHAR(50) DEFAULT NULL COMMENT '模板分类',
    enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '所属企业ID（为空表示系统模板）',
    is_system TINYINT DEFAULT 0 COMMENT '是否系统模板',
    sort INT DEFAULT 0 COMMENT '排序号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_enterprise_id (enterprise_id)
) ENGINE=InnoDB COMMENT='需求模板表';

-- ============================================
-- 字段补全（修复已存在的表，continue-on-error 忽略重复执行报错）
-- ============================================
-- 为 bug 表添加 screenshots 列（如果表已存在但列不存在，请手动执行以下语句）
-- ALTER TABLE bug ADD COLUMN screenshots TEXT DEFAULT NULL COMMENT '截图（JSON 数组，存储 URL 列表）' AFTER environment;

-- ============================================
-- 任务评论表
-- ============================================
CREATE TABLE IF NOT EXISTS task_comment (
    id VARCHAR(36) PRIMARY KEY COMMENT '评论ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(36) NOT NULL COMMENT '评论者ID',
    content TEXT NOT NULL COMMENT '评论内容',
    reply_to_id VARCHAR(36) DEFAULT NULL COMMENT '回复的评论ID（为空表示顶级评论）',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_task_id (task_id),
    KEY idx_user_id (user_id),
    KEY idx_reply_to_id (reply_to_id)
) ENGINE=InnoDB COMMENT='任务评论表';

-- ============================================
-- 测试账号种子数据（与 README 一致，密码均为 admin123）
-- INSERT IGNORE：依赖 uk_username/uk_email 唯一键，重复执行自动跳过
-- ============================================
INSERT IGNORE INTO sys_user (id, username, email, password, role, status, deleted) VALUES
(REPLACE(UUID(), '-', ''), 'admin', 'admin@devflow.com', '$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale', 'ADMIN', 'ACTIVE', 0),
(REPLACE(UUID(), '-', ''), 'zhangsan', 'zhangsan@devflow.com', '$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale', 'MEMBER', 'ACTIVE', 0);

-- ============================================
-- AI 助手消息历史表（按用户持久化对话与行为记录）
-- ============================================
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id VARCHAR(36) PRIMARY KEY COMMENT '消息ID',
    user_id VARCHAR(36) NOT NULL COMMENT '所属用户ID',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: user/assistant',
    kind VARCHAR(20) NOT NULL DEFAULT 'CHAT' COMMENT '行为类型: CHAT/REPORT/SUMMARY/TASKS/AGENT/RAG/REVIEW',
    content TEXT NOT NULL COMMENT '消息内容',
    model VARCHAR(100) DEFAULT NULL COMMENT '生成模型（assistant 消息可选）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB COMMENT='AI 助手消息历史表';

-- ============================================
-- 字段补全：项目邀请码（分享链接加入项目）
-- continue-on-error 会忽略"列/索引已存在"的报错，保证幂等
-- ============================================
ALTER TABLE project ADD COLUMN invite_code VARCHAR(16) DEFAULT NULL COMMENT '项目邀请码' AFTER owner_id;
ALTER TABLE project ADD UNIQUE KEY uk_project_invite_code (invite_code);

-- ============================================
-- 权限体系（第 1 期：权限点注册 + 角色-权限映射，全局固定一套）
-- 设计文档：docs/PERMISSION_DESIGN.md
-- ============================================
CREATE TABLE IF NOT EXISTS sys_permission (
    code VARCHAR(64) PRIMARY KEY COMMENT '权限点，如 bug:create',
    name VARCHAR(100) NOT NULL COMMENT '权限点名称',
    scope VARCHAR(20) NOT NULL COMMENT '作用域: ENTERPRISE/PROJECT',
    description VARCHAR(255) DEFAULT NULL COMMENT '说明',
    sort INT DEFAULT 0 COMMENT '排序'
) ENGINE=InnoDB COMMENT='权限点注册表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    scope VARCHAR(20) NOT NULL COMMENT '作用域: ENTERPRISE/PROJECT',
    role VARCHAR(20) NOT NULL COMMENT '角色（企业7级角色 / 项目角色）',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限点',
    UNIQUE KEY uk_role_perm (scope, role, permission_code)
) ENGINE=InnoDB COMMENT='角色-权限点映射（INSERT IGNORE 幂等）';

-- 权限点注册（REPLACE 自愈：修复历史乱码数据，并以文件内容为准覆盖）
REPLACE INTO sys_permission (code, name, scope, description, sort) VALUES
('enterprise:manage', '企业管理', 'ENTERPRISE', '企业设置/邀请码/解散企业', 10),
('member:invite', '邀请成员', 'ENTERPRISE', '邀请新成员加入企业', 20),
('member:remove', '移除成员', 'ENTERPRISE', '移除企业成员', 30),
('member:role', '调整成员角色', 'ENTERPRISE', '修改企业成员角色', 40),
('member:department', '调整成员部门', 'ENTERPRISE', '调整企业成员部门归属', 50),
('audit:read', '查看审计日志', 'ENTERPRISE', '查看企业操作日志（预留）', 60),
('project:manage', '项目管理', 'PROJECT', '项目设置/归档/删除', 10),
('member:manage', '项目成员管理', 'PROJECT', '项目加人/改角色/移除', 20),
('bug:create', '新建 Bug', 'PROJECT', '创建 Bug（测试人员及项目管理员）', 30),
('bug:edit', '编辑 Bug', 'PROJECT', '修改 Bug 内容', 40),
('bug:delete', '删除 Bug', 'PROJECT', '删除 Bug', 50),
('bug:status:change', '变更 Bug 状态', 'PROJECT', '资源关系授权：仅负责人/创建人，不映射角色', 60);

-- 角色-权限映射（企业级 4 级：超管/管理员/经理/成员；2026-10 从 7 级收敛，见 docs/PERMISSION_DESIGN.md）
-- 先清除已废弃角色（DIRECTOR/SUPERVISOR/LEADER）的历史映射行，DELETE 幂等
DELETE FROM sys_role_permission WHERE scope='ENTERPRISE' AND role IN ('DIRECTOR','SUPERVISOR','LEADER');
INSERT IGNORE INTO sys_role_permission (id, scope, role, permission_code) VALUES
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'enterprise:manage'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'audit:read'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'member:invite'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'member:remove'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'member:role'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'SUPER_ADMIN', 'member:department'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'ADMIN', 'audit:read'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'ADMIN', 'member:invite'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'ADMIN', 'member:remove'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'ADMIN', 'member:role'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'ADMIN', 'member:department'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'MANAGER', 'member:invite'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'MANAGER', 'member:remove'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'MANAGER', 'member:role'),
(REPLACE(UUID(),'-',''), 'ENTERPRISE', 'MANAGER', 'member:department');

-- 存量用户角色迁移（7 级 → 4 级）：总监/主管→经理，组长→成员。
-- 只跑一次——否则管理员事后把某个经理降回成员，重启会被重新升级成经理。
UPDATE sys_user SET role='MANAGER'
  WHERE role IN ('DIRECTOR','SUPERVISOR')
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-user-role-7to4');
UPDATE sys_user SET role='MEMBER'
  WHERE role='LEADER'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-user-role-7to4');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-user-role-7to4');

-- ============================================
-- 游客分享项目（第 3 期，见 docs/PERMISSION_DESIGN.md §6）
-- ============================================
CREATE TABLE IF NOT EXISTS project_guest (
    id VARCHAR(36) PRIMARY KEY COMMENT '游客ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    token_hash VARCHAR(128) NOT NULL COMMENT '链接token哈希(SHA-256，明文不落库)',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '游客昵称（提交时自报）',
    email VARCHAR(100) DEFAULT NULL COMMENT '可选邮箱',
    expires_at DATETIME DEFAULT NULL COMMENT '过期时间（空=长期有效）',
    revoked TINYINT DEFAULT 0 COMMENT '是否撤销: 0-有效 1-已撤销',
    created_by VARCHAR(36) NOT NULL COMMENT '生成人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='项目游客协作者';

CREATE TABLE IF NOT EXISTS project_feedback (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    type VARCHAR(20) NOT NULL DEFAULT 'SUGGESTION' COMMENT '类型: SUGGESTION/IMPROVEMENT/OTHER',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT DEFAULT NULL COMMENT '内容',
    submitter_type VARCHAR(10) NOT NULL DEFAULT 'GUEST' COMMENT '提交者类型: USER/GUEST',
    submitter_user_id VARCHAR(36) DEFAULT NULL COMMENT '站内用户ID（submitter_type=USER时）',
    guest_id VARCHAR(36) DEFAULT NULL COMMENT '游客ID（submitter_type=GUEST时）',
    guest_nickname VARCHAR(50) DEFAULT NULL COMMENT '游客昵称',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/ACCEPTED/REJECTED/CONVERTED',
    converted_bug_id VARCHAR(36) DEFAULT NULL COMMENT '转化后的Bug ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='项目建议反馈表';

-- Bug 游客来源字段（continue-on-error 幂等）
ALTER TABLE bug ADD COLUMN reporter_type VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT '报告人类型: USER/GUEST' AFTER reporter_id;
ALTER TABLE bug ADD COLUMN guest_id VARCHAR(36) DEFAULT NULL COMMENT '游客ID（reporter_type=GUEST时）' AFTER reporter_type;
ALTER TABLE bug ADD COLUMN guest_nickname VARCHAR(50) DEFAULT NULL COMMENT '游客昵称' AFTER guest_id;
ALTER TABLE bug ADD COLUMN pending TINYINT NOT NULL DEFAULT 0 COMMENT '游客提交待确认: 0-否 1-待确认' AFTER guest_nickname;

-- 角色-权限映射（项目级；bug 三权限同时映射 OWNER/MANAGER/TESTER，即决策"豁免"）
INSERT IGNORE INTO sys_role_permission (id, scope, role, permission_code) VALUES
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'project:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'member:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'bug:create'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'bug:edit'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'bug:delete'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'member:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'bug:create'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'bug:edit'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'bug:delete'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'bug:create'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'bug:edit'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'bug:delete');

-- 聊天消息时间戳毫秒精度（MODIFY 幂等，修复同秒消息排序不稳定）
ALTER TABLE chat_message MODIFY COLUMN created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（毫秒精度）';

-- 需求审批功能已移除（2026-10-23）：需求创建即生效。
-- 这条原是"把所有非 APPROVED 状态改成 APPROVED"，但 2026-10-26 需求生命周期已改为
-- OPEN/COMPLETED/CLOSED，这条会把 COMPLETED/CLOSED 全部打回，且每次启动都执行。
-- 现在只把它并入下面的一次性迁移（见 2026-10-26-requirement-status-three-state）。

-- 通知跳转地址修复（2026-10-23）：历史通知里的 /bugs/{id}、/tasks/{id} 迁移为真实前端路由（幂等）
-- 这三条都是"历史通知地址修复"，幂等但每次启动都全表扫一遍。
-- 用迁移标记挡住重复执行（通知表是增长最快的表之一，启动时全表 UPDATE 的代价不划算）。
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

-- 用户真实姓名字段（2026-10-23，幂等）
ALTER TABLE sys_user ADD COLUMN real_name VARCHAR(50) DEFAULT NULL COMMENT '真实姓名（展示用）' AFTER username;

-- 任务清单（子任务）与任务依赖（2026-10-23）
CREATE TABLE IF NOT EXISTS task_checklist (
    id VARCHAR(36) PRIMARY KEY COMMENT '清单项ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    title VARCHAR(200) NOT NULL COMMENT '内容',
    done TINYINT DEFAULT 0 COMMENT '是否完成',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_task (task_id)
) ENGINE=InnoDB COMMENT='任务清单项';

CREATE TABLE IF NOT EXISTS task_dependency (
    id VARCHAR(36) PRIMARY KEY COMMENT '依赖记录ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID（被阻塞方）',
    depends_on_task_id VARCHAR(36) NOT NULL COMMENT '前置任务ID（阻塞方）',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_task_dep (task_id, depends_on_task_id),
    KEY idx_dep_on (depends_on_task_id)
) ENGINE=InnoDB COMMENT='任务依赖';

-- 文档版本 / 文档评论（2026-10-23）
-- 注意：task_worklog 曾在此处被定义过一次，下面的第 610 行附近又定义了一次。
-- CREATE TABLE IF NOT EXISTS 是"先建者生效"，所以实际生效的是这里那份 DECIMAL(5,1)，
-- 而实体与接口约定的却是 DECIMAL(5,2)（0.25 小时落库会被四舍五入成 0.3）。
-- 已删除本处定义，统一由下面那份（DECIMAL(5,2) + note 500）建表。
CREATE TABLE IF NOT EXISTS document_versions (
    id VARCHAR(36) PRIMARY KEY COMMENT '版本ID',
    document_id VARCHAR(36) NOT NULL COMMENT '文档ID',
    version INT NOT NULL COMMENT '版本号',
    title VARCHAR(100) NOT NULL COMMENT '标题快照',
    content LONGTEXT COMMENT '内容快照',
    editor_id VARCHAR(36) NOT NULL COMMENT '编辑人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_doc (document_id)
) ENGINE=InnoDB COMMENT='文档版本快照';

CREATE TABLE IF NOT EXISTS document_comments (
    id VARCHAR(36) PRIMARY KEY COMMENT '评论ID',
    document_id VARCHAR(36) NOT NULL COMMENT '文档ID',
    user_id VARCHAR(36) NOT NULL COMMENT '评论人',
    content VARCHAR(1000) NOT NULL COMMENT '评论内容',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_doc (document_id)
) ENGINE=InnoDB COMMENT='文档评论';

-- 工时登记（2026-10-23）
-- 工时登记（唯一权威定义；DECIMAL(5,2) 支持 0.25/0.75 这类真实工时）
-- 老库可能已被 DECIMAL(5,1) 版本建过表，ALTER ... MODIFY 把它纠正过来（幂等）
CREATE TABLE IF NOT EXISTS task_worklog (
    id VARCHAR(36) PRIMARY KEY COMMENT '工时记录ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(36) NOT NULL COMMENT '登记人',
    hours DECIMAL(5,2) NOT NULL COMMENT '工时（小时，两位小数）',
    work_date DATE NOT NULL COMMENT '工作日期',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_task (task_id),
    KEY idx_user_date (user_id, work_date)
) ENGINE=InnoDB COMMENT='任务工时';
ALTER TABLE task_worklog MODIFY COLUMN hours DECIMAL(5,2) NOT NULL COMMENT '工时（小时，两位小数）';
ALTER TABLE task_worklog MODIFY COLUMN note VARCHAR(500) DEFAULT NULL COMMENT '备注';

-- 文档版本快照（2026-10-23；同时修复 document 表缺 version 列导致的详情 500）
ALTER TABLE document ADD COLUMN version INT DEFAULT 1 COMMENT '版本号（每次保存+1）';
CREATE TABLE IF NOT EXISTS document_version (
    id VARCHAR(36) PRIMARY KEY COMMENT '版本ID',
    document_id VARCHAR(36) NOT NULL COMMENT '文档ID',
    version INT NOT NULL COMMENT '版本号（保存前的内容状态）',
    title VARCHAR(200) NOT NULL COMMENT '标题快照',
    content LONGTEXT COMMENT '内容快照',
    editor_id VARCHAR(36) NOT NULL COMMENT '编辑人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_doc_ver (document_id, version),
    KEY idx_doc (document_id)
) ENGINE=InnoDB COMMENT='文档版本快照';

-- 文档评论（2026-10-23）
CREATE TABLE IF NOT EXISTS document_comment (
    id VARCHAR(36) PRIMARY KEY COMMENT '评论ID',
    document_id VARCHAR(36) NOT NULL COMMENT '文档ID',
    user_id VARCHAR(36) NOT NULL COMMENT '评论人',
    content VARCHAR(2000) NOT NULL COMMENT '内容',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_doc (document_id)
) ENGINE=InnoDB COMMENT='文档评论';

-- Bug 评论（2026-10-23）
CREATE TABLE IF NOT EXISTS bug_comment (
    id VARCHAR(36) PRIMARY KEY COMMENT '评论ID',
    bug_id VARCHAR(36) NOT NULL COMMENT 'Bug ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID（冗余，便于可见性过滤）',
    user_id VARCHAR(36) NOT NULL COMMENT '评论人',
    content TEXT NOT NULL COMMENT '内容',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_bug (bug_id)
) ENGINE=InnoDB COMMENT='Bug评论';

-- 测试用例与执行记录（2026-10-23）
CREATE TABLE IF NOT EXISTS test_case (
    id VARCHAR(36) PRIMARY KEY COMMENT '用例ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    title VARCHAR(200) NOT NULL COMMENT '用例标题',
    precondition TEXT COMMENT '前置条件',
    steps TEXT COMMENT '步骤 JSON [{action, expected}]',
    expected_result TEXT COMMENT '预期结果',
    priority VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '优先级',
    requirement_id VARCHAR(36) DEFAULT NULL COMMENT '关联需求',
    creator_id VARCHAR(36) NOT NULL COMMENT '创建人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试用例';

CREATE TABLE IF NOT EXISTS test_execution (
    id VARCHAR(36) PRIMARY KEY COMMENT '执行记录ID',
    case_id VARCHAR(36) NOT NULL COMMENT '用例ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    result VARCHAR(20) NOT NULL COMMENT '结果: PASS/FAIL/BLOCKED/SKIP',
    note VARCHAR(1000) DEFAULT NULL COMMENT '执行备注',
    bug_id VARCHAR(36) DEFAULT NULL COMMENT '关联缺陷（失败转缺陷时回填）',
    executor_id VARCHAR(36) NOT NULL COMMENT '执行人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '执行时间',
    KEY idx_case (case_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试执行记录';

-- 测试用例管理权限点（TESTER/OWNER/MANAGER 可管理；PROJECT/OWNER 行已存在，其余追加）
INSERT IGNORE INTO sys_permission (code, name, scope, description, sort) VALUES
('testcase:manage', '测试用例管理', 'PROJECT', '测试用例的新建/编辑/删除/执行/转缺陷', 40);
INSERT IGNORE INTO sys_role_permission (id, scope, role, permission_code) VALUES
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'testcase:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'testcase:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'testcase:manage');

-- 企业微信群机器人 Webhook（2026-10-23，关键事件出站推送）
ALTER TABLE enterprise ADD COLUMN wecom_webhook VARCHAR(500) DEFAULT NULL COMMENT '企业微信群机器人 Webhook 地址（空=不推送）';

-- 自动化规则 MVP（2026-10-26）
CREATE TABLE IF NOT EXISTS automation_rule (
    id VARCHAR(36) PRIMARY KEY COMMENT '规则ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    name VARCHAR(100) NOT NULL COMMENT '规则名称',
    trigger_type VARCHAR(30) NOT NULL COMMENT '触发器: BUG_CREATED/BUG_FAIL/CASE_FAIL/TASK_DONE',
    assign_to_user_id VARCHAR(36) DEFAULT NULL COMMENT '自动指派给（可空）',
    notify_user_id VARCHAR(36) DEFAULT NULL COMMENT '通知给（可空）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    creator_id VARCHAR(36) NOT NULL COMMENT '创建人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='自动化规则';

-- 需求状态生命周期（2026-10-26，审批流移除后引入 OPEN/COMPLETED/CLOSED 三态）。
-- **这条必须只跑一次**：裸 UPDATE 会让每次重启都把"已完成/已关闭"的需求重开成 OPEN。
-- 已用 schema_migration 标记保护。
UPDATE requirement SET status = 'OPEN'
  WHERE (status IS NULL OR status NOT IN ('OPEN', 'COMPLETED', 'CLOSED'))
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-requirement-status-three-state');
UPDATE requirement SET reject_reason = NULL, approver_id = NULL
  WHERE (reject_reason IS NOT NULL OR approver_id IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-requirement-status-three-state');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-requirement-status-three-state');

-- 需求预估工时支持小数（2026-10-26）
ALTER TABLE requirement MODIFY estimated_hours DECIMAL(6,1) DEFAULT NULL COMMENT '预估工时（小时，支持小数）';

-- ============================================
-- 第三梯队：回收站/需求变更记录/Git 集成/文档企业隔离（2026-10-26）
-- ============================================

-- 项目 Git 集成字段
ALTER TABLE project ADD COLUMN git_repo_url VARCHAR(500) DEFAULT NULL COMMENT 'Git 仓库地址';
ALTER TABLE project ADD COLUMN git_webhook_secret VARCHAR(32) DEFAULT NULL COMMENT 'Git Webhook 密钥';
-- 只跑一次：否则管理员把密钥置空（停用 webhook）后，重启又会被重新生成一个可用密钥
UPDATE project SET git_webhook_secret = REPLACE(UUID(), '-', '')
  WHERE git_webhook_secret IS NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-webhook-secret-backfill');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-webhook-secret-backfill');

-- 全局文档按企业隔离（回填作者所属企业）
ALTER TABLE document ADD COLUMN enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '所属企业（全局文档按企业隔离）';
UPDATE document d JOIN sys_user u ON d.author_id = u.id SET d.enterprise_id = u.enterprise_id
  WHERE d.enterprise_id IS NULL AND u.enterprise_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-10-26-document-enterprise-backfill');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-10-26-document-enterprise-backfill');

-- 需求变更记录
CREATE TABLE IF NOT EXISTS requirement_change_log (
    id VARCHAR(36) PRIMARY KEY COMMENT '记录ID',
    requirement_id VARCHAR(36) NOT NULL COMMENT '需求ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    field VARCHAR(50) NOT NULL COMMENT '变更字段',
    old_value VARCHAR(1000) DEFAULT NULL COMMENT '旧值',
    new_value VARCHAR(1000) DEFAULT NULL COMMENT '新值',
    editor_id VARCHAR(36) NOT NULL COMMENT '修改人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    KEY idx_req (requirement_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='需求变更记录';

-- Git 提交记录（webhook 入站）
CREATE TABLE IF NOT EXISTS git_commit (
    id VARCHAR(36) PRIMARY KEY COMMENT '记录ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    sha VARCHAR(64) NOT NULL COMMENT '提交SHA',
    message VARCHAR(1000) DEFAULT NULL COMMENT '提交说明',
    author_name VARCHAR(100) DEFAULT NULL COMMENT '作者',
    web_url VARCHAR(500) DEFAULT NULL COMMENT '提交链接',
    pushed_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '推送时间',
    UNIQUE KEY uk_project_sha (project_id, sha),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='Git 提交记录';

-- ============================================
-- 安全加固迁移（2026-11，见 docs/AUDIT_REPORT_2026-11.md 第 0 批）
-- ============================================

-- 权限矩阵按企业隔离：enterprise_id='*' 为全局默认模板；企业首次调整矩阵时复制成本企业快照
ALTER TABLE sys_role_permission ADD COLUMN enterprise_id VARCHAR(36) NOT NULL DEFAULT '*' COMMENT '企业ID，* 为全局默认模板' AFTER id;
ALTER TABLE sys_role_permission DROP INDEX uk_role_perm;
ALTER TABLE sys_role_permission ADD UNIQUE KEY uk_role_perm (enterprise_id, scope, role, permission_code);

-- 新增项目级权限点（业务权限从"仅成员可见"升级为"按权限点可配置"）
REPLACE INTO sys_permission (code, name, scope, description, sort) VALUES
('project:edit', '编辑项目信息', 'PROJECT', '修改项目名称/标识/描述/仓库地址/状态', 5),
('task:manage', '任务管理', 'PROJECT', '任务新建/编辑/删除/状态流转/依赖/清单/工时', 70),
('requirement:manage', '需求管理', 'PROJECT', '需求新建/编辑/删除/关联任务', 80),
('document:manage', '文档编辑', 'PROJECT', '文档新建/编辑/版本恢复/评论', 90),
('document:delete', '删除文档', 'PROJECT', '删除项目文档', 100),
('file:manage', '文件管理', 'PROJECT', '项目文件上传/删除', 110),
('sprint:manage', '迭代管理', 'PROJECT', '迭代创建/编辑/激活/完成/删除', 120);

INSERT IGNORE INTO sys_role_permission (id, scope, role, permission_code) VALUES
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'project:edit'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'project:edit'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'task:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'task:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'task:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MEMBER', 'task:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'requirement:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'requirement:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'requirement:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MEMBER', 'requirement:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'document:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'document:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'document:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MEMBER', 'document:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'document:delete'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'document:delete'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'file:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'file:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'file:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MEMBER', 'file:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'sprint:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'sprint:manage');

-- 高频查询复合索引（安全审计报告 §D3 的索引缺口，低风险、可幂等重跑）
ALTER TABLE chat_channel ADD KEY idx_project (project_id);
ALTER TABLE bug ADD KEY idx_assignee (assignee_id);
ALTER TABLE department_member ADD KEY idx_enterprise (enterprise_id);
ALTER TABLE notification ADD KEY idx_user_read_created (user_id, is_read, created_at);
ALTER TABLE activity_log ADD KEY idx_enterprise_created (enterprise_id, created_at);
ALTER TABLE file_item ADD KEY idx_url (url(191));
ALTER TABLE chat_message ADD KEY idx_channel_created (channel_id, created_at);
ALTER TABLE document ADD KEY idx_parent (parent_id);

-- ============================================
-- 第 2/3 批新增表（权威定义同时保留在 db/migration/ 下，供未来 Flyway 接入）
-- 注意：Flyway 尚未接入，这里是启动时实际执行的位置；缺了这段，
-- 对应的接口会在运行时报 "Table doesn't exist"
-- ============================================

CREATE TABLE IF NOT EXISTS workflow (
    id VARCHAR(36) PRIMARY KEY COMMENT '工作流ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目；NULL 表示全局默认模板',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    issue_type VARCHAR(20) NOT NULL COMMENT '适用问题类型: TASK/BUG/REQUIREMENT',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否该项目该类型的默认工作流',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_type (project_id, issue_type)
) ENGINE=InnoDB COMMENT='工作流定义';

CREATE TABLE IF NOT EXISTS workflow_status (
    id VARCHAR(36) PRIMARY KEY COMMENT '状态ID',
    workflow_id VARCHAR(36) NOT NULL COMMENT '所属工作流',
    code VARCHAR(40) NOT NULL COMMENT '状态码（存进业务表 status 字段的值）',
    name VARCHAR(40) NOT NULL COMMENT '展示名',
    category VARCHAR(20) NOT NULL COMMENT '归一分类: TODO/IN_PROGRESS/DONE（报表口径依赖它）',
    color VARCHAR(20) DEFAULT NULL COMMENT '看板列颜色',
    sort INT NOT NULL DEFAULT 0 COMMENT '看板列顺序',
    is_initial TINYINT NOT NULL DEFAULT 0 COMMENT '是否新建时的初始状态',
    is_terminal TINYINT NOT NULL DEFAULT 0 COMMENT '是否终态',
    UNIQUE KEY uk_workflow_code (workflow_id, code),
    KEY idx_workflow_sort (workflow_id, sort)
) ENGINE=InnoDB COMMENT='工作流状态';

CREATE TABLE IF NOT EXISTS workflow_transition (
    id VARCHAR(36) PRIMARY KEY COMMENT '流转ID',
    workflow_id VARCHAR(36) NOT NULL COMMENT '所属工作流',
    from_status VARCHAR(40) NOT NULL COMMENT '起始状态码',
    to_status VARCHAR(40) NOT NULL COMMENT '目标状态码',
    name VARCHAR(60) DEFAULT NULL COMMENT '流转动作名',
    UNIQUE KEY uk_transition (workflow_id, from_status, to_status),
    KEY idx_workflow (workflow_id)
) ENGINE=InnoDB COMMENT='工作流流转规则';

CREATE TABLE IF NOT EXISTS project_release (
    id VARCHAR(36) PRIMARY KEY COMMENT '版本ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(100) NOT NULL COMMENT '版本名称',
    version VARCHAR(40) NOT NULL COMMENT '版本号（项目内唯一）',
    status VARCHAR(20) NOT NULL DEFAULT 'UNRELEASED' COMMENT '状态: UNRELEASED/RELEASED/ARCHIVED',
    description TEXT DEFAULT NULL COMMENT '版本说明',
    release_date DATE DEFAULT NULL COMMENT '计划发布日期',
    released_at DATETIME DEFAULT NULL COMMENT '实际发布时间',
    released_by VARCHAR(36) DEFAULT NULL COMMENT '发布操作人',
    release_notes TEXT DEFAULT NULL COMMENT '发布说明（Markdown）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_project_version (project_id, version, deleted),
    KEY idx_project_status (project_id, status)
) ENGINE=InnoDB COMMENT='项目版本/发布';

CREATE TABLE IF NOT EXISTS test_plan (
    id VARCHAR(36) PRIMARY KEY COMMENT '测试计划ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(200) NOT NULL COMMENT '计划名称',
    description TEXT DEFAULT NULL COMMENT '计划说明',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING/ACTIVE/COMPLETED',
    owner_id VARCHAR(36) DEFAULT NULL COMMENT '负责人',
    release_id VARCHAR(36) DEFAULT NULL COMMENT '关联版本',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试计划';

CREATE TABLE IF NOT EXISTS test_cycle (
    id VARCHAR(36) PRIMARY KEY COMMENT '测试轮次ID',
    plan_id VARCHAR(36) NOT NULL COMMENT '所属测试计划',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(200) NOT NULL COMMENT '轮次名称',
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_plan (plan_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试轮次';

CREATE TABLE IF NOT EXISTS test_cycle_case (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    cycle_id VARCHAR(36) NOT NULL COMMENT '测试轮次',
    case_id VARCHAR(36) NOT NULL COMMENT '测试用例',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    assignee_id VARCHAR(36) DEFAULT NULL COMMENT '本轮执行人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    UNIQUE KEY uk_cycle_case (cycle_id, case_id),
    KEY idx_case (case_id)
) ENGINE=InnoDB COMMENT='测试轮次包含的用例';

CREATE TABLE IF NOT EXISTS test_case_version (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    case_id VARCHAR(36) NOT NULL COMMENT '用例ID',
    version INT NOT NULL COMMENT '版本号',
    title VARCHAR(200) NOT NULL COMMENT '标题快照',
    precondition TEXT COMMENT '前置条件快照',
    steps TEXT COMMENT '步骤快照',
    expected_result TEXT COMMENT '预期结果快照',
    editor_id VARCHAR(36) NOT NULL COMMENT '编辑人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照时间',
    UNIQUE KEY uk_case_version (case_id, version)
) ENGINE=InnoDB COMMENT='测试用例版本快照';

CREATE TABLE IF NOT EXISTS design_asset (
    id VARCHAR(36) PRIMARY KEY COMMENT '设计稿ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    requirement_id VARCHAR(36) DEFAULT NULL COMMENT '关联需求',
    task_id VARCHAR(36) DEFAULT NULL COMMENT '关联任务',
    provider VARCHAR(30) NOT NULL DEFAULT 'OTHER' COMMENT '来源: FIGMA/LANHU/MASTERGO/OTHER',
    title VARCHAR(200) NOT NULL COMMENT '设计稿标题',
    file_key VARCHAR(200) DEFAULT NULL COMMENT '外部文件标识',
    node_id VARCHAR(200) DEFAULT NULL COMMENT '外部节点标识',
    embed_url VARCHAR(1000) DEFAULT NULL COMMENT '嵌入/预览链接',
    thumbnail_url VARCHAR(1000) DEFAULT NULL COMMENT '缩略图链接',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主设计稿',
    status VARCHAR(30) NOT NULL DEFAULT 'DESIGNING' COMMENT 'DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED',
    current_version INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by VARCHAR(36) NOT NULL COMMENT '创建人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_requirement (requirement_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='设计稿（外部工具指针）';

CREATE TABLE IF NOT EXISTS design_asset_version (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    asset_id VARCHAR(36) NOT NULL COMMENT '设计稿ID',
    version INT NOT NULL COMMENT '版本号',
    snapshot_url VARCHAR(1000) DEFAULT NULL COMMENT '该版本的预览/快照链接',
    note VARCHAR(500) DEFAULT NULL COMMENT '版本说明',
    editor_id VARCHAR(36) NOT NULL COMMENT '提交人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_asset_version (asset_id, version)
) ENGINE=InnoDB COMMENT='设计稿版本快照';

CREATE TABLE IF NOT EXISTS saved_filter (
    id VARCHAR(36) PRIMARY KEY COMMENT '筛选器ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目；NULL 表示跨项目',
    owner_id VARCHAR(36) NOT NULL COMMENT '创建人',
    name VARCHAR(100) NOT NULL COMMENT '筛选器名称',
    entity VARCHAR(20) NOT NULL COMMENT '查询对象: TASK/BUG/REQUIREMENT',
    expression VARCHAR(2000) NOT NULL COMMENT '结构化查询表达式',
    is_shared TINYINT NOT NULL DEFAULT 0 COMMENT '是否共享给项目成员',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_entity (project_id, entity),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB COMMENT='保存的筛选器';

CREATE TABLE IF NOT EXISTS deploy_environment (
    id VARCHAR(36) PRIMARY KEY COMMENT '环境ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(100) NOT NULL COMMENT '环境名称（如 测试环境/预发/生产）',
    type VARCHAR(20) NOT NULL DEFAULT 'TEST' COMMENT '类型: DEV/TEST/STAGING/PROD',
    requires_approval TINYINT NOT NULL DEFAULT 0 COMMENT '部署是否需要审批',
    approval_roles VARCHAR(100) DEFAULT NULL COMMENT '可审批的项目角色（逗号分隔，空=OWNER/MANAGER）',
    url VARCHAR(500) DEFAULT NULL COMMENT '环境访问地址',
    sort INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='部署环境';

CREATE TABLE IF NOT EXISTS deployment (
    id VARCHAR(36) PRIMARY KEY COMMENT '部署记录ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    environment_id VARCHAR(36) NOT NULL COMMENT '目标环境',
    release_id VARCHAR(36) DEFAULT NULL COMMENT '部署的版本（project_release.id）',
    version VARCHAR(40) DEFAULT NULL COMMENT '版本号快照（版本被删也不丢历史）',
    commit_sha VARCHAR(64) DEFAULT NULL COMMENT '对应提交',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '状态: PENDING_APPROVAL/APPROVED/DEPLOYING/SUCCEEDED/FAILED/ROLLED_BACK/CANCELLED',
    triggered_by VARCHAR(36) NOT NULL COMMENT '发起人',
    approved_by VARCHAR(36) DEFAULT NULL COMMENT '审批人',
    approved_at DATETIME DEFAULT NULL COMMENT '审批时间',
    approval_note VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    started_at DATETIME DEFAULT NULL COMMENT '开始部署时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
    rollback_of VARCHAR(36) DEFAULT NULL COMMENT '回滚目标部署记录（本次回滚的是哪次部署）',
    note VARCHAR(1000) DEFAULT NULL COMMENT '说明/失败原因',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_env (project_id, environment_id),
    KEY idx_project_finished (project_id, finished_at),
    KEY idx_release (release_id)
) ENGINE=InnoDB COMMENT='部署记录';

CREATE TABLE IF NOT EXISTS design_walkthrough (
    id VARCHAR(36) PRIMARY KEY COMMENT '走查单ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    requirement_id VARCHAR(36) NOT NULL COMMENT '关联需求',
    design_asset_id VARCHAR(36) DEFAULT NULL COMMENT '走查依据的设计稿',
    design_version INT DEFAULT NULL COMMENT '走查依据的设计稿版本（锁定基线）',
    title VARCHAR(200) NOT NULL COMMENT '走查单标题',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
    created_by VARCHAR(36) NOT NULL COMMENT '创建人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_requirement (requirement_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='UI 走查单';

CREATE TABLE IF NOT EXISTS design_walkthrough_item (
    id VARCHAR(36) PRIMARY KEY COMMENT '走查项ID',
    walkthrough_id VARCHAR(36) NOT NULL COMMENT '所属走查单',
    title VARCHAR(300) NOT NULL COMMENT '走查项（如「按钮间距与设计稿一致」）',
    result VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '结果: PENDING/PASS/FAIL/NA',
    note VARCHAR(1000) DEFAULT NULL COMMENT '备注',
    screenshot_url VARCHAR(1000) DEFAULT NULL COMMENT '截图/证据链接',
    bug_id VARCHAR(36) DEFAULT NULL COMMENT '失败项转出的缺陷',
    sort INT NOT NULL DEFAULT 0 COMMENT '顺序',
    checked_by VARCHAR(36) DEFAULT NULL COMMENT '检查人',
    checked_at DATETIME DEFAULT NULL COMMENT '检查时间',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_walkthrough (walkthrough_id)
) ENGINE=InnoDB COMMENT='UI 走查项';

-- ============================================
-- 第 2 批地基改造（2026-11，见 docs/BATCH2_FOUNDATION.md）
-- 新表的权威定义在 db/migration/V2__batch2_foundation.sql（幂等），这里补齐列级变更
-- ============================================

-- 工作项挂版本（fixVersion 语义）
ALTER TABLE task ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT '修复版本（project_release.id）' AFTER sprint_id;
ALTER TABLE bug ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT '修复版本（project_release.id）' AFTER environment;
ALTER TABLE requirement ADD COLUMN fix_version_id VARCHAR(36) DEFAULT NULL COMMENT '交付版本（project_release.id）' AFTER estimated_hours;
ALTER TABLE requirement ADD COLUMN assignee_id VARCHAR(36) DEFAULT NULL COMMENT '需求负责人' AFTER requester_id;

-- 设计走查状态（NONE/DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED）
ALTER TABLE task ADD COLUMN design_status VARCHAR(20) DEFAULT NULL COMMENT '设计状态' AFTER status;
ALTER TABLE requirement ADD COLUMN design_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '设计状态' AFTER status;

-- 测试：用例版本与评审、执行记录挂轮次
ALTER TABLE test_case ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '用例版本号' AFTER expected_result;
ALTER TABLE test_case ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '评审状态' AFTER version;
ALTER TABLE test_execution ADD COLUMN cycle_id VARCHAR(36) DEFAULT NULL COMMENT '所属测试轮次' AFTER project_id;
ALTER TABLE test_execution ADD COLUMN case_version INT DEFAULT NULL COMMENT '执行时的用例版本号' AFTER case_id;

-- 高频索引（幂等：索引已存在时跳过）
--
-- 这里原来用 CREATE PROCEDURE + DELIMITER $$ 实现幂等，看起来是对的，实际上在应用里
-- **从来没有生效过**：Spring 的 spring.sql.init 用的是 ScriptUtils，它只按 ";" 切分语句，
-- 不认识 DELIMITER。于是 "DELIMITER $$" 被当成一条非法 SQL 直接失败，而后半段
-- "END$$ DELIMITER ;" 也各自失败，存储过程从未被创建，后面 7 条 CALL 全部报
-- "PROCEDURE does not exist"。因为 continue-on-error=true，这些全被静默吞掉。
--
-- 集成验证脚本用的是 mysql 命令行客户端（认识 DELIMITER），所以那些检查一直是绿的 ——
-- 一个只在"用应用启动"时才出现的缺陷，被一个"用 CLI 执行"的验证掩盖了。
--
-- 现在改成不依赖 DELIMITER 的写法：SET @ddl / PREPARE / EXECUTE 是四条独立语句，
-- ScriptUtils 按 ";" 切分正好每句一条，两种执行方式结果一致。

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


-- requirement.status 的列注释长期写着已废弃的审批流状态（DRAFT/PENDING/APPROVED...），
-- 实际取值是工作流定义的 OPEN/COMPLETED/CLOSED。老库的注释不会因 CREATE TABLE IF NOT EXISTS 更新，
-- 这里显式 MODIFY 一次（MODIFY 幂等）。
ALTER TABLE requirement MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'OPEN'
  COMMENT '状态: 取值来自项目工作流配置（默认 OPEN/COMPLETED/CLOSED）';

-- 任务完成时间（审计报告 §C15）：燃尽图不能再用 updated_at 当完成时间
ALTER TABLE task ADD COLUMN completed_at DATETIME DEFAULT NULL COMMENT '完成时间（进入 DONE 分类时写入）';

-- 回填：历史上已完成的任务用 updated_at 近似（不完美，但比全空强；
-- 而且只回填一次，之后由业务写入真实完成时间）
UPDATE task t SET t.completed_at = t.updated_at
  WHERE t.completed_at IS NULL AND t.status IN (
      SELECT code FROM workflow_status WHERE category = 'DONE')
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-completed-at-backfill');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-11-task-completed-at-backfill');

-- ============================================
-- 乐观锁版本列（审计报告 §C1）
-- ============================================
-- 三个人同时拖同一张看板卡片时，后保存的会静默覆盖前一个 —— 全库没有一列能让
-- UPDATE 判断"我看到的是不是最新版本"。这里给编辑最频繁的两类工作项加 version，
-- 配合 MyBatis-Plus 的 @Version 注解，冲突时影响行数为 0，接口返回 409。
-- 注意 DOCUMENT.version 是"文档版本号"（语义不同），不要混用。
ALTER TABLE task ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号（每次更新+1）';
ALTER TABLE bug ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号（每次更新+1）';

-- ============================================
-- 审计报告 §D3 点名的缺失索引（热点过滤列）
-- ============================================
-- 这些列都在真实查询的 WHERE/JOIN 条件里，但建表时没建索引，等于每次全表扫：
--   chat_channel.project_id      → GET /chat/channels 的主过滤条件，这张表原本除主键外零索引
--   bug.assignee_id              → listMyBugs 按它查
--   notification(user_id,is_read)→ 未读数查询；两个单列索引在复合条件下不如一个复合索引
--   activity_log(enterprise_id, created_at) → 企业审计日志分页
--   file_item.url                → 按 URL 反查文件（前缀索引，url 可能有 500 字符）
--   chat_message(channel_id, created_at) → 历史消息分页（单列 channel_id 后还要排序）
--   document.parent_id           → Wiki 树全量构建
--   department_member.enterprise_id → 按企业列部门成员
SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `chat_channel` ADD KEY `idx_project_id` (project_id)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_channel' AND index_name = 'idx_project_id');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `bug` ADD KEY `idx_bug_assignee` (assignee_id)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_bug_assignee');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `notification` ADD KEY `idx_user_unread` (user_id, is_read)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notification' AND index_name = 'idx_user_unread');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `notification` ADD KEY `idx_notification_created` (created_at)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notification' AND index_name = 'idx_notification_created');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `activity_log` ADD KEY `idx_activity_ent_created` (enterprise_id, created_at)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'activity_log' AND index_name = 'idx_activity_ent_created');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `file_item` ADD KEY `idx_file_url` (url(191))', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'file_item' AND index_name = 'idx_file_url');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `chat_message` ADD KEY `idx_msg_channel_created` (channel_id, created_at)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_msg_channel_created');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `document` ADD KEY `idx_doc_parent` (parent_id)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'document' AND index_name = 'idx_doc_parent');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `department_member` ADD KEY `idx_dept_member_ent` (enterprise_id)', 'DO 0')
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'department_member' AND index_name = 'idx_dept_member_ent');
PREPARE devflow_stmt FROM @ddl; EXECUTE devflow_stmt; DEALLOCATE PREPARE devflow_stmt;

-- 历史任务状态归一：早期代码用过 IN_PROGRESS / IN_REVIEW，与 schema/前端约定的
-- TODO/DOING/TESTING/DONE 不一致（审计报告 §C7）。不归一的话这些任务会落到
-- 工作流之外的"未知状态"桶里，看板和报表都统计不进去。
-- 只跑一次：项目可自定义工作流状态码，如果某个项目真的建了叫 IN_PROGRESS 的状态，
-- 每次重启都把它改写成 DOING 就是在破坏用户数据。
UPDATE task SET status = 'DOING'
  WHERE status = 'IN_PROGRESS'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-status-normalize');
UPDATE task SET status = 'TESTING'
  WHERE status = 'IN_REVIEW'
    AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '2026-11-task-status-normalize');
INSERT IGNORE INTO schema_migration (name) VALUES ('2026-11-task-status-normalize');

-- ============================================
-- 第 3 批：部署与发布闭环、UI 走查（2026-11，见 docs/BATCH3_DELIVERY.md）
-- 新表权威定义见 db/migration/V3__batch3_deploy_walkthrough.sql
-- ============================================

-- 部署记录关联提交（DORA 变更前置时间需要"提交→部署"的对应关系）
ALTER TABLE deployment ADD COLUMN first_commit_at DATETIME DEFAULT NULL COMMENT '本版本首个提交时间（变更前置时间起点）';
ALTER TABLE deployment ADD KEY idx_env_finished (environment_id, finished_at);

-- ============================================
-- 第 4 批：企业级门槛（2026-11，见 docs/BATCH4_ENTERPRISE.md）
-- ============================================

-- 双因素认证（TOTP，RFC 6238）
ALTER TABLE sys_user ADD COLUMN totp_secret VARCHAR(64) DEFAULT NULL COMMENT 'TOTP 密钥（Base32，启用后才写入）';
ALTER TABLE sys_user ADD COLUMN totp_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用双因素认证';
ALTER TABLE sys_user ADD COLUMN totp_bound_at DATETIME DEFAULT NULL COMMENT '绑定时间';

-- 密码策略：首次登录 / 超管重置后强制改密
ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '下次登录必须修改密码';
ALTER TABLE sys_user ADD COLUMN password_updated_at DATETIME DEFAULT NULL COMMENT '上次改密时间（密码过期策略用）';
ALTER TABLE sys_user ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 COMMENT '会话版本（改密后递增并撤销旧令牌）';

-- 审计防篡改：哈希链
ALTER TABLE activity_log ADD COLUMN prev_hash VARCHAR(64) DEFAULT NULL COMMENT '上一条审计记录的哈希（链式防篡改）';
ALTER TABLE activity_log ADD COLUMN record_hash VARCHAR(64) DEFAULT NULL COMMENT '本条记录的哈希';
ALTER TABLE activity_log ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '操作来源 IP';
ALTER TABLE activity_log ADD COLUMN user_agent VARCHAR(300) DEFAULT NULL COMMENT '操作来源 UA';
ALTER TABLE activity_log ADD KEY idx_enterprise_id_created (enterprise_id, id);

-- ============================================
-- 第 5 批：自定义字段（权威定义见 db/migration/V4__batch5_custom_fields.sql）
-- 注意：Flyway 尚未接入，这里是启动时实际执行的位置
-- ============================================

CREATE TABLE IF NOT EXISTS custom_field (
    id VARCHAR(36) PRIMARY KEY COMMENT '字段ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    issue_type VARCHAR(20) NOT NULL COMMENT '适用对象: TASK/BUG/REQUIREMENT',
    field_key VARCHAR(60) NOT NULL COMMENT '字段标识（项目内同一对象类型唯一）',
    name VARCHAR(100) NOT NULL COMMENT '展示名',
    type VARCHAR(20) NOT NULL COMMENT '类型: TEXT/TEXTAREA/NUMBER/DATE/SELECT/MULTI_SELECT/USER/CHECKBOX',
    options TEXT DEFAULT NULL COMMENT '选项 JSON 数组（SELECT/MULTI_SELECT 用）',
    required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填',
    default_value VARCHAR(500) DEFAULT NULL COMMENT '默认值',
    sort INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    description VARCHAR(255) DEFAULT NULL COMMENT '字段说明（表单提示）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（停用不删数据）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_project_type_key (project_id, issue_type, field_key),
    KEY idx_project_type (project_id, issue_type)
) ENGINE=InnoDB COMMENT='自定义字段定义';

CREATE TABLE IF NOT EXISTS custom_field_value (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    field_id VARCHAR(36) NOT NULL COMMENT '字段ID',
    issue_id VARCHAR(36) NOT NULL COMMENT '工作项ID',
    issue_type VARCHAR(20) NOT NULL COMMENT '工作项类型',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID（冗余，便于按项目清理）',
    value TEXT DEFAULT NULL COMMENT '值（文本/数字/日期原样存；多选存 JSON 数组）',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_field_issue (field_id, issue_id),
    KEY idx_issue (issue_id, issue_type),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='自定义字段值';

-- ============================================
-- 第 5 批：项目集（Program）与备份说明（见 docs/BATCH5_ENTERPRISE_SCALE.md）
-- ============================================

-- 项目集：企业下的一组项目（产品线/业务线），支持跨项目里程碑与资源视图
CREATE TABLE IF NOT EXISTS program (
    id VARCHAR(36) PRIMARY KEY COMMENT '项目集ID',
    enterprise_id VARCHAR(36) NOT NULL COMMENT '所属企业',
    name VARCHAR(100) NOT NULL COMMENT '项目集名称',
    description TEXT DEFAULT NULL COMMENT '说明',
    owner_id VARCHAR(36) DEFAULT NULL COMMENT '负责人',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_enterprise (enterprise_id)
) ENGINE=InnoDB COMMENT='项目集';

-- 项目挂项目集（一个项目至多属于一个项目集）
ALTER TABLE project ADD COLUMN program_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目集（program.id）';
ALTER TABLE project ADD KEY idx_program (program_id);

-- ============================================
-- 第 6 批：IM 基础能力、通知偏好、全文检索（见 docs/BATCH6_COLLABORATION.md）
-- ============================================

-- 消息已读回执：按"频道 + 用户"记录已读位置，比逐条已读表省一个数量级
CREATE TABLE IF NOT EXISTS chat_read_state (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    channel_id VARCHAR(36) NOT NULL COMMENT '频道ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    last_read_at DATETIME(3) NOT NULL COMMENT '已读至该时间点（含）',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_channel_user (channel_id, user_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='频道已读位置';

-- 消息表情回应：一条消息 × 一个用户 × 一个表情唯一
CREATE TABLE IF NOT EXISTS chat_reaction (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    message_id VARCHAR(36) NOT NULL COMMENT '消息ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    emoji VARCHAR(16) NOT NULL COMMENT '表情（Unicode 字符或短码）',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_message_user_emoji (message_id, user_id, emoji),
    KEY idx_message (message_id)
) ENGINE=InnoDB COMMENT='消息表情回应';

-- 消息编辑/撤回留痕（原内容不再对外，但审计可查）
ALTER TABLE chat_message ADD COLUMN edited_at DATETIME(3) DEFAULT NULL COMMENT '编辑时间';
ALTER TABLE chat_message ADD COLUMN recalled_at DATETIME(3) DEFAULT NULL COMMENT '撤回时间';
ALTER TABLE chat_message ADD COLUMN recalled_by VARCHAR(36) DEFAULT NULL COMMENT '撤回操作人';
ALTER TABLE chat_message ADD COLUMN original_content TEXT DEFAULT NULL COMMENT '撤回前的原文（仅审计可见）';

-- 通知偏好：按类型开关 + 免打扰时段
CREATE TABLE IF NOT EXISTS notification_preference (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    type VARCHAR(20) NOT NULL COMMENT '通知类型（与 notification.type 对应）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否接收该类型通知',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_user_type (user_id, type)
) ENGINE=InnoDB COMMENT='通知类型偏好';

-- 免打扰：全局时段（跨小时或跨天时 start > end 表示跨天）
ALTER TABLE sys_user ADD COLUMN dnd_start TIME DEFAULT NULL COMMENT '免打扰开始时间';
ALTER TABLE sys_user ADD COLUMN dnd_end TIME DEFAULT NULL COMMENT '免打扰结束时间';
ALTER TABLE sys_user ADD COLUMN dnd_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用免打扰';

-- 全文检索：ngram 分词（中文按 2-gram 切分），替代 LIKE '%kw%' 的全表扫描。
-- 注意：ngram_token_size 默认 2，若要支持单字检索需在 my.cnf 调成 1（代价是索引膨胀）。
ALTER TABLE document ADD FULLTEXT KEY ft_document (title, content) WITH PARSER ngram;
ALTER TABLE task ADD FULLTEXT KEY ft_task (title, description) WITH PARSER ngram;
ALTER TABLE bug ADD FULLTEXT KEY ft_bug (title, description) WITH PARSER ngram;
ALTER TABLE requirement ADD FULLTEXT KEY ft_requirement (title, description) WITH PARSER ngram;
ALTER TABLE chat_message ADD FULLTEXT KEY ft_chat_message (content) WITH PARSER ngram;

-- ============================================
-- 第 7 批：订阅式通知（见 docs/BATCH7_COLLAB_UX.md）
-- ============================================

-- 关注关系：某用户关注某个工作项后，其变更才会推送给他
CREATE TABLE IF NOT EXISTS subscription (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(36) NOT NULL COMMENT '关注人',
    target_type VARCHAR(20) NOT NULL COMMENT '对象类型: TASK/BUG/REQUIREMENT/DOCUMENT',
    target_id VARCHAR(36) NOT NULL COMMENT '对象ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目（便于按项目清理）',
    auto TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动关注（被指派/被提及/创建人）',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关注时间',
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target (target_type, target_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='工作项关注关系';

-- 任务状态变更事件：燃尽与趋势只读追加式历史，不用当前状态反推过去
CREATE TABLE IF NOT EXISTS task_status_event (
    id VARCHAR(36) PRIMARY KEY COMMENT '状态事件ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    from_status VARCHAR(40) DEFAULT NULL COMMENT '变更前状态（回填时为NULL）',
    to_status VARCHAR(40) NOT NULL COMMENT '变更后状态',
    from_category VARCHAR(20) DEFAULT NULL COMMENT '变更前状态分类',
    to_category VARCHAR(20) NOT NULL COMMENT '变更后状态分类',
    actor_id VARCHAR(36) DEFAULT NULL COMMENT '操作人ID（系统/历史回填为空）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '状态变更时间',
    KEY idx_task_status_event_task_time (task_id, created_at),
    KEY idx_task_status_event_project_time (project_id, created_at)
) ENGINE=InnoDB COMMENT='任务状态变更事件（追加式，不更新历史）';

-- 文档向量索引事务发件箱：文档主数据与同步命令在同一事务提交
CREATE TABLE IF NOT EXISTS document_index_outbox (
    id VARCHAR(36) PRIMARY KEY COMMENT '索引同步事件ID',
    document_id VARCHAR(36) NOT NULL COMMENT '文档ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '项目隔离范围',
    enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '企业隔离范围',
    operation VARCHAR(10) NOT NULL COMMENT '操作: UPSERT/DELETE',
    title VARCHAR(200) NOT NULL COMMENT '文档标题快照',
    content LONGTEXT DEFAULT NULL COMMENT '文档正文快照',
    doc_type VARCHAR(20) NOT NULL DEFAULT 'DOC' COMMENT '文档类型快照',
    document_updated_at DATETIME(3) NOT NULL COMMENT '文档版本时间',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RETRY/DONE/FAILED',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_attempt_at DATETIME(3) DEFAULT NULL COMMENT '下次执行时间',
    last_error VARCHAR(2000) DEFAULT NULL COMMENT '最近失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_document_index_outbox_version
        (document_id, operation, document_updated_at),
    KEY idx_document_index_outbox_due (status, next_attempt_at),
    KEY idx_document_index_outbox_document (document_id, created_at)
) ENGINE=InnoDB COMMENT='文档向量索引事务发件箱';
