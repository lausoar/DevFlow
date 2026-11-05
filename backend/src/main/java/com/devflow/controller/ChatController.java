package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.dto.request.SendMessageRequest;
import com.devflow.entity.ChatChannel;
import com.devflow.entity.ChatMessage;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.User;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.mapper.ChatMessageMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AiChatAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天控制器 - REST + WebSocket
 */
@Tag(name = "聊天", description = "频道管理、消息收发")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatChannelMapper channelMapper;
    private final ChatMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final com.devflow.service.ChatMentionService chatMentionService;
    private final com.devflow.service.ProjectAccessService projectAccessService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiChatAssistantService aiAssistant;

    // ==================== REST 接口 ====================

    @Operation(summary = "获取我的频道列表")
    @GetMapping("/channels")
    public R<List<ChatChannel>> getMyChannels(Authentication authentication) {
        // 频道可见性 = 项目可见性：项目群只对项目成员开放（超管可见本企业项目群）
        List<String> visibleIds = projectAccessService.visibleProjectIds((String) authentication.getPrincipal());
        if (visibleIds.isEmpty()) {
            return R.ok(List.of());
        }
        List<ChatChannel> channels = channelMapper.selectList(
            new LambdaQueryWrapper<ChatChannel>()
                .in(ChatChannel::getProjectId, visibleIds)
                .orderByDesc(ChatChannel::getCreatedAt)
        );
        return R.ok(channels);
    }

    /**
     * 频道成员：项目群的成员即项目成员（动态派生自 project_member，
     * 项目加人/移除自动同步，无需独立频道成员表）
     */
    @Operation(summary = "获取频道成员（项目群=项目成员）")
    @GetMapping("/channels/{channelId}/members")
    public R<List<ProjectMember>> getChannelMembers(@PathVariable String channelId, Authentication authentication) {
        ChatChannel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw new BusinessException(404, "频道不存在");
        }
        if ("PROJECT".equals(channel.getType()) && StringUtils.hasText(channel.getProjectId())) {
            if (!projectAccessService.canView((String) authentication.getPrincipal(), channel.getProjectId())) {
                throw new BusinessException(404, "频道不存在");
            }
            return R.ok(projectMemberMapper.selectMembersWithUser(channel.getProjectId()));
        }
        return R.ok(List.of());
    }

    @Operation(summary = "创建频道")
    @PostMapping("/channels")
    public R<ChatChannel> createChannel(@RequestBody ChatChannel channel) {
        channelMapper.insert(channel);
        return R.ok(channel);
    }

    @Operation(summary = "获取频道消息历史")
    @GetMapping("/channels/{channelId}/messages")
    public R<Page<ChatMessage>> getMessages(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {

        Page<ChatMessage> pageParam = new Page<>(page, pageSize);
        return R.ok(messageMapper.selectMessagesWithUser(pageParam, channelId));
    }

    /**
     * REST 发送消息（兼容非 WebSocket 客户端）
     * 消息入库后尝试通过 WebSocket 广播（如果有活跃连接）
     */
    @Operation(summary = "发送消息（REST）")
    @PostMapping("/channels/{channelId}/messages")
    public R<ChatMessage> sendMessageRest(
            @PathVariable String channelId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {

        String senderId = (String) authentication.getPrincipal();

        // 保存消息到数据库
        ChatMessage message = new ChatMessage();
        message.setChannelId(channelId);
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setType(request.getType() != null ? request.getType() : "TEXT");
        message.setReplyToId(request.getReplyToId());
        message.setIsEdited(0);
        message.setIsDeleted(0);
        messageMapper.insert(message);

        // 填充发送者信息
        User sender = userMapper.selectById(senderId);
        if (sender != null) {
            message.setSenderName(sender.displayName());
            message.setSenderAvatar(sender.getAvatar());
        }

        // 尝试通过 WebSocket 广播（不影响 REST 返回）
        try {
            messagingTemplate.convertAndSend("/topic/channel." + channelId, message);
        } catch (Exception ignored) {
            // WebSocket 未连接时忽略广播失败
        }

        // 广播轻量聊天事件（仅含 channelId+senderId，不含内容防泄露）：
        // 全局监听端据此在任意页面刷新消息列表并维护侧边栏未读角标
        try {
            messagingTemplate.convertAndSend("/topic/chat.events",
                    java.util.Map.of("channelId", channelId, "senderId", senderId));
        } catch (Exception ignored) {
        }

        // @提及成员通知
        chatMentionService.processMentions(channelId, senderId, request.getContent());

        // @AI 提及：异步触发 AI 助手回复
        if (aiAssistant.isAiMention(request.getContent())) {
            aiAssistant.replyToChannel(channelId, aiAssistant.extractQuestion(request.getContent()));
        }

        return R.ok(message);
    }

    // ==================== WebSocket 消息 ====================

    /**
     * 处理 WebSocket 发送消息
     * 客户端发送: /app/chat.send
     * 广播到: /topic/channel.{channelId}
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        // 从 WebSocket 会话中获取用户ID
        String senderId = (String) headerAccessor.getSessionAttributes().get("userId");
        if (senderId == null) {
            senderId = "anonymous";
        }

        // 保存消息到数据库
        ChatMessage message = new ChatMessage();
        message.setChannelId(request.getChannelId());
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setType(request.getType() != null ? request.getType() : "TEXT");
        message.setReplyToId(request.getReplyToId());
        message.setIsEdited(0);
        message.setIsDeleted(0);
        messageMapper.insert(message);

        // 广播到频道
        messagingTemplate.convertAndSend(
            "/topic/channel." + request.getChannelId(),
            message
        );

        // 广播轻量聊天事件（同 REST 路径，仅含 ID 不含内容）
        messagingTemplate.convertAndSend("/topic/chat.events",
            java.util.Map.of("channelId", request.getChannelId(), "senderId", senderId));

        // @AI 提及：异步触发 AI 助手回复
        if (aiAssistant.isAiMention(request.getContent())) {
            aiAssistant.replyToChannel(
                request.getChannelId(), aiAssistant.extractQuestion(request.getContent()));
        }

        // @提及成员通知
        chatMentionService.processMentions(request.getChannelId(), senderId, request.getContent());
    }
}
