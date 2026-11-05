package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档版本快照（每次保存前对旧内容做快照）
 */
@Data
@TableName("document_version")
public class DocumentVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 文档 ID */
    private String documentId;

    /** 版本号（保存前的内容状态） */
    private Integer version;

    /** 标题快照 */
    private String title;

    /** 内容快照 */
    private String content;

    /** 编辑人 */
    private String editorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
