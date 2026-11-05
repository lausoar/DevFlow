package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档评论
 */
@Data
@TableName("document_comment")
public class DocumentComment {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 文档 ID */
    private String documentId;

    /** 评论人 */
    private String userId;

    /** 内容 */
    private String content;

    /** 评论人姓名/头像（联查填充，非表字段） */
    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String userAvatar;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
