-- Durable document-to-RAG commands. The unique key prevents duplicate work for one save.
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

-- Millisecond precision prevents two rapid saves from sharing one idempotency key.
ALTER TABLE document
    MODIFY COLUMN updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
