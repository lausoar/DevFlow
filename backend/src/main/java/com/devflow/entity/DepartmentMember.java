package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门成员关联表
 */
@Data
@TableName("department_member")
public class DepartmentMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String departmentId;
    private String userId;
    private String enterpriseId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
