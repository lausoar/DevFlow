package com.devflow.service;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态服务（连接感知）
 * 用户建立 WebSocket 会话时记录，会话断开（含心跳超时/浏览器关闭）时移除。
 * 内存存储，单实例部署；多实例部署需改用 Redis 共享。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    /** userId -> 该用户的活跃 WebSocket 会话ID集合（支持同一用户多端在线） */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    /** sessionId -> userId（断开时反查） */
    private final Map<String, String> sessionUser = new ConcurrentHashMap<>();

    /** 延迟获取消息模板，避免与 WebSocket 配置类形成 bean 循环依赖 */
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final UserMapper userMapper;

    /** 记录一次会话连接 */
    public void connect(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        sessionUser.put(sessionId, userId);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /** 会话断开：若该用户的最后一个会话已关闭则返回其 userId（转为离线） */
    public Optional<String> disconnect(String sessionId) {
        String userId = sessionUser.remove(sessionId);
        if (userId == null) {
            return Optional.empty();
        }
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                return Optional.of(userId);
            }
        }
        return Optional.empty();
    }

    /** 当前全部在线用户ID */
    public Set<String> onlineUserIds() {
        return Set.copyOf(userSessions.keySet());
    }

    /**
     * 在线名单变化后按企业广播（/topic/presence.{enterpriseId}）：
     * 在线状态属于组织内部信息，不能跨租户广播全量名单。
     */
    public void broadcast() {
        SimpMessagingTemplate template = messagingTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        Map<String, java.util.List<String>> byEnterprise = new java.util.HashMap<>();
        for (String userId : onlineUserIds()) {
            User user = userMapper.selectById(userId);
            if (user == null || user.getEnterpriseId() == null) {
                continue;
            }
            byEnterprise.computeIfAbsent(user.getEnterpriseId(), k -> new java.util.ArrayList<>()).add(userId);
        }
        byEnterprise.forEach((enterpriseId, ids) -> {
            try {
                template.convertAndSend("/topic/presence." + enterpriseId,
                        Map.of("onlineUserIds", ids));
            } catch (Exception e) {
                log.warn("在线状态广播失败: enterprise={}, 原因={}", enterpriseId, e.getMessage());
            }
        });
    }
}
