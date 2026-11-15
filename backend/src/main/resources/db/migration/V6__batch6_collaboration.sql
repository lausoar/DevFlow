-- ============================================
-- V6：第 6 批 —— IM 基础能力、通知偏好、全文检索
-- ============================================
-- 只含幂等建表；列级变更与 FULLTEXT 索引在 schema.sql 末尾。

CREATE TABLE IF NOT EXISTS chat_read_state (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    channel_id VARCHAR(36) NOT NULL COMMENT '频道ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    last_read_at DATETIME(3) NOT NULL COMMENT '已读至该时间点（含）',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_channel_user (channel_id, user_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='频道已读位置';

CREATE TABLE IF NOT EXISTS chat_reaction (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    message_id VARCHAR(36) NOT NULL COMMENT '消息ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    emoji VARCHAR(16) NOT NULL COMMENT '表情',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_message_user_emoji (message_id, user_id, emoji),
    KEY idx_message (message_id)
) ENGINE=InnoDB COMMENT='消息表情回应';

CREATE TABLE IF NOT EXISTS notification_preference (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    type VARCHAR(20) NOT NULL COMMENT '通知类型',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否接收',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_user_type (user_id, type)
) ENGINE=InnoDB COMMENT='通知类型偏好';
