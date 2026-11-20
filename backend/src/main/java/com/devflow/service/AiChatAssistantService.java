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

    /** 单条消息最多保留的引用来源数 */
    private static final int MAX_SOURCES = 20;

    private final ChatMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final com.devflow.mapper.ChatChannelMapper channelMapper;
    private final com.devflow.mapper.ProjectMapper projectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private final RestClient restClient;
    /** 服务间调用令牌（AI 服务强制认证后必须带上） */
    private final String serviceToken;

    public AiChatAssistantService(
            ChatMessageMapper messageMapper,
            UserMapper userMapper,
            com.devflow.mapper.ChatChannelMapper channelMapper,
            com.devflow.mapper.ProjectMapper projectMapper,
            SimpMessagingTemplate messagingTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            @Value("${ai.service.base-url:http://localhost:8000/api/v1}") String aiBaseUrl,
            @Value("${ai.service.token:}") String serviceToken) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.channelMapper = channelMapper;
        this.projectMapper = projectMapper;
        this.messagingTemplate = messagingTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.serviceToken = serviceToken;
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
        AiAnswer answer = askAi(question, channelId);
        try {
            User aiUser = ensureAiUser();
            ChatMessage reply = new ChatMessage();
            reply.setChannelId(channelId);
            reply.setSenderId(aiUser.getId());
            reply.setContent(answer.content());
            reply.setType("TEXT");
            reply.setIsEdited(0);
            reply.setIsDeleted(0);
            // 引用来源随消息持久化，历史消息里也能核对出处
            reply.setSources(answer.sourcesJson());
            messageMapper.insert(reply);

            // 广播时带上发送者信息，与历史消息联表查询的返回结构一致
            reply.setSenderName(aiUser.getUsername());
            reply.setSenderAvatar(aiUser.getAvatar());
            messagingTemplate.convertAndSend("/topic/channel." + channelId, reply);
        } catch (Exception e) {
            log.warn("AI 助手回复写入频道失败: channelId={}, 原因={}", channelId, e.getMessage());
        }
    }

    /** 答案正文 + 引用来源 JSON（无来源时为 null，避免大量空数组） */
    private record AiAnswer(String content, String sourcesJson) {
    }

    /** RAG 问答优先，知识库未命中或调用失败时降级为普通对话 */
    private AiAnswer askAi(String question) {
        return askAi(question, null);
    }

    /**
     * RAG 问答（带检索范围）。
     * 频道属于某个项目时把 project_id / enterprise_id 传给 AI 服务，
     * 否则 AI 侧无法做隔离会直接拒绝（宁可降级也不跨项目检索）。
     */
    private AiAnswer askAi(String question, String channelId) {
        Map<String, Object> ragBody = new java.util.HashMap<>();
        ragBody.put("question", question);
        ragBody.put("top_k", 4);
        Scope scope = resolveScope(channelId);
        if (scope.projectId() != null) {
            ragBody.put("project_id", scope.projectId());
        }
        if (scope.enterpriseId() != null) {
            ragBody.put("enterprise_id", scope.enterpriseId());
        }
        if (scope.projectId() == null && scope.enterpriseId() == null) {
            // 无范围可检索：直接走普通对话，避免把全平台知识库暴露给无归属频道
            return new AiAnswer(plainChat(question), null);
        }
        try {
            String raw = restClient.post()
                    .uri("/rag/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyServiceToken)
                    .body(ragBody)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(raw);
            JsonNode sources = node.path("sources");
            if (sources.isArray() && !sources.isEmpty()) {
                return new AiAnswer(node.path("answer").asText(""),
                        limitSourcesJson(sources));
            }
        } catch (Exception e) {
            log.info("RAG 问答不可用，降级为普通对话: {}", e.getMessage());
        }
        return new AiAnswer(plainChat(question), null);
    }

    /** 来源最多保留 20 条；超出说明检索配置异常，截断防止单条消息过大。 */
    private String limitSourcesJson(JsonNode sources) {
        try {
            if (sources.size() <= MAX_SOURCES) {
                return objectMapper.writeValueAsString(sources);
            }
            var trimmed = objectMapper.createArrayNode();
            for (int i = 0; i < MAX_SOURCES; i++) {
                trimmed.add(sources.get(i));
            }
            return objectMapper.writeValueAsString(trimmed);
        } catch (Exception e) {
            log.debug("引用来源序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /** 普通对话兜底（不带知识库上下文） */
    private String plainChat(String question) {
        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyServiceToken)
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

    /** 频道 → 检索范围（项目优先，其次项目所属企业） */
    private record Scope(String projectId, String enterpriseId) {
    }

    private Scope resolveScope(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return new Scope(null, null);
        }
        com.devflow.entity.ChatChannel channel = channelMapper.selectById(channelId);
        if (channel == null || channel.getProjectId() == null) {
            return new Scope(null, null);
        }
        com.devflow.entity.Project project = projectMapper.selectById(channel.getProjectId());
        return new Scope(channel.getProjectId(), project != null ? project.getEnterpriseId() : null);
    }

    private void applyServiceToken(org.springframework.http.HttpHeaders headers) {
        if (serviceToken != null && !serviceToken.isBlank()) {
            headers.set("X-Service-Token", serviceToken);
        }
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
