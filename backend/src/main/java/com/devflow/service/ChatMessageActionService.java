package com.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息的编辑、撤回、表情与已读回执。
 *
 * 三条产品约束（都是企业 IM 的基本线）：
 * - 编辑/撤回只允许本人，且撤回保留原文供审计（对外不可见）；
 * - 已读按"频道 + 用户"记录到时间点，比逐条已读表省一个数量级；
 * - 表情回应用户级唯一，重复点击即取消。
 */
public interface ChatMessageActionService {

    /** 编辑消息（仅本人；保留 is_edited 与 edited_at） */
    Map<String, Object> edit(String messageId, String content, String operatorId);

    /** 撤回消息（仅本人；原文写入 original_content，对外 content 置占位） */
    Map<String, Object> recall(String messageId, String operatorId);

    /** 切换表情回应（存在则取消，不存在则添加） */
    Map<String, Object> toggleReaction(String messageId, String emoji, String operatorId);

    /** 某条消息的表情聚合：emoji → 用户列表 */
    Map<String, List<String>> reactionsOf(String messageId);

    /** 批量获取多条消息的表情（避免逐条查询） */
    Map<String, Map<String, List<String>>> reactionsOfMessages(List<String> messageIds);

    /** 标记频道已读至指定时间（默认当前时间） */
    void markRead(String channelId, String userId, LocalDateTime readAt);

    /** 频道未读数（按 last_read_at 之后的消息数） */
    int unreadCount(String channelId, String userId);

    /** 频道成员的已读情况：userId → lastReadAt（用于"已读 N 人"） */
    Map<String, Object> readSummary(String channelId, int limit);
}
