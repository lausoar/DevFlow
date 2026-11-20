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

    /** 编辑时间（为空表示未编辑） */
    private LocalDateTime editedAt;

    /** 撤回时间（不为空表示已撤回，内容不再对外） */
    private LocalDateTime recalledAt;

    /** 撤回操作人 */
    private String recalledBy;

    /** 撤回前的原文（仅审计可见，不返回给前端） */
    private String originalContent;

    /** RAG 引用来源（JSON 数组文本；仅 @AI 知识库回答写入） */
    private String sources;

    /** 表情聚合（非表字段，列表接口批量填充）：emoji → userId 列表 */
    @TableField(exist = false)
    private java.util.Map<String, java.util.List<String>> reactions;

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
