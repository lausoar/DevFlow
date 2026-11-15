package com.devflow.service.impl;

import com.devflow.service.NotificationRecipients;
import com.devflow.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收件人计算实现。
 *
 * 订阅查询失败时**只用 explicit 列表**（宁可少推，也不要把通知发给无关的人）——
 * 这与"通知偏好查询失败时默认推送"的方向相反，因为两者风险不同：
 * - 偏好失败 → 少推会让用户漏掉通知，所以 fail-open；
 * - 订阅失败 → 多推会把通知发给不该看到的人，所以 fail-closed。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecipientsImpl implements NotificationRecipients {

    private final SubscriptionService subscriptionService;

    @Override
    public List<String> resolve(String targetType, String targetId, String operatorId, List<String> explicit) {
        Set<String> recipients = new LinkedHashSet<>();
        if (explicit != null) {
            explicit.stream().filter(r -> r != null && !r.isBlank()).forEach(recipients::add);
        }
        if (targetType != null && targetId != null) {
            try {
                subscriptionService.subscribersOf(targetType, targetId).forEach(recipients::add);
            } catch (Exception e) {
                log.debug("订阅查询失败，仅按直接关系人发送: type={}, id={}, 原因={}",
                        targetType, targetId, e.getMessage());
            }
        }
        if (operatorId != null) {
            recipients.remove(operatorId);
        }
        return new ArrayList<>(recipients);
    }
}
