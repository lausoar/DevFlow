-- ============================================
-- V4：第 5 批 —— 自定义字段
-- ============================================
-- 只含幂等建表；列级变更仍在 schema.sql 末尾。

-- 字段定义（项目级；按问题类型分组）
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

-- 字段值（每个工作项一份 JSON 快照，避免宽表与 EAV 的 N 次查询）
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
