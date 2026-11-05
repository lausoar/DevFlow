package com.devflow.service;

/**
 * 聊天 @提及处理：解析消息中的 @用户名，给被提及的频道成员发 MENTION 通知。
 * 处理失败不影响消息发送（内部兜底）。
 */
public interface ChatMentionService {

    /**
     * 解析并处理 @提及。
     *
     * @param channelId 频道 ID
     * @param senderId  发送者（不给自己发）
     * @param content   消息内容（可能为附件 JSON，内部提取纯文本）
     */
    void processMentions(String channelId, String senderId, String content);
}
