package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Bug 评论
 */
@Data
@TableName("bug_comment")
public class BugComment {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** Bug ID */
    private String bugId;

    /** 项目 ID（冗余，便于可见性过滤） */
    private String projectId;

    /** 评论人 */
    private String userId;

    /** 内容 */
    private String content;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 评论人展示名/头像（联查填充，非表字段） */
    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String userAvatar;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
