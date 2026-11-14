package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
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
import com.devflow.security.AccessGuard;
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
 *
 * 鉴权口径：所有读写都必须先判定"该频道所属项目对当前用户可见"；
 * WebSocket 侧不存在匿名降级 —— 未认证会话在拦截器阶段就被拒绝。
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
    private final AccessGuard guard;

    // ==================== REST 接口 ====================

    @Operation(summary = "获取我的频道列表")
    @GetMapping("/channels")
    public R<List<ChatChannel>> getMyChannels(Authentication authentication) {
        // 频道可见性 = 项目可见性：项目群只对项目成员开放（超管可见本企业项目群）
        List<String> visibleIds = projectAccessService.visibleProjectIds(guard.userId(authentication));
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
        ChatChannel channel = requireChannel(guard.userId(authentication), channelId);
        return R.ok(projectMemberMapper.selectMembersWithUser(channel.getProjectId()));
    }

    /**
     * 创建频道：仅支持在可见项目中创建 PROJECT 频道（部门群/私聊尚未产品化，
     * 避免产生无人可见的孤儿频道；也避免任意登录用户向全平台注入频道）。
     */
    @Operation(summary = "创建频道（仅限可见项目）")
    @PostMapping("/channels")
    public R<ChatChannel> createChannel(@RequestBody ChatChannel channel, Authentication authentication) {
        String userId = guard.userId(authentication);
        if (!StringUtils.hasText(channel.getProjectId())) {
            throw new BusinessException(400, "频道必须归属到项目");
        }
        if (!StringUtils.hasText(channel.getName()) || channel.getName().length() > 100) {
            throw new BusinessException(400, "频道名称长度需在 1~100 字之间");
        }
        guard.requireProjectPermission(userId, channel.getProjectId(), PermissionCodes.PROJECT_EDIT);

        ChatChannel target = new ChatChannel();
        target.setName(channel.getName().trim());
        target.setType("PROJECT");
        target.setProjectId(channel.getProjectId());
        channelMapper.insert(target);
        return R.ok(target);
    }

    @Operation(summary = "获取频道消息历史")
    @GetMapping("/channels/{channelId}/messages")
    public R<Page<ChatMessage>> getMessages(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            Authentication authentication) {

        requireChannel(guard.userId(authentication), channelId);
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

        String senderId = guard.userId(authentication);
        requireChannel(senderId, channelId);

        ChatMessage message = persistAndBroadcast(channelId, senderId, request);
        return R.ok(message);
    }

    // ==================== WebSocket 消息 ====================

    /**
     * 处理 WebSocket 发送消息
     * 客户端发送: /app/chat.send
     * 广播到: /topic/channel.{channelId}
     *
     * 会话身份由 WebSocketAuthInterceptor 在 CONNECT 阶段写入；
     * 拿不到身份说明连接已被拒绝，这里再兜底一次，绝不写 anonymous 消息。
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String senderId = headerAccessor.getSessionAttributes() != null
                ? (String) headerAccessor.getSessionAttributes().get("userId")
                : null;
        if (!StringUtils.hasText(senderId)) {
            throw new BusinessException(401, "WebSocket 会话未认证");
        }
        if (!StringUtils.hasText(request.getChannelId())) {
            throw new BusinessException(400, "缺少频道 ID");
        }
        requireChannel(senderId, request.getChannelId());

        persistAndBroadcast(request.getChannelId(), senderId, request);
    }

    // ==================== 内部 ====================

    /** 频道必须存在且所属项目对当前用户可见，否则 404（不泄露存在性） */
    private ChatChannel requireChannel(String userId, String channelId) {
        ChatChannel channel = StringUtils.hasText(channelId) ? channelMapper.selectById(channelId) : null;
        if (channel == null || !StringUtils.hasText(channel.getProjectId())
                || !projectAccessService.canView(userId, channel.getProjectId())) {
            throw new BusinessException(404, "频道不存在");
        }
        return channel;
    }

    /** 入库 + 广播 + @提及 / @AI 处理 */
    private ChatMessage persistAndBroadcast(String channelId, String senderId, SendMessageRequest request) {
        ChatMessage message = new ChatMessage();
        message.setChannelId(channelId);
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setType(request.getType() != null ? request.getType() : "TEXT");
        message.setReplyToId(request.getReplyToId());
        message.setIsEdited(0);
        message.setIsDeleted(0);
        messageMapper.insert(message);

        User sender = userMapper.selectById(senderId);
        if (sender != null) {
            message.setSenderName(sender.displayName());
            message.setSenderAvatar(sender.getAvatar());
        }

        // 广播失败只影响实时性，不影响消息落库（历史消息仍可拉取）
        messagingTemplate.convertAndSend("/topic/channel." + channelId, message);
        // 轻量事件：仅含 channelId+senderId，不含内容，供侧边栏未读角标使用
        messagingTemplate.convertAndSend("/topic/chat.events",
                java.util.Map.of("channelId", channelId, "senderId", senderId));

        chatMentionService.processMentions(channelId, senderId, request.getContent());

        if (aiAssistant.isAiMention(request.getContent())) {
            aiAssistant.replyToChannel(channelId, aiAssistant.extractQuestion(request.getContent()));
        }
        return message;
    }
}
