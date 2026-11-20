package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable document-to-RAG synchronization command.
 *
 * The document version timestamp, operation and document id form the idempotency
 * key. Content is snapshotted so retries do not depend on the current document row.
 */
@Data
@TableName("document_index_outbox")
public class DocumentIndexOutbox {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String documentId;
    private String projectId;
    private String enterpriseId;
    private String operation;
    private String title;
    private String content;
    private String docType;
    /** 索引时的文档版本（引用溯源据此判断来源是否已改版） */
    private Integer documentVersion;
    private LocalDateTime documentUpdatedAt;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
