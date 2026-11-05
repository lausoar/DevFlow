package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门实体
 */
@Data
@TableName("department")
public class Department {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String enterpriseId;
    private String parentId;
    private String leaderId;
    private Integer sort;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
