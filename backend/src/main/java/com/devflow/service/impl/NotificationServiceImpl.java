package com.devflow.service.impl;

import com.devflow.entity.Notification;
import com.devflow.mapper.NotificationMapper;
import com.devflow.service.NotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现
 * 负责在业务事件发生时自动创建通知记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void createNotification(String userId, String type, String title, String content, String actionUrl) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setActionUrl(actionUrl);
            notification.setIsRead(0);
            notificationMapper.insert(notification);
            log.info("创建通知: userId={}, type={}, title={}", userId, type, title);

            // 通过 WebSocket 实时推送给接收用户（/user/{userId}/queue/notifications）
            try {
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", notification);
            } catch (Exception e) {
                log.warn("通知实时推送失败: userId={}, type={}", userId, type, e);
            }
        } catch (Exception e) {
            // 通知创建失败不应影响主业务流程，仅记录日志
            log.error("创建通知失败: userId={}, type={}, title={}", userId, type, title, e);
        }
    }

    @Override
    public void notifyMultiple(List<String> userIds, String type, String title, String content, String actionUrl) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        // 去重
        userIds.stream().distinct().forEach(uid ->
            createNotification(uid, type, title, content, actionUrl)
        );
    }
}
