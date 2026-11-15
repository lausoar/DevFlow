package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.ChatMessage;
import com.devflow.entity.ChatReaction;
import com.devflow.entity.ChatReadState;
import com.devflow.entity.ProjectMember;
import com.devflow.mapper.ChatMessageMapper;
import com.devflow.mapper.ChatReactionMapper;
import com.devflow.mapper.ChatReadStateMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.service.ChatMessageActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息编辑/撤回/表情/已读实现。
 *
 * 撤回语义：content 置为占位文本、原文写入 original_content。
 * 这样前端只需判断 recalled_at 是否非空就能渲染"消息已撤回"，
 * 而审计侧仍能查到原文（合规要求）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageActionServiceImpl implements ChatMessageActionService {

    private final ChatMessageMapper messageMapper;
    private final ChatReactionMapper reactionMapper;
    private final ChatReadStateMapper readStateMapper;
    private final ProjectMemberMapper projectMemberMapper;

    /** 撤回后对外显示的占位（前端也可自行渲染，这里保证接口返回不泄露内容） */
    private static final String RECALLED_PLACEHOLDER = "此消息已撤回";

    @Override
    public Map<String, Object> edit(String messageId, String content, String operatorId) {
        ChatMessage msg = requireOwnMessage(messageId, operatorId);
        if (msg.getRecalledAt() != null) {
            throw new BusinessException(400, "已撤回的消息不能编辑");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (content.length() > 5000) {
            throw new BusinessException(400, "消息内容过长");
        }
        ChatMessage patch = new ChatMessage();
        patch.setId(messageId);
        patch.setContent(content);
        patch.setIsEdited(1);
        patch.setEditedAt(LocalDateTime.now());
        messageMapper.updateById(patch);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", messageId);
        m.put("content", content);
        m.put("isEdited", 1);
        m.put("editedAt", patch.getEditedAt());
        return m;
    }

    @Override
    public Map<String, Object> recall(String messageId, String operatorId) {
        ChatMessage msg = requireOwnMessage(messageId, operatorId);
        if (msg.getRecalledAt() != null) {
            throw new BusinessException(400, "消息已撤回");
        }
        ChatMessage patch = new ChatMessage();
        patch.setId(messageId);
        patch.setContent(RECALLED_PLACEHOLDER);
        patch.setOriginalContent(msg.getContent());
        patch.setRecalledAt(LocalDateTime.now());
        patch.setRecalledBy(operatorId);
        messageMapper.updateById(patch);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", messageId);
        m.put("recalledAt", patch.getRecalledAt());
        m.put("recalledBy", operatorId);
        return m;
    }

    @Override
    public Map<String, Object> toggleReaction(String messageId, String emoji, String operatorId) {
        if (!StringUtils.hasText(emoji) || emoji.length() > 16) {
            throw new BusinessException(400, "无效的表情");
        }
        ChatMessage msg = messageMapper.selectById(messageId);
        if (msg == null || Integer.valueOf(1).equals(msg.getIsDeleted())) {
            throw new BusinessException(404, "消息不存在");
        }
        ChatReaction existing = reactionMapper.selectOne(new LambdaQueryWrapper<ChatReaction>()
                .eq(ChatReaction::getMessageId, messageId)
                .eq(ChatReaction::getUserId, operatorId)
                .eq(ChatReaction::getEmoji, emoji));
        boolean added;
        if (existing != null) {
            reactionMapper.deleteById(existing.getId());
            added = false;
        } else {
            ChatReaction row = new ChatReaction();
            row.setMessageId(messageId);
            row.setUserId(operatorId);
            row.setEmoji(emoji);
            reactionMapper.insert(row);
            added = true;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("messageId", messageId);
        m.put("emoji", emoji);
        m.put("added", added);
        m.put("reactions", reactionsOf(messageId));
        return m;
    }

    @Override
    public Map<String, List<String>> reactionsOf(String messageId) {
        List<ChatReaction> rows = reactionMapper.selectList(new LambdaQueryWrapper<ChatReaction>()
                .eq(ChatReaction::getMessageId, messageId));
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (ChatReaction r : rows) {
            out.computeIfAbsent(r.getEmoji(), k -> new ArrayList<>()).add(r.getUserId());
        }
        return out;
    }

    @Override
    public Map<String, Map<String, List<String>>> reactionsOfMessages(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<ChatReaction> rows = reactionMapper.selectList(new LambdaQueryWrapper<ChatReaction>()
                .in(ChatReaction::getMessageId, messageIds));
        Map<String, Map<String, List<String>>> out = new HashMap<>();
        for (ChatReaction r : rows) {
            out.computeIfAbsent(r.getMessageId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getEmoji(), k -> new ArrayList<>())
                    .add(r.getUserId());
        }
        return out;
    }

    @Override
    public void markRead(String channelId, String userId, LocalDateTime readAt) {
        LocalDateTime target = readAt != null ? readAt : LocalDateTime.now();
        ChatReadState existing = readStateMapper.selectOne(new LambdaQueryWrapper<ChatReadState>()
                .eq(ChatReadState::getChannelId, channelId)
                .eq(ChatReadState::getUserId, userId));
        if (existing == null) {
            ChatReadState row = new ChatReadState();
            row.setChannelId(channelId);
            row.setUserId(userId);
            row.setLastReadAt(target);
            readStateMapper.insert(row);
        } else if (existing.getLastReadAt() == null || target.isAfter(existing.getLastReadAt())) {
            // 只前进不后退：乱序到达的"已读"请求不会把位置改小
            existing.setLastReadAt(target);
            readStateMapper.updateById(existing);
        }
    }

    @Override
    public int unreadCount(String channelId, String userId) {
        ChatReadState state = readStateMapper.selectOne(new LambdaQueryWrapper<ChatReadState>()
                .eq(ChatReadState::getChannelId, channelId)
                .eq(ChatReadState::getUserId, userId));
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getChannelId, channelId)
                .eq(ChatMessage::getIsDeleted, 0)
                .ne(ChatMessage::getSenderId, userId);
        if (state != null && state.getLastReadAt() != null) {
            wrapper.gt(ChatMessage::getCreatedAt, state.getLastReadAt());
        }
        Long n = messageMapper.selectCount(wrapper);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public Map<String, Object> readSummary(String channelId, int limit) {
        List<ChatReadState> states = readStateMapper.selectList(new LambdaQueryWrapper<ChatReadState>()
                .eq(ChatReadState::getChannelId, channelId)
                .orderByDesc(ChatReadState::getLastReadAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> byUser = new LinkedHashMap<>();
        states.forEach(s -> byUser.put(s.getUserId(), s.getLastReadAt().toString()));
        m.put("readers", byUser);
        m.put("readCount", states.size());
        return m;
    }

    /** 消息必须存在且属于操作者本人 */
    private ChatMessage requireOwnMessage(String messageId, String operatorId) {
        ChatMessage msg = messageMapper.selectById(messageId);
        if (msg == null || Integer.valueOf(1).equals(msg.getIsDeleted())) {
            throw new BusinessException(404, "消息不存在");
        }
        if (!operatorId.equals(msg.getSenderId())) {
            throw new BusinessException(403, "只能操作自己发送的消息");
        }
        return msg;
    }
}
