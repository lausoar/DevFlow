package com.devflow.controller;

import com.devflow.dto.request.SendMessageRequest;
import com.devflow.entity.ChatChannel;
import com.devflow.entity.ChatMessage;
import com.devflow.entity.User;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.mapper.ChatMessageMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AiChatAssistantService;
import com.devflow.service.ChatMentionService;
import com.devflow.service.ChatMessageActionService;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController 实时事件隔离")
class ChatControllerTest {

    private static final String USER_ID = "u1";
    private static final String CHANNEL_ID = "c1";
    private static final String PROJECT_ID = "p1";

    @Mock private ChatChannelMapper channelMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private ChatMentionService chatMentionService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AiChatAssistantService aiAssistant;
    @Mock private AccessGuard guard;
    @Mock private ChatMessageActionService messageActionService;
    @Mock private Authentication authentication;

    @InjectMocks
    private ChatController chatController;
    @Test
    @DisplayName("轻量聊天事件只发布到对应频道 topic")
    void metadataEventUsesChannelTopic() {
        when(guard.userId(authentication)).thenReturn(USER_ID);
        ChatChannel channel = new ChatChannel();
        channel.setId(CHANNEL_ID);
        channel.setType("PROJECT");
        channel.setProjectId(PROJECT_ID);
        when(channelMapper.selectById(CHANNEL_ID)).thenReturn(channel);
        when(projectAccessService.canView(USER_ID, PROJECT_ID)).thenReturn(true);
        User sender = new User();
        sender.setId(USER_ID);
        sender.setRealName("Smoke User");
        when(userMapper.selectById(USER_ID)).thenReturn(sender);
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("hello");

        chatController.sendMessageRest(CHANNEL_ID, request, authentication);

        verify(messagingTemplate).convertAndSend(eq("/topic/channel." + CHANNEL_ID), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend("/topic/chat.events." + CHANNEL_ID,
                Map.of("channelId", CHANNEL_ID, "senderId", USER_ID));
        verify(chatMentionService).processMentions(CHANNEL_ID, USER_ID, "hello");
    }
}
