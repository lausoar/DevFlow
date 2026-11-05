package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 权限点注册（sys_permission）
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /** 权限点编码，如 bug:create（业务固定，非自增/UUID） */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 权限点名称 */
    private String name;

    /** 作用域: ENTERPRISE / PROJECT */
    private String scope;

    /** 说明 */
    private String description;

    /** 排序 */
    private Integer sort;
}
