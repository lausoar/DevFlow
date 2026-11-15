package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 通知类型偏好（按类型开关） */
@Data
@TableName("notification_preference")
public class NotificationPreference {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    /** 与 notification.type 对应：COMMENT/MENTION/TASK_UPDATE/BUG_UPDATE/SYSTEM... */
    private String type;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
