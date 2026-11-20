package com.devflow.config;

import com.devflow.entity.ChatChannel;
import com.devflow.entity.User;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.PresenceService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthInterceptor 会话持续鉴权")
class WebSocketAuthInterceptorTest {

    private static final String SESSION_ID = "session-1";
    private static final String USER_ID = "u1";
    private static final String TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PresenceService presenceService;
    @Mock
    private ChatChannelMapper channelMapper;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ChannelRegistration inboundRegistration;
    @Mock
    private ChannelRegistration outboundRegistration;
    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private ChannelInterceptor inbound;
    private ChannelInterceptor outbound;

    @BeforeEach
    void captureInterceptors() {
        interceptor.configureClientInboundChannel(inboundRegistration);
        interceptor.configureClientOutboundChannel(outboundRegistration);
        inbound = registeredInterceptor(inboundRegistration);
        outbound = registeredInterceptor(outboundRegistration);
    }

    @Test
    @DisplayName("Refresh Token 不能建立 WebSocket 连接")
    void refreshTokenCannotConnect() {
        when(jwtUtil.validateAccessToken(REFRESH_TOKEN)).thenReturn(false);
        Message<?> connect = connectMessage(REFRESH_TOKEN);

        assertThatThrownBy(() -> inbound.preSend(connect, messageChannel))
                .isInstanceOf(AccessDeniedException.class);
        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
        verifyNoInteractions(userMapper, presenceService);
    }

    @Test
    @DisplayName("有效 Access Token 可以连接并保存会话凭证")
    void validAccessTokenCanConnect() {
        Message<?> connected = connectValidSession();

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                connected, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(USER_ID);
        assertThat(accessor.getSessionAttributes())
                .containsEntry("userId", USER_ID)
                .containsEntry("token", TOKEN);
        verify(presenceService).connect(USER_ID, SESSION_ID);
        verify(presenceService).broadcast();
    }

    @Test
    @DisplayName("有效会话的出站 MESSAGE 正常通过")
    void validSessionCanReceiveOutboundMessage() {
        connectValidSession();
        Message<?> message = outboundMessage();

        assertThat(outbound.preSend(message, messageChannel)).isSameAs(message);
    }

    @Test
    @DisplayName("连接后令牌过期，出站 MESSAGE 被丢弃")
    void expiredSessionHasOutboundMessageDropped() {
        connectValidSession();
        when(jwtUtil.validateAccessToken(TOKEN)).thenReturn(false);

        assertThat(outbound.preSend(outboundMessage(), messageChannel)).isNull();
    }

    @Test
    @DisplayName("连接后令牌被撤销，出站 MESSAGE 被丢弃")
    void revokedSessionHasOutboundMessageDropped() {
        connectValidSession();
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(true);

        assertThat(outbound.preSend(outboundMessage(), messageChannel)).isNull();
    }

    @Test
    @DisplayName("连接后会话版本变化，出站 MESSAGE 被丢弃")
    void sessionVersionMismatchHasOutboundMessageDropped() {
        connectValidSession();
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(4L));

        assertThat(outbound.preSend(outboundMessage(), messageChannel)).isNull();
    }

    @Test
    @DisplayName("断连事件清理认证会话，后续出站 MESSAGE 被丢弃")
    void disconnectListenerCleansSessionState() {
        connectValidSession();
        assertThat(outbound.preSend(outboundMessage(), messageChannel)).isNotNull();
        when(presenceService.disconnect(SESSION_ID)).thenReturn(Optional.of(USER_ID));
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn(SESSION_ID);
        WebSocketPresenceListener listener = new WebSocketPresenceListener(presenceService, interceptor);

        listener.onSessionDisconnect(event);

        assertThat(outbound.preSend(outboundMessage(), messageChannel)).isNull();
        verify(presenceService).disconnect(SESSION_ID);
        verify(presenceService, times(2)).broadcast();
    }

    @Test
    @DisplayName("项目成员可以订阅本频道的轻量聊天事件")
    void memberCanSubscribeToChannelEvents() {
        connectValidSession();
        when(channelMapper.selectById("c1")).thenReturn(projectChannel("c1", "p1"));
        when(projectAccessService.canView(USER_ID, "p1")).thenReturn(true);
        Message<?> subscribe = subscribeMessage("/topic/chat.events.c1");

        assertThat(inbound.preSend(subscribe, messageChannel)).isSameAs(subscribe);
    }

    @Test
    @DisplayName("拒绝订阅其他项目频道的轻量聊天事件")
    void crossProjectChannelEventsAreRejected() {
        connectValidSession();
        when(channelMapper.selectById("c2")).thenReturn(projectChannel("c2", "p2"));
        when(projectAccessService.canView(USER_ID, "p2")).thenReturn(false);

        assertThatThrownBy(() -> inbound.preSend(subscribeMessage("/topic/chat.events.c2"), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("用户只能订阅本企业在线名单")
    void presenceRequiresMatchingEnterprise() {
        connectValidSession();
        Message<?> own = subscribeMessage("/topic/presence.e1");

        assertThat(inbound.preSend(own, messageChannel)).isSameAs(own);
        assertThatThrownBy(() -> inbound.preSend(subscribeMessage("/topic/presence.e2"), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("旧全局聊天事件 topic 不允许订阅")
    void legacyGlobalChatEventsAreRejected() {
        connectValidSession();

        assertThatThrownBy(() -> inbound.preSend(subscribeMessage("/topic/chat.events"), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(channelMapper, projectAccessService);
    }

    private Message<?> connectValidSession() {
        when(jwtUtil.validateAccessToken(TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(3L));
        when(jwtUtil.getSessionVersionFromToken(TOKEN)).thenReturn(3L);
        return inbound.preSend(connectMessage(TOKEN), messageChannel);
    }

    private static Message<?> connectMessage(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId(SESSION_ID);
        Map<String, Object> sessionAttributes = new HashMap<>();
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        return new GenericMessage<>("connect", accessor.getMessageHeaders());
    }

    private static Message<?> subscribeMessage(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination(destination);
        return new GenericMessage<>("subscribe", accessor.getMessageHeaders());
    }

    private static ChatChannel projectChannel(String channelId, String projectId) {
        ChatChannel channel = new ChatChannel();
        channel.setId(channelId);
        channel.setProjectId(projectId);
        channel.setType("PROJECT");
        return channel;
    }

    private static Message<?> outboundMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setLeaveMutable(true);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/presence.e1");
        return new GenericMessage<>("outbound", accessor.getMessageHeaders());
    }

    private static User activeUser(long sessionVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setStatus("ACTIVE");
        user.setRole("MEMBER");
        user.setEnterpriseId("e1");
        user.setSessionVersion(sessionVersion);
        return user;
    }

    private static ChannelInterceptor registeredInterceptor(ChannelRegistration registration) {
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        verify(registration).interceptors(captor.capture());
        return captor.getValue();
    }
}
