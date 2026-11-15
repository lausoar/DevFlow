package com.devflow.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知收件人计算：把"谁该收到这条通知"的判定收敛到一处。
 *
 * 为什么需要单独抽出来：原来每个 service 各自拼 notifyUsers 列表，
 * 口径散落且都没有考虑"订阅"——用户关注了某个工作项，却收不到它的变更通知。
 *
 * 合并规则（并集，去重，排除操作者自己）：
 * 1. 显式传入的人（如报告人、被指派人）——业务上的直接关系人，必须收到；
 * 2. 关注该工作项的人（含自动关注）；
 * 3. 排除操作者本人（没人需要收到自己刚做的事的通知）。
 */
public interface NotificationRecipients {

    /**
     * 计算收件人。
     *
     * @param targetType 工作项类型（TASK/BUG/REQUIREMENT）；为空则不考虑订阅
     * @param targetId   工作项 ID
     * @param operatorId 操作者（排除）
     * @param explicit   业务上的直接关系人（可为空）
     */
    List<String> resolve(String targetType, String targetId, String operatorId, List<String> explicit);

    /** 便捷方法：单个直接关系人 */
    default List<String> resolveOne(String targetType, String targetId, String operatorId, String explicit) {
        List<String> list = new ArrayList<>();
        if (explicit != null) {
            list.add(explicit);
        }
        return resolve(targetType, targetId, operatorId, list);
    }
}
