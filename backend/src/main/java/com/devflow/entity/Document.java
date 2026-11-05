package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;

    /** 文档内容（Markdown） */
    private String content;

    private String projectId;
    private String authorId;

    /** 所属企业（全局文档按企业隔离；带项目的文档以项目为准） */
    private String enterpriseId;

    /** 类型: DOC / WIKI / MEETING / REPORT */
    private String type;

    /** 父文档ID（用于树形结构） */
    private String parentId;

    private Integer sort;

    /** 版本号（内容每次变更递增） */
    private Integer version;
    private Integer isPublished;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
