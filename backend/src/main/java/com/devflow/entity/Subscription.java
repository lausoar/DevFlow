package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 工作项关注关系 */
@Data
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** TASK / BUG / REQUIREMENT / DOCUMENT */
    private String targetType;
    private String targetId;
    private String projectId;

    /** 是否自动关注（被指派 / 被提及 / 创建人） */
    private Integer auto;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
