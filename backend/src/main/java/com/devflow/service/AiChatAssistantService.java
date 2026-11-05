package com.devflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.entity.ChatMessage;
import com.devflow.entity.User;
import com.devflow.mapper.ChatMessageMapper;
import com.devflow.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 AI 助手服务
 * 检测频道消息中的 @AI 提及，异步调用 AI 服务（RAG 问答，未命中知识库时降级普通对话），
 * 以 "AI助手" 系统用户身份把回复写回频道并广播。
 */
@Slf4j
@Service
public class AiChatAssistantService {

    /** AI 助手的系统用户名（同时也是聊天中显示的发送者名称） */
    public static final String AI_USERNAME = "AI助手";

    /** @AI 提及：要求 @ 前是行首或空白，避免误伤邮箱等文本 */
    private static final Pattern AI_MENTION =
            Pattern.compile("(?i)(^|\\s)@(ai助手|ai 助手|ai)\\b");

    private final ChatMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    public AiChatAssistantService(
            ChatMessageMapper messageMapper,
            UserMapper userMapper,
            SimpMessagingTemplate messagingTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            @Value("${ai.service.base-url:http://localhost:8000/api/v1}") String aiBaseUrl) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.messagingTemplate = messagingTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(factory)
                .build();
    }

    /** 消息是否 @ 了 AI 助手 */
    public boolean isAiMention(String content) {
        return content != null && AI_MENTION.matcher(content).find();
    }

    /** 去掉 @AI 提及，提取真正的问题 */
    public String extractQuestion(String content) {
        Matcher m = AI_MENTION.matcher(content);
        String question = m.replaceAll(" ").trim();
        return question.isEmpty() ? content.trim() : question;
    }

    /**
     * 异步回复频道：调用 AI 生成答案，以 AI 助手身份入库并广播
     * 失败时发送一条提示消息，保证用户有反馈
     */
    @Async("aiExecutor")
    public void replyToChannel(String channelId, String question) {
        String answer = askAi(question);
        try {
            User aiUser = ensureAiUser();
            ChatMessage reply = new ChatMessage();
            reply.setChannelId(channelId);
            reply.setSenderId(aiUser.getId());
            reply.setContent(answer);
            reply.setType("TEXT");
            reply.setIsEdited(0);
            reply.setIsDeleted(0);
            messageMapper.insert(reply);

            // 广播时带上发送者信息，与历史消息联表查询的返回结构一致
            reply.setSenderName(aiUser.getUsername());
            reply.setSenderAvatar(aiUser.getAvatar());
            messagingTemplate.convertAndSend("/topic/channel." + channelId, reply);
        } catch (Exception e) {
            log.warn("AI 助手回复写入频道失败: channelId={}, 原因={}", channelId, e.getMessage());
        }
    }

    /** RAG 问答优先，知识库未命中或调用失败时降级为普通对话 */
    private String askAi(String question) {
        try {
            String raw = restClient.post()
                    .uri("/rag/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", question, "top_k", 4))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(raw);
            boolean hasSources = node.path("sources").isArray() && !node.path("sources").isEmpty();
            if (hasSources) {
                return node.path("answer").asText("");
            }
        } catch (Exception e) {
            log.info("RAG 问答不可用，降级为普通对话: {}", e.getMessage());
        }

        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messages", java.util.List.of(Map.of("role", "user", "content", question)),
                            "temperature", 0.3))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(raw);
            String content = node.path("content").asText("");
            if (!content.isBlank()) {
                return content;
            }
        } catch (Exception e) {
            log.warn("AI 对话调用失败: {}", e.getMessage());
        }
        return "⚠️ AI 助手暂时无法回复，请确认 ai-service 已启动且配置了 LLM API Key，稍后再试。";
    }

    /** 获取或懒创建 AI 助手系统用户（聊天历史联表 sys_user 渲染发送者信息） */
    private User ensureAiUser() {
        User aiUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, AI_USERNAME));
        if (aiUser != null) {
            return aiUser;
        }
        User user = new User();
        user.setUsername(AI_USERNAME);
        user.setEmail("ai-assistant@devflow.internal");
        // 随机密码，禁止登录；仅作为消息发送者的系统账号
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("已创建 AI 助手系统用户: {}", user.getId());
        return user;
    }
}
