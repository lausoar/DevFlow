package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 工作流流转规则：允许 from → to 的跳转。
 * 校验在服务端做，前端看板据此决定哪些列可拖。
 */
@Data
@TableName("workflow_transition")
public class WorkflowTransition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workflowId;

    private String fromStatus;

    private String toStatus;

    /** 动作名，如「开始开发」 */
    private String name;
}
