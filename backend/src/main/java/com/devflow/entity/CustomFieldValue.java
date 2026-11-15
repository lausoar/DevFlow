package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("custom_field_value")
public class CustomFieldValue {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String fieldId;
    private String issueId;
    private String issueType;
    private String projectId;
    private String value;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
