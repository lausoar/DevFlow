package com.devflow.config;

import com.devflow.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.devflow.service.PresenceService;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 认证拦截器
 *
 * 安全口径（见 docs/AUDIT_REPORT_2026-11.md §A2）：
 * - CONNECT：token 缺失/无效直接拒绝连接，不存在"匿名连接"；
 * - SEND：必须有会话身份，且目标频道必须对当前用户可见（项目群按项目成员判定）；
 * - SUBSCRIBE：项目频道（/topic/channel.{id}）必须对当前用户可见，
 *   避免任意人订阅他人项目群实时窃听。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final PresenceService presenceService;
    /** 频道可见性判定（避免与 ChatController 形成循环依赖，这里只依赖 mapper 层） */
    private final com.devflow.mapper.ChatChannelMapper channelMapper;
    private final com.devflow.service.ProjectAccessService projectAccessService;
    private final com.devflow.service.TokenBlacklistService tokenBlacklistService;

    /** 项目频道 topic 前缀：/topic/channel.{channelId} */
    private static final String CHANNEL_TOPIC_PREFIX = "/topic/channel.";

    /** 会话 → userId 缓存（SEND/SUBSCRIBE 阶段 sessionAttributes 可能为空，用会话 ID 反查） */
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    @Value("${websocket.allowed-origin-patterns:http://localhost:3000,http://localhost:3001}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                switch (accessor.getCommand()) {
                    case CONNECT -> handleConnect(accessor);
                    case SUBSCRIBE -> handleSubscribe(accessor);
                    case SEND -> handleSend(accessor);
                    case DISCONNECT -> {
                        String sessionId = accessor.getSessionId();
                        if (sessionId != null) {
                            sessionUsers.remove(sessionId);
                        }
                    }
                    default -> {
                        // 其他 STOMP 帧不额外处理
                    }
                }
                return message;
            }
        });
    }

    /** CONNECT：必须携带有效 JWT，否则拒绝握手（抛异常 → 客户端收到 ERROR 帧） */
    private void handleConnect(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        if (token == null) {
            token = accessor.getFirstNativeHeader("authorization");
        }
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            throw new AccessDeniedException("WebSocket 连接需要登录凭证");
        }
        if (!jwtUtil.validateToken(token) || tokenBlacklistService.isBlacklisted(token)) {
            throw new AccessDeniedException("WebSocket 登录凭证无效或已过期");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put("userId", userId);
        }
        if (accessor.getSessionId() != null) {
            sessionUsers.put(accessor.getSessionId(), userId);
        }
        // 设置 STOMP 会话用户身份，使 /user 目的地能够按用户路由
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, null));

        // 记录在线会话并广播（断开由 WebSocketPresenceListener 兜底）
        if (accessor.getSessionId() != null) {
            presenceService.connect(userId, accessor.getSessionId());
            presenceService.broadcast();
        }
    }

    /** SEND：必须有会话身份，且只能向自己可见的项目频道发消息 */
    private void handleSend(StompHeaderAccessor accessor) {
        String userId = requireSessionUser(accessor);
        String destination = accessor.getDestination();
        String channelId = channelIdOf(destination);
        if (channelId != null && !canAccessChannel(userId, channelId)) {
            throw new AccessDeniedException("无权向该频道发送消息");
        }
    }

    /** SUBSCRIBE：项目频道需可见；个人队列 /user/** 天然按会话隔离 */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String userId = requireSessionUser(accessor);
        String channelId = channelIdOf(accessor.getDestination());
        if (channelId != null && !canAccessChannel(userId, channelId)) {
            throw new AccessDeniedException("无权订阅该频道");
        }
    }

    private String requireSessionUser(StompHeaderAccessor accessor) {
        String userId = accessor.getSessionId() != null ? sessionUsers.get(accessor.getSessionId()) : null;
        if (userId == null && accessor.getSessionAttributes() != null) {
            Object attr = accessor.getSessionAttributes().get("userId");
            userId = attr != null ? String.valueOf(attr) : null;
        }
        if (userId == null) {
            throw new AccessDeniedException("WebSocket 会话未认证");
        }
        return userId;
    }

    /** /topic/channel.{id} → id；其他目的地返回 null（不需要频道级校验） */
    private String channelIdOf(String destination) {
        if (destination == null || !destination.startsWith(CHANNEL_TOPIC_PREFIX)) {
            return null;
        }
        String id = destination.substring(CHANNEL_TOPIC_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    /** 频道可见性：项目群按项目成员判定；非项目频道一律拒绝（当前产品未开放部门群/私聊） */
    private boolean canAccessChannel(String userId, String channelId) {
        com.devflow.entity.ChatChannel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            return false;
        }
        if (!"PROJECT".equals(channel.getType()) || channel.getProjectId() == null) {
            return false;
        }
        return projectAccessService.canView(userId, channel.getProjectId());
    }

    /** 供 WebSocketConfig 复用同一份白名单 */
    Set<String> allowedOrigins() {
        Set<String> patterns = new java.util.LinkedHashSet<>();
        for (String p : allowedOriginPatterns) {
            if (p != null && !p.isBlank()) {
                patterns.add(p.trim());
            }
        }
        return patterns;
    }
}
