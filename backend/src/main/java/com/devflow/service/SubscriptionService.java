package com.devflow.service;

import java.util.List;
import java.util.Map;

/**
 * 订阅式通知：关注某个工作项后，它的变更才会推给你。
 *
 * 与"按类型开关"（NotificationPreference）互补：
 * - 类型开关是"我不关心某类事件"；
 * - 订阅是"我只关心我关注的对象"。
 *
 * 默认语义：被指派/被提及/自己创建的自动视为关注（否则用户会漏掉与自己直接相关的事）。
 */
public interface SubscriptionService {

    /** 关注一个对象（幂等） */
    void subscribe(String userId, String targetType, String targetId, String projectId);

    /** 自动关注（被指派 / 被提及 / 创建人），已存在则跳过 */
    void autoSubscribe(String userId, String targetType, String targetId, String projectId);

    /** 取消关注 */
    void unsubscribe(String userId, String targetType, String targetId);

    /** 是否已关注 */
    boolean isSubscribed(String userId, String targetType, String targetId);

    /** 我的关注列表（可按对象类型过滤） */
    List<Map<String, Object>> listMine(String userId, String targetType);

    /** 某对象的关注者 ID 列表（通知分发用） */
    List<String> subscribersOf(String targetType, String targetId);

    /** 是否应把该对象的变更推给该用户（关注者 或 自动关注关系） */
    boolean shouldNotify(String userId, String targetType, String targetId);
}
