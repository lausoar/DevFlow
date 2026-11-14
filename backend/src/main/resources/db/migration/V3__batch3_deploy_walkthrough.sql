-- ============================================
-- V3：第 3 批 —— 部署与发布闭环、UI 走查
-- ============================================
-- 只含幂等建表（CREATE TABLE IF NOT EXISTS），可重复执行。
-- 列级变更仍按既有方式放在 schema.sql 末尾。

-- --------------------------------------------
-- 1. 环境与部署记录
-- --------------------------------------------
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

-- --------------------------------------------
-- 2. UI 走查单
-- --------------------------------------------
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
