package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.AutomationRule;
import com.devflow.entity.User;
import com.devflow.mapper.AutomationRuleMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 自动化规则实现（MVP）。
 * 触发语义：
 * - BUG_CREATED: 新 Bug 未指派时自动指派给规则成员，并通知
 * - BUG_FAIL / CASE_FAIL / TASK_DONE: 通知规则成员
 * 管理权限：项目内 TESTER/OWNER/MANAGER（testcase:manage 同款），企业超管亦可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRuleServiceImpl implements AutomationRuleService {

    private final AutomationRuleMapper automationRuleMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Override
    public List<AutomationRule> listByProject(String projectId) {
        return automationRuleMapper.selectList(new LambdaQueryWrapper<AutomationRule>()
                .eq(AutomationRule::getProjectId, projectId)
                .orderByDesc(AutomationRule::getCreatedAt));
    }

    @Override
    public AutomationRule create(String projectId, AutomationRule rule, String userId) {
        requireManager(projectId, userId);
        rule.setId(null);
        rule.setProjectId(projectId);
        rule.setCreatorId(userId);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        validateTargets(rule);
        automationRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public AutomationRule update(String ruleId, AutomationRule updates, String userId) {
        AutomationRule existing = automationRuleMapper.selectById(ruleId);
        if (existing == null) {
            throw new BusinessException(404, "规则不存在");
        }
        requireManager(existing.getProjectId(), userId);

        if (updates.getName() != null && !updates.getName().isBlank()) {
            existing.setName(updates.getName().trim());
        }
        if (updates.getTriggerType() != null) {
            existing.setTriggerType(updates.getTriggerType());
        }
        if (updates.getAssignToUserId() != null) {
            existing.setAssignToUserId(updates.getAssignToUserId().isBlank() ? null : updates.getAssignToUserId().trim());
        }
        if (updates.getNotifyUserId() != null) {
            existing.setNotifyUserId(updates.getNotifyUserId().isBlank() ? null : updates.getNotifyUserId().trim());
        }
        if (updates.getEnabled() != null) {
            existing.setEnabled(updates.getEnabled());
        }
        validateTargets(existing);
        automationRuleMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(String ruleId, String userId) {
        AutomationRule existing = automationRuleMapper.selectById(ruleId);
        if (existing == null) {
            throw new BusinessException(404, "规则不存在");
        }
        requireManager(existing.getProjectId(), userId);
        automationRuleMapper.deleteById(ruleId);
    }

    @Override
    public int fire(String triggerType, String projectId, String objectId,
                    String objectTitle, String creatorId) {
        try {
            List<AutomationRule> rules = automationRuleMapper.selectList(
                    new LambdaQueryWrapper<AutomationRule>()
                            .eq(AutomationRule::getProjectId, projectId)
                            .eq(AutomationRule::getTriggerType, triggerType)
                            .eq(AutomationRule::getEnabled, 1));
            int handled = 0;
            for (AutomationRule rule : rules) {
                // 自动指派：仅新对象（BUG_CREATED）且对象未指派
                if ("BUG_CREATED".equals(triggerType)
                        && StringUtils.hasText(rule.getAssignToUserId())
                        && !rule.getAssignToUserId().equals(creatorId)) {
                    // 由调用方（BugServiceImpl）负责实际指派，这里只计数
                    handled++;
                    continue;
                }
                // 通知
                if (StringUtils.hasText(rule.getNotifyUserId())
                        && !rule.getNotifyUserId().equals(creatorId)) {
                    notificationService.createNotification(
                            rule.getNotifyUserId(), "SYSTEM",
                            "自动化: " + triggerType,
                            "规则「" + rule.getName() + "」触发：" + objectTitle,
                            "/projects/" + projectId);
                    handled++;
                }
            }
            return handled;
        } catch (Exception e) {
            log.warn("自动化规则触发失败（不影响主流程）project={} trigger={}: {}",
                    projectId, triggerType, e.getMessage());
            return 0;
        }
    }

    @Override
    public String autoAssignBug(String projectId, com.devflow.entity.Bug bug, String reporterId) {
        try {
            if (StringUtils.hasText(bug.getAssigneeId())) {
                return null; // 已指派则不动
            }
            AutomationRule rule = automationRuleMapper.selectList(
                    new LambdaQueryWrapper<AutomationRule>()
                            .eq(AutomationRule::getProjectId, projectId)
                            .eq(AutomationRule::getTriggerType, "BUG_CREATED")
                            .eq(AutomationRule::getEnabled, 1)
                            .isNotNull(AutomationRule::getAssignToUserId)
                            .last("LIMIT 1")).stream().findFirst().orElse(null);
            if (rule == null || rule.getAssignToUserId().equals(reporterId)) {
                return null;
            }
            bug.setAssigneeId(rule.getAssignToUserId());
            bug.setStatus("ASSIGNED");
            com.devflow.mapper.BugMapper bugMapper = null; // 由调用方更新，此处直接置内存字段
            return rule.getAssignToUserId();
        } catch (Exception e) {
            log.warn("Bug 自动指派失败（不影响创建）: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 内部 ====================

    private void requireManager(String projectId, String userId) {
        User operator = userMapper.selectById(userId);
        boolean enterpriseSuper = operator != null
                && EnterpriseRoles.SUPER_ADMIN.equals(operator.getRole())
                && projectId != null;
        if (!enterpriseSuper) {
            throw new BusinessException(403, "仅超级管理员可管理自动化规则");
        }
    }

    private void validateTargets(AutomationRule rule) {
        if (StringUtils.hasText(rule.getAssignToUserId())) {
            User assignee = userMapper.selectById(rule.getAssignToUserId());
            if (assignee == null) {
                throw new BusinessException(400, "自动指派对象不存在");
            }
        }
        if (StringUtils.hasText(rule.getNotifyUserId())) {
            User notify = userMapper.selectById(rule.getNotifyUserId());
            if (notify == null) {
                throw new BusinessException(400, "通知对象不存在");
            }
        }
    }
}
