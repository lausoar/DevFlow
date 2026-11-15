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
    private final com.devflow.mapper.NotificationPreferenceMapper preferenceMapper;
    private final com.devflow.mapper.UserMapper userMapper;

    @Override
    public void createNotification(String userId, String type, String title, String content, String actionUrl) {
        try {
            // 尊重用户偏好：该类型被关闭时仍然入库（可回溯），但不实时推送。
            // 免打扰时段内同样只入库不推送 —— 用户回到工作站后能在通知中心看到。
            boolean push = shouldPush(userId, type);

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
            if (push) {
                try {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", notification);
                } catch (Exception e) {
                    log.warn("通知实时推送失败: userId={}, type={}", userId, type, e);
                }
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

    /**
     * 是否实时推送：类型被关掉 → 不推；在免打扰时段 → 不推。
     * 查询失败时默认推送（宁可多推一次，也不要静默丢通知）。
     */
    private boolean shouldPush(String userId, String type) {
        try {
            var pref = preferenceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.devflow.entity.NotificationPreference>()
                            .eq(com.devflow.entity.NotificationPreference::getUserId, userId)
                            .eq(com.devflow.entity.NotificationPreference::getType, type));
            if (pref != null && !Integer.valueOf(1).equals(pref.getEnabled())) {
                return false;
            }
            var user = userMapper.selectById(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getDndEnabled())) {
                return true;
            }
            var start = user.getDndStart();
            var end = user.getDndEnd();
            if (start == null || end == null) {
                return true;
            }
            var now = java.time.LocalTime.now();
            // start > end 表示跨天（例如 22:00-08:00）
            boolean inWindow = start.isBefore(end)
                    ? (!now.isBefore(start) && now.isBefore(end))
                    : (!now.isBefore(start) || now.isBefore(end));
            return !inWindow;
        } catch (Exception e) {
            log.debug("通知偏好查询失败，按默认推送: userId={}, 原因={}", userId, e.getMessage());
            return true;
        }
    }
}
