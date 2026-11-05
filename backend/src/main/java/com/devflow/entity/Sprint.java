package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 实体
 */
@Data
@TableName("sprint")
public class Sprint {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String projectId;
    private String goal;

    /** 状态: PLANNING / ACTIVE / COMPLETED */
    private String status;

    private LocalDate startDate;
    private LocalDate endDate;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
