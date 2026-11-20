package com.devflow.service;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceService 企业在线名单")
class PresenceServiceTest {

    @Mock
    private ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PresenceService presenceService;

    @Test
    @DisplayName("用户仍有其他端点时不离线")
    void remainingEndpointKeepsUserOnline() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        when(userMapper.selectById("u1")).thenReturn(user("u1", "e1"));
        presenceService.connect("u1", "s1");
        presenceService.connect("u1", "s2");

        Optional<String> offline = presenceService.disconnect("s1");
        presenceService.broadcast();

        assertThat(offline).isEmpty();
        assertThat(presenceService.onlineUserIds()).containsExactly("u1");
        verify(messagingTemplate).convertAndSend("/topic/presence.e1",
                Map.of("onlineUserIds", List.of("u1")));
    }

    @Test
    @DisplayName("企业最后用户下线时显式广播空名单")
    void lastDisconnectBroadcastsEmptyEnterpriseRoster() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        when(userMapper.selectById("u1")).thenReturn(user("u1", "e1"));
        presenceService.connect("u1", "s1");
        presenceService.broadcast();
        reset(messagingTemplate);

        Optional<String> offline = presenceService.disconnect("s1");
        presenceService.broadcast();

        assertThat(offline).contains("u1");
        assertThat(presenceService.onlineUserIds()).isEmpty();
        verify(messagingTemplate).convertAndSend("/topic/presence.e1",
                Map.of("onlineUserIds", List.of()));
    }

    @Test
    @DisplayName("空名单只发给离线企业，不影响其他企业")
    void emptyRosterIsScopedToDisconnectedEnterprise() {
        when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        when(userMapper.selectById("u1")).thenReturn(user("u1", "e1"));
        when(userMapper.selectById("u2")).thenReturn(user("u2", "e2"));
        presenceService.connect("u1", "s1");
        presenceService.connect("u2", "s2");
        presenceService.broadcast();
        reset(messagingTemplate);

        presenceService.disconnect("s1");
        presenceService.broadcast();

        verify(messagingTemplate).convertAndSend("/topic/presence.e1",
                Map.of("onlineUserIds", List.of()));
        verify(messagingTemplate).convertAndSend("/topic/presence.e2",
                Map.of("onlineUserIds", List.of("u2")));
    }

    private static User user(String userId, String enterpriseId) {
        User user = new User();
        user.setId(userId);
        user.setEnterpriseId(enterpriseId);
        return user;
    }
}
