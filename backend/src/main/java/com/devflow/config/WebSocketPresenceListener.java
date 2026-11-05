package com.devflow.config;

import com.devflow.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 会话断开监听
 * 连接建立时 WebSocketAuthInterceptor 已记录会话；此处兜底处理断开
 * （浏览器直接关闭、断网时客户端不会发送 DISCONNECT 帧，由事件感知）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final PresenceService presenceService;

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        presenceService.disconnect(event.getSessionId()).ifPresent(userId -> {
            log.debug("用户下线: {}", userId);
            presenceService.broadcast();
        });
    }
}
