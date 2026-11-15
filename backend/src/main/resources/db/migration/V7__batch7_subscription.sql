-- ============================================
-- V7：第 7 批 —— 订阅式通知
-- ============================================

CREATE TABLE IF NOT EXISTS subscription (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(36) NOT NULL COMMENT '关注人',
    target_type VARCHAR(20) NOT NULL COMMENT '对象类型',
    target_id VARCHAR(36) NOT NULL COMMENT '对象ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目',
    auto TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动关注',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关注时间',
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target (target_type, target_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='工作项关注关系';
