-- ============================================
-- DevFlow 需求管理模块 - 数据库补丁
-- 执行方式：mysql -u root -p devflow < requirement-patch.sql
-- ============================================

USE devflow;

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

-- 完成提示
SELECT '需求管理模块数据库表创建成功！' AS message;
