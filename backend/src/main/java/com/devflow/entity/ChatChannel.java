package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天频道实体
 */
@Data
@TableName("chat_channel")
public class ChatChannel {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    /** 类型: PROJECT / DEPARTMENT / ENTERPRISE / PRIVATE */
    private String type;

    private String projectId;
    private String departmentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
