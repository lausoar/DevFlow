package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目实体
 */
@Data
@TableName("project")
public class Project {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目名称 */
    private String name;

    /** 项目标识（如 DF, PP） */
    private String projectKey;

    /** 项目描述 */
    private String description;

    /** 项目图标 */
    private String icon;

    /** 所属企业 ID */
    private String enterpriseId;

    /** 项目负责人 ID */
    private String ownerId;

    /** 项目邀请码（分享链接加入项目，为空表示尚未生成） */
    private String inviteCode;

    /** Git 仓库地址 */
    private String gitRepoUrl;

    /** Git Webhook 密钥 */
    private String gitWebhookSecret;

    /** 状态: ACTIVE / ARCHIVED / PLANNING */
    private String status;

    @TableLogic
    private Integer deleted;

    /** 成员数（列表卡片展示用，非表字段，查询后批量填充） */
    @TableField(exist = false)
    private Integer memberCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
