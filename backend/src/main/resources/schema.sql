-- ============================================
-- DevFlow 数据库 Schema
-- MySQL 8.0+
-- ============================================

CREATE DATABASE IF NOT EXISTS devflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE devflow;

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
    status VARCHAR(20) NOT NULL DEFAULT 'TODO' COMMENT '状态: TODO/DOING/TESTING/DONE',
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
CREATE TABLE IF NOT EXISTS task_tag (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名',
    KEY idx_task_id (task_id)
) ENGINE=InnoDB COMMENT='任务标签表';

-- ============================================
-- 任务检查清单
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
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PENDING/APPROVED/REJECTED/CANCELLED',
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

-- 存量用户角色迁移（7 级 → 4 级，UPDATE 幂等）：总监/主管→经理，组长→成员
UPDATE sys_user SET role='MANAGER' WHERE role IN ('DIRECTOR','SUPERVISOR');
UPDATE sys_user SET role='MEMBER' WHERE role='LEADER';

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

-- 需求审批功能已移除（2026-10-23）：需求创建即生效，历史状态统一归一为 APPROVED
UPDATE requirement SET status='APPROVED', reject_reason=NULL, approver_id=NULL WHERE status IS NULL OR status <> 'APPROVED';

-- 通知跳转地址修复（2026-10-23）：历史通知里的 /bugs/{id}、/tasks/{id} 迁移为真实前端路由（幂等）
UPDATE notification n JOIN bug b ON SUBSTRING(n.action_url, 7) = b.id
  SET n.action_url = CONCAT('/projects/', b.project_id, '?tab=bugs')
  WHERE n.action_url LIKE '/bugs/%';
UPDATE notification n JOIN task t ON SUBSTRING(n.action_url, 8) = t.id
  SET n.action_url = CONCAT('/projects/', t.project_id, '?tab=tasks')
  WHERE n.action_url LIKE '/tasks/%';
-- 关联不到实体（已删除）的旧地址置空，通知退化为不可点击而不是点了 404
UPDATE notification SET action_url = NULL WHERE action_url LIKE '/bugs/%' OR action_url LIKE '/tasks/%';

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

-- 工时登记 / 文档版本 / 文档评论（2026-10-23）
CREATE TABLE IF NOT EXISTS task_worklog (
    id VARCHAR(36) PRIMARY KEY COMMENT '工时记录ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(36) NOT NULL COMMENT '登记人',
    hours DECIMAL(5,1) NOT NULL COMMENT '工时（小时）',
    work_date DATE NOT NULL COMMENT '工作日期',
    note VARCHAR(200) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_task (task_id),
    KEY idx_user_date (user_id, work_date)
) ENGINE=InnoDB COMMENT='任务工时登记';

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
CREATE TABLE IF NOT EXISTS task_worklog (
    id VARCHAR(36) PRIMARY KEY COMMENT '工时记录ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(36) NOT NULL COMMENT '登记人',
    hours DECIMAL(5,2) NOT NULL COMMENT '工时（小时）',
    work_date DATE NOT NULL COMMENT '工作日期',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_task (task_id)
) ENGINE=InnoDB COMMENT='任务工时';

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

-- 需求状态生命周期（2026-10-26，审批流移除后引入 OPEN/COMPLETED/CLOSED 三态，存量旧状态全部归为 OPEN）
UPDATE requirement SET status = 'OPEN' WHERE status IS NULL OR status NOT IN ('OPEN', 'COMPLETED', 'CLOSED');

-- 需求预估工时支持小数（2026-10-26）
ALTER TABLE requirement MODIFY estimated_hours DECIMAL(6,1) DEFAULT NULL COMMENT '预估工时（小时，支持小数）';

-- ============================================
-- 第三梯队：回收站/需求变更记录/Git 集成/文档企业隔离（2026-10-26）
-- ============================================

-- 项目 Git 集成字段
ALTER TABLE project ADD COLUMN git_repo_url VARCHAR(500) DEFAULT NULL COMMENT 'Git 仓库地址';
ALTER TABLE project ADD COLUMN git_webhook_secret VARCHAR(32) DEFAULT NULL COMMENT 'Git Webhook 密钥';
UPDATE project SET git_webhook_secret = REPLACE(UUID(), '-', '') WHERE git_webhook_secret IS NULL;

-- 全局文档按企业隔离（回填作者所属企业）
ALTER TABLE document ADD COLUMN enterprise_id VARCHAR(36) DEFAULT NULL COMMENT '所属企业（全局文档按企业隔离）';
UPDATE document d JOIN sys_user u ON d.author_id = u.id SET d.enterprise_id = u.enterprise_id
  WHERE d.enterprise_id IS NULL AND u.enterprise_id IS NOT NULL;

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
