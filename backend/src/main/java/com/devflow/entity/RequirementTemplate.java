package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 需求模板实体
 */
@Data
@TableName("requirement_template")
public class RequirementTemplate {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 模板内容（JSON格式） */
    private String content;

    /** 模板分类 */
    private String category;

    /** 所属企业ID（为空表示系统模板） */
    private String enterpriseId;

    /** 是否系统模板 */
    private Integer isSystem;

    /** 排序号 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
