package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 角色-权限点映射（sys_role_permission）
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 作用域: ENTERPRISE / PROJECT */
    private String scope;

    /** 角色（企业角色 / 项目角色枚举） */
    private String role;

    /** 权限点 */
    private String permissionCode;
}
