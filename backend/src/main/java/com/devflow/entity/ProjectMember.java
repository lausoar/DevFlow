package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目成员关联表
 */
@Data
@TableName("project_member")
public class ProjectMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 用户 ID */
    private String userId;

    /** 项目角色: OWNER / MANAGER / MEMBER */
    private String role;

    /** 加入时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    // ====== 非数据库字段（JOIN 查询填充） ======

    /** 用户名（JOIN sys_user） */
    @TableField(exist = false)
    private String username;

    /** 真实姓名（联查填充，展示优先用；非表字段） */
    @TableField(exist = false)
    private String realName;

    /** 邮箱（JOIN sys_user） */
    @TableField(exist = false)
    private String email;

    /** 头像（JOIN sys_user） */
    @TableField(exist = false)
    private String avatar;
}
