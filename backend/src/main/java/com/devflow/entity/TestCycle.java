package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试轮次：一次回归/验证的执行范围。
 * 执行记录（test_execution.cycle_id）挂到轮次上，才能回答"这一轮测了什么、进度多少"。
 */
@Data
@TableName("test_cycle")
public class TestCycle {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String planId;

    private String projectId;

    private String name;

    /** 状态: NOT_STARTED / IN_PROGRESS / COMPLETED */
    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
}
