package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 通知实体
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** 类型: COMMENT / MENTION / TASK_UPDATE / BUG_UPDATE / SPRINT_UPDATE / SYSTEM / AI */
    private String type;

    private String title;
    private String content;
    private Integer isRead;
    private String actionUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
