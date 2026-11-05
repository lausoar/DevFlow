package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 自动化规则（MVP）：当触发事件发生时，通知指定成员，并可把新对象自动指派给指定成员。
 */
@Data
@TableName("automation_rule")
public class AutomationRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 规则名称 */
    private String name;

    /** 触发器: BUG_CREATED / BUG_FAIL / CASE_FAIL / TASK_DONE */
    private String triggerType;

    /** 自动指派给（可空；仅对新对象生效） */
    private String assignToUserId;

    /** 通知给（可空） */
    private String notifyUserId;

    /** 是否启用 */
    private Integer enabled;

    /** 创建人 */
    private String creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
