package com.devflow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置 - STOMP 协议
 *
 * 跨域来源与 HTTP CORS 使用同一份白名单（websocket.allowed-origin-patterns），
 * 不再 setAllowedOriginPatterns("*") —— 通配会让任意站点携带用户浏览器凭证建立连接。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端订阅地址前缀
        config.enableSimpleBroker("/topic", "/queue");
        // 客户端发送地址前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户特定消息前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(webSocketAuthInterceptor.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }
}
