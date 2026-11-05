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

    /** 当前审批人ID */
    private String approverId;

    /** 状态: OPEN / COMPLETED / CLOSED */
    private String status;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 需求来源: CUSTOMER / INTERNAL / MARKET / TECHNICAL */
    private String source;

    /** 需求分类: FEATURE / IMPROVEMENT / BUGFIX / OTHER */
    private String category;

    /** 预估工时（小时，支持小数） */
    private Double estimatedHours;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
