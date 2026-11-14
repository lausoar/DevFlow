package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目版本 / 发布（Release）。
 *
 * 这是把「需求-任务-缺陷」按版本聚合的主键：有了它才能回答
 * "这个版本还差什么才能发"，也才能自动生成 Release Notes。
 */
@Data
@TableName("project_release")
public class ProjectRelease {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;

    private String name;

    /** 版本号，项目内唯一（如 v1.2.0） */
    private String version;

    /** 状态: UNRELEASED / RELEASED / ARCHIVED */
    private String status;

    private String description;

    private LocalDate releaseDate;

    private LocalDateTime releasedAt;

    private String releasedBy;

    /** 发布说明（Markdown，可自动生成后人工编辑） */
    private String releaseNotes;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_UNRELEASED = "UNRELEASED";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
}
