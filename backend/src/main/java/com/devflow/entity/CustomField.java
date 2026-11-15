package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("custom_field")
public class CustomField {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;
    /** 适用对象: TASK / BUG / REQUIREMENT */
    private String issueType;
    /** 字段标识（项目内同一对象类型唯一） */
    private String fieldKey;
    private String name;
    /** TEXT / TEXTAREA / NUMBER / DATE / SELECT / MULTI_SELECT / USER / CHECKBOX */
    private String type;
    /** 选项 JSON 数组（SELECT / MULTI_SELECT） */
    private String options;
    private Integer required;
    private String defaultValue;
    private Integer sort;
    private String description;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
