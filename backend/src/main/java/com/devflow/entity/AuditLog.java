package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作审计日志（activity_log）
 * 记录敏感操作与权限判定拒绝事件；写入失败不影响业务（AuditService 内部兜底）
 */
@Data
@TableName("activity_log")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 操作用户 ID */
    private String userId;

    /** 所属企业 ID（全局操作为空） */
    private String enterpriseId;

    /** 所属项目 ID（非项目操作为空） */
    private String projectId;

    /** 操作类型，如 member.invite / permission.denied */
    private String action;

    /** 目标类型（user/project/permission...） */
    private String targetType;

    /** 目标 ID */
    private String targetId;

    /** 操作详情 */
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
