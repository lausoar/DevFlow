-- ============================================
-- V2：种子数据（账号与权限矩阵）
-- ============================================
-- 全部语句幂等（INSERT IGNORE / REPLACE / 带条件的 DELETE），
-- 因此对"表已经存在的老库"和"全新库"都安全。
--
-- 为什么种子数据要进迁移而不是留在 schema.sql：
--   它是**数据**不是**结构**。放在 spring.sql.init 里意味着每次启动都重跑一遍
--   REPLACE（删+插），而 Flyway 让它只在版本推进时执行一次。

INSERT IGNORE INTO sys_user (id, username, email, password, role, status, deleted) VALUES
(REPLACE(UUID(), '-', ''), 'admin', 'admin@devflow.com', '$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale', 'ADMIN', 'ACTIVE', 0),
(REPLACE(UUID(), '-', ''), 'zhangsan', 'zhangsan@devflow.com', '$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale', 'MEMBER', 'ACTIVE', 0);

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

INSERT IGNORE INTO sys_permission (code, name, scope, description, sort) VALUES
('testcase:manage', '测试用例管理', 'PROJECT', '测试用例的新建/编辑/删除/执行/转缺陷', 40);

INSERT IGNORE INTO sys_role_permission (id, scope, role, permission_code) VALUES
(REPLACE(UUID(),'-',''), 'PROJECT', 'TESTER', 'testcase:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'OWNER', 'testcase:manage'),
(REPLACE(UUID(),'-',''), 'PROJECT', 'MANAGER', 'testcase:manage');

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
