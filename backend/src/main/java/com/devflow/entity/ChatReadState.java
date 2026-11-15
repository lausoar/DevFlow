package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 频道已读位置：按"频道 + 用户"记录已读时间点。
 * 比逐条已读表省一个数量级，也天然支持"全部已读"。
 */
@Data
@TableName("chat_read_state")
public class ChatReadState {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String channelId;
    private String userId;

    /** 已读至该时间点（含） */
    private LocalDateTime lastReadAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
