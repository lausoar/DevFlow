package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目游客协作者（分享链接身份，不进企业、无登录态）
 */
@Data
@TableName("project_guest")
public class ProjectGuest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 链接 token 哈希（SHA-256，明文只在生成时返回一次） */
    private String tokenHash;

    /** 游客昵称（提交时自报） */
    private String nickname;

    /** 可选邮箱 */
    private String email;

    /** 过期时间（空=长期有效） */
    private LocalDateTime expiresAt;

    /** 是否撤销 */
    private Integer revoked;

    /** 生成人 ID */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
