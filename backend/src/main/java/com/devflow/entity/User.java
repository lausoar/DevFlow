package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户名（登录用） */
    private String username;

    /** 真实姓名（展示用，空则回退用户名） */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 角色 */
    private String role;

    /** 状态: ACTIVE / INACTIVE / SUSPENDED */
    private String status;

    /** 所属企业 ID */
    private String enterpriseId;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 当前所属部门ID（非表字段，查询企业成员时填充） */
    @TableField(exist = false)
    private String departmentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 展示名：真实姓名优先，用户名兜底（所有给人看的地方统一用这个） */
    public String displayName() {
        return realName != null && !realName.isBlank() ? realName : username;
    }
}
