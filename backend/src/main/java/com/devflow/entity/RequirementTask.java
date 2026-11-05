package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 需求任务关联实体
 */
@Data
@TableName("requirement_task")
public class RequirementTask {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 需求ID */
    private String requirementId;

    /** 任务ID */
    private String taskId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
