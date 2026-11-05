package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务实体
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String description;
    private String projectId;
    private String sprintId;
    private String assigneeId;
    private String reporterId;

    /** 状态: TODO / DOING / TESTING / DONE */
    private String status;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    private Integer storyPoints;
    private LocalDateTime dueDate;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
