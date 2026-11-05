package com.devflow.service;

import com.devflow.entity.Notification;

/**
 * 通知服务接口
 */
public interface NotificationService {

    /**
     * 创建通知
     */
    void createNotification(String userId, String type, String title, String content, String actionUrl);

    /**
     * 批量通知（同一个通知发给多人）
     */
    void notifyMultiple(java.util.List<String> userIds, String type, String title, String content, String actionUrl);
}
