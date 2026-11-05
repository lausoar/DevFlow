package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务评论实体
 */
@Data
@TableName("task_comment")
public class TaskComment {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 任务 ID */
    private String taskId;

    /** 评论者 ID */
    private String userId;

    /** 评论内容 */
    private String content;

    /** 回复的评论 ID（可选） */
    private String replyToId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
