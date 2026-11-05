package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 企业实体
 */
@Data
@TableName("enterprise")
public class Enterprise {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 企业名称 */
    private String name;

    /** 企业 Logo */
    private String logo;

    /** 邀请码 */
    private String inviteCode;

    /** 企业微信群机器人 Webhook（空=不推送） */
    private String wecomWebhook;

    /** 企业所有者 ID */
    private String ownerId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
