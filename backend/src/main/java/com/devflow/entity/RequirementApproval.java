package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 需求审批记录实体
 */
@Data
@TableName("requirement_approval")
public class RequirementApproval {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 需求ID */
    private String requirementId;

    /** 审批人ID */
    private String approverId;

    /** 审批层级（1, 2, 3...） */
    private Integer approvalLevel;

    /** 审批动作: PENDING / APPROVE / REJECT */
    private String action;

    /** 审批意见 */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
