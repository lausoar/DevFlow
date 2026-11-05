package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {

    /** 频道ID（WebSocket 发送时使用；REST 发送时取自 URL 路径，故此处不校验） */
    private String channelId;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 消息类型: TEXT / IMAGE / FILE / CODE */
    private String type;

    /** 回复的消息ID */
    private String replyToId;
}
