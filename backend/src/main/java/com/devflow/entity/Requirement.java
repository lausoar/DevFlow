package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 需求实体
 */
@Data
@TableName("requirement")
public class Requirement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 需求标题 */
    private String title;

    /** 需求描述 */
    private String description;

    /** 所属项目ID */
    private String projectId;

    /** 需求提出人ID */
    private String requesterId;

    /** 需求负责人（产品/研发） */
    private String assigneeId;

    /** 当前审批人ID */
    private String approverId;

    /** 状态：取值来自项目工作流配置（默认 OPEN / COMPLETED / CLOSED） */
    private String status;

    /** 设计状态: NONE/DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED */
    private String designStatus;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 需求来源: CUSTOMER / INTERNAL / MARKET / TECHNICAL */
    private String source;

    /** 需求分类: FEATURE / IMPROVEMENT / BUGFIX / OTHER */
    private String category;

    /** 预估工时（小时，支持小数） */
    private Double estimatedHours;

    /** 交付版本（project_release.id） */
    private String fixVersionId;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 审批拒绝时间 */
    private LocalDateTime rejectedAt;

    /** 拒绝原因 */
    private String rejectReason;

    /** 父需求ID */
    private String parentId;

    /** 排序号 */
    private Integer sort;

    @TableLogic
    private Integer deleted;

    /**
     * 删除时间 / 删除人（deleted=1 时才有值）。
     *
     * 之前只有 deleted 一个布尔标记，回收站只能拿 updated_at 冒充"删除于"——
     * 那条记录删除前如果被改过标题，显示的就是改标题的时间。
     */
    private LocalDateTime deletedAt;
    private String deletedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
