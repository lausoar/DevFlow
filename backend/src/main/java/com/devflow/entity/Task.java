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

    /** 修复版本（project_release.id） */
    private String fixVersionId;
    private String assigneeId;
    private String reporterId;

    /** 乐观锁版本号（乐观锁冲突时 409） */
    @Version
    private Integer version;

    /** 状态：取值来自项目工作流配置（默认 TODO / DOING / TESTING / DONE） */
    private String status;

    /** 设计状态: NONE/DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED */
    private String designStatus;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    private Integer storyPoints;
    private LocalDateTime dueDate;

    /**
     * 完成时间（进入 DONE 分类状态时写入，离开时清空）。
     *
     * 燃尽图原来拿 updated_at 当完成时间 —— 任务完成后改个标题或换个人，
     * updated_at 就刷新，历史曲线当场重画。有了独立列，历史才稳定。
     */
    private LocalDateTime completedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
