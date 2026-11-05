package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 需求变更记录
 */
@Data
@TableName("requirement_change_log")
public class RequirementChangeLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 需求ID */
    private String requirementId;

    /** 项目ID */
    private String projectId;

    /** 变更字段 */
    private String field;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 修改人 */
    private String editorId;

    /** 展示用：修改人姓名（非表字段） */
    @TableField(exist = false)
    private String editorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
