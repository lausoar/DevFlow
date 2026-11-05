package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String channelId;
    private String senderId;
    private String content;

    /** 类型: TEXT / IMAGE / FILE / CODE / SYSTEM */
    private String type;

    private String replyToId;
    private Integer isEdited;
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ====== 关联填充（非数据库字段） ======

    /** 发送者用户名（JOIN 填充） */
    @TableField(exist = false)
    private String senderName;

    /** 发送者头像（JOIN 填充） */
    @TableField(exist = false)
    private String senderAvatar;
}
