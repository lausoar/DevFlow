package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 助手消息历史实体
 * 按用户持久化 AI 中心的所有交互（对话、日报、总结、Agent、知识库、代码审查）
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属用户ID */
    private String userId;

    /** 角色: user / assistant */
    private String role;

    /** 行为类型: CHAT/REPORT/SUMMARY/TASKS/AGENT/RAG/REVIEW */
    private String kind;

    private String content;

    /** 生成该回复的模型（assistant 消息可选） */
    private String model;

    private LocalDateTime createdAt;
}
