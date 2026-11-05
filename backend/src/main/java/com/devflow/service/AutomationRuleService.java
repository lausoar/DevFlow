package com.devflow.service;

import com.devflow.entity.AutomationRule;

import java.util.List;

/**
 * 自动化规则服务（MVP）：事件触发时按规则自动指派/通知。
 */
public interface AutomationRuleService {

    /** 项目的规则列表 */
    List<AutomationRule> listByProject(String projectId);

    /** 创建规则 */
    AutomationRule create(String projectId, AutomationRule rule, String userId);

    /** 更新规则（启用/禁用、改配置） */
    AutomationRule update(String ruleId, AutomationRule updates, String userId);

    /** 删除规则 */
    void delete(String ruleId, String userId);

    /**
     * BUG_CREATED 的自动指派：新 Bug 未指派时指派给第一条启用规则的成员并通知。
     *
     * @return 实际指派的用户 ID（无规则则 null）
     */
    String autoAssignBug(String projectId, com.devflow.entity.Bug bug, String reporterId);

    /**
     * 事件触发入口：按项目+触发器找启用规则，执行自动指派与通知。
     * 异步语义由调用方决定（本方法同步执行但内部兜底异常）。
     *
     * @param triggerType 触发器类型
     * @param projectId   项目
     * @param objectId    事件对象 ID（Bug/任务/用例）
     * @param objectTitle 事件对象标题
     * @param creatorId   事件对象创建人（自动指派时排除）
     * @return 处理的规则数
     */
    int fire(String triggerType, String projectId, String objectId,
             String objectTitle, String creatorId);
}
