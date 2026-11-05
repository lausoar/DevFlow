package com.devflow.config;

import com.devflow.service.PresenceService;
import com.devflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 认证拦截器
 * 在 STOMP CONNECT 时从 token 中提取用户ID，存入会话属性；
 * 并记录该会话的在线状态（PresenceService）
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final PresenceService presenceService;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 从 STOMP header 中获取 token
                    String token = accessor.getFirstNativeHeader("Authorization");
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                    }

                    if (token != null && jwtUtil.validateToken(token)) {
                        String userId = jwtUtil.getUserIdFromToken(token);
                        accessor.getSessionAttributes().put("userId", userId);
                        // 设置 STOMP 会话用户身份，使 /user 目的地能够按用户路由
                        accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, null));

                        // 记录在线会话并广播（断开由 WebSocketPresenceListener 兜底）
                        presenceService.connect(userId, accessor.getSessionId());
                        presenceService.broadcast();
                    }
                }

                return message;
            }
        });
    }
}
