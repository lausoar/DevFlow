package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 企业目标（OKR 的 O）。
 *
 * 刻意不存"进度百分比"：进度由关联项目/需求的工时、任务完成、缺陷数据聚合得出，
 * 一旦允许手工填数，管理层看到的数字就会与真实交付脱节。
 */
@Data
@TableName("objective")
public class Objective {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ACHIEVED = "ACHIEVED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String enterpriseId;

    /** 所属项目集（可空） */
    private String programId;

    private String name;

    private String description;

    private String ownerId;

    /** 周期，如 2026Q4 */
    private String period;

    private LocalDate startDate;

    private LocalDate endDate;

    /** ACTIVE / ACHIEVED / ARCHIVED */
    private String status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
