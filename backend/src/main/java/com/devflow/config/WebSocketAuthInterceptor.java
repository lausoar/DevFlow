package com.devflow.config;

import com.devflow.util.JwtUtil;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.security.JwtAuthFilter;
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
 * - SUBSCRIBE：/topic/channel.{id} 与 /topic/chat.events.{id} 必须对当前用户可见，
 *   /topic/presence.{enterpriseId} 必须属于当前用户企业，避免跨项目/跨租户窃听。
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
    private final UserMapper userMapper;

    /** 项目频道消息与轻量事件 topic；两者使用同一个频道可见性判定。 */
    private static final String CHANNEL_TOPIC_PREFIX = "/topic/channel.";
    private static final String CHAT_EVENTS_TOPIC = "/topic/chat.events";
    private static final String CHAT_EVENTS_TOPIC_PREFIX = CHAT_EVENTS_TOPIC + ".";
    private static final String PRESENCE_TOPIC = "/topic/presence";
    private static final String PRESENCE_TOPIC_PREFIX = PRESENCE_TOPIC + ".";

    /** 会话 → userId 缓存（SEND/SUBSCRIBE 阶段 sessionAttributes 可能为空，用会话 ID 反查） */
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

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
                            sessionTokens.remove(sessionId);
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

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.MESSAGE) {
                    return message;
                }
                try {
                    User user = requireSessionUser(accessor);
                    // 订阅时授权不等于永久授权：成员被移出项目后，已建立的订阅
                    // 也必须在每一条出站消息上重新校验，否则仍会持续收到内容。
                    requireChannelAccess(user.getId(), accessor.getDestination(), "接收该频道消息");
                    return message;
                } catch (RuntimeException e) {
                    return null;
                }
            }
        });
    }

    public void cleanupSession(String sessionId) {
        if (sessionId != null) {
            sessionUsers.remove(sessionId);
            sessionTokens.remove(sessionId);
        }
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
        if (!jwtUtil.validateAccessToken(token) || tokenBlacklistService.isBlacklisted(token)) {
            throw new AccessDeniedException("WebSocket 登录凭证无效或已过期");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || JwtAuthFilter.sessionVersion(user) != jwtUtil.getSessionVersionFromToken(token)) {
            throw new AccessDeniedException("WebSocket 登录状态已失效");
        }
        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put("userId", userId);
            accessor.getSessionAttributes().put("token", token);
        }
        if (accessor.getSessionId() != null) {
            sessionUsers.put(accessor.getSessionId(), userId);
            sessionTokens.put(accessor.getSessionId(), token);
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
        String userId = requireSessionUser(accessor).getId();
        requireChannelAccess(userId, accessor.getDestination(), "向该频道发送消息");
    }

    /** SUBSCRIBE：频道消息/轻量事件按项目授权，在线名单必须属于本企业。 */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        User user = requireSessionUser(accessor);
        String destination = accessor.getDestination();
        if (CHAT_EVENTS_TOPIC.equals(destination)) {
            throw new AccessDeniedException("全局聊天事件订阅已禁用，请按频道订阅");
        }
        requireChannelAccess(user.getId(), destination, "订阅该频道");

        if (PRESENCE_TOPIC.equals(destination)) {
            throw new AccessDeniedException("在线名单必须按企业订阅");
        }
        if (destination != null && destination.startsWith(PRESENCE_TOPIC_PREFIX)) {
            String enterpriseId = destination.substring(PRESENCE_TOPIC_PREFIX.length()).trim();
            if (enterpriseId.isEmpty() || !enterpriseId.equals(user.getEnterpriseId())) {
                throw new AccessDeniedException("无权订阅其他企业的在线名单");
            }
        }
    }

    private User requireSessionUser(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String userId = sessionId != null ? sessionUsers.get(sessionId) : null;
        String token = sessionId != null ? sessionTokens.get(sessionId) : null;
        if (accessor.getSessionAttributes() != null) {
            if (userId == null && accessor.getSessionAttributes().get("userId") != null) {
                userId = String.valueOf(accessor.getSessionAttributes().get("userId"));
            }
            if (token == null && accessor.getSessionAttributes().get("token") != null) {
                token = String.valueOf(accessor.getSessionAttributes().get("token"));
            }
        }
        if (userId == null || token == null || !jwtUtil.validateAccessToken(token)
                || tokenBlacklistService.isBlacklisted(token)
                || !userId.equals(jwtUtil.getUserIdFromToken(token))) {
            throw new AccessDeniedException("WebSocket 会话未认证或已失效");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || JwtAuthFilter.sessionVersion(user) != jwtUtil.getSessionVersionFromToken(token)) {
            throw new AccessDeniedException("WebSocket 登录状态已失效");
        }
        return user;
    }

    private void requireChannelAccess(String userId, String destination, String action) {
        if (!isChannelDestination(destination)) {
            return;
        }
        String channelId = channelIdOf(destination);
        if (channelId == null || !canAccessChannel(userId, channelId)) {
            throw new AccessDeniedException("无权" + action);
        }
    }

    private boolean isChannelDestination(String destination) {
        return destination != null && (destination.startsWith(CHANNEL_TOPIC_PREFIX)
                || destination.startsWith(CHAT_EVENTS_TOPIC_PREFIX));
    }

    /** /topic/channel.{id} 与 /topic/chat.events.{id} → id；其他目的地返回 null。 */
    private String channelIdOf(String destination) {
        if (!isChannelDestination(destination)) {
            return null;
        }
        String prefix = destination.startsWith(CHANNEL_TOPIC_PREFIX)
                ? CHANNEL_TOPIC_PREFIX : CHAT_EVENTS_TOPIC_PREFIX;
        String id = destination.substring(prefix.length()).trim();
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
