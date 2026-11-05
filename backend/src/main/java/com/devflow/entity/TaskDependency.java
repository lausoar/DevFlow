package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务依赖（task_id 的开工以前置任务 depends_on_task_id 的完成为前提）
 */
@Data
@TableName("task_dependency")
public class TaskDependency {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 任务 ID（被阻塞方） */
    private String taskId;

    /** 前置任务 ID（阻塞方） */
    private String dependsOnTaskId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
