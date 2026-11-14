package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试计划：一次交付（通常是某个版本）的测试范围容器。
 * 下面挂若干测试轮次（test_cycle），轮次里圈定用例（test_cycle_case）。
 */
@Data
@TableName("test_plan")
public class TestPlan {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;

    private String name;

    private String description;

    /** 状态: PLANNING / ACTIVE / COMPLETED */
    private String status;

    private String ownerId;

    /** 关联版本（project_release.id） */
    private String releaseId;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
}
