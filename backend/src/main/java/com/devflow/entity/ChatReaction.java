package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 消息表情回应 */
@Data
@TableName("chat_reaction")
public class ChatReaction {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String messageId;
    private String userId;
    private String emoji;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
