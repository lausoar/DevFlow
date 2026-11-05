package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务清单项（子任务）
 */
@Data
@TableName("task_checklist")
public class TaskChecklistItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 任务 ID */
    private String taskId;

    /** 内容 */
    private String title;

    /** 是否完成 */
    private Integer done;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
