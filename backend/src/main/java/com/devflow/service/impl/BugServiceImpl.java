package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.Bug;
import com.devflow.entity.User;
import com.devflow.mapper.BugMapper;
import com.devflow.service.BugService;
import com.devflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bug 服务实现
 * 在 Bug 创建和状态变更时自动生成通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BugServiceImpl implements BugService {

    private final BugMapper bugMapper;
    private final NotificationService notificationService;
    private final com.devflow.mapper.ProjectMapper projectMapper;
    private final com.devflow.service.ImPushService imPushService;
    private final com.devflow.service.AutomationRuleService automationRuleService;
    private final com.devflow.mapper.UserMapper userMapper;

    @Override
    public Page<Bug> listByProject(String projectId, int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Bug> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bug::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Bug::getTitle, keyword)
                    .or().like(Bug::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Bug::getStatus, status);
        }
        wrapper.orderByDesc(Bug::getCreatedAt);
        return bugMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public java.util.List<Bug> listMyBugs(String userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bug> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(Bug::getAssigneeId, userId);
        wrapper.orderByAsc(Bug::getPriority).orderByDesc(Bug::getCreatedAt);
        wrapper.last("LIMIT 200");
        return bugMapper.selectList(wrapper);
    }

    @Override
    public Bug getById(String id) {
        return bugMapper.selectById(id);
    }

    @Override
    public Bug create(String projectId, Bug bug, String reporterId) {
        bug.setProjectId(projectId);
        bug.setReporterId(reporterId);
        // 状态枚举与 schema/前端保持一致: NEW/ASSIGNED/DOING/RESOLVED/REOPENED/CLOSED
        // 创建时已指派直接进入"已分配"
        bug.setStatus(StringUtils.hasText(bug.getAssigneeId()) ? "ASSIGNED" : "NEW");
        bugMapper.insert(bug);

        // 通知被指派的人
        if (StringUtils.hasText(bug.getAssigneeId()) && !bug.getAssigneeId().equals(reporterId)) {
            notificationService.createNotification(
                bug.getAssigneeId(),
                "BUG_UPDATE",
                "新 Bug 指派",
                "你被指派了一个新 Bug: " + bug.getTitle(),
                "/projects/" + bug.getProjectId() + "?tab=bugs"
            );
        }

        // 自动化规则：BUG_CREATED 触发（未指派时自动指派给规则成员+通知）
        try {
            automationRuleService.autoAssignBug(projectId, bug, reporterId);
        } catch (Exception ignored) {
        }

        // 自动化规则：BUG_CREATED 触发（自动指派+通知）
        try {
            automationRuleService.fire("BUG_CREATED", projectId, bug.getId(), bug.getTitle(), reporterId);
        } catch (Exception ignored) {
        }

        // 企业微信群机器人：新缺陷出站推送（异步，失败静默）
        try {
            com.devflow.entity.Project project = projectMapper.selectById(projectId);
            if (project != null && project.getEnterpriseId() != null) {
                String reporterName = resolveDisplayName(reporterId);
                String priorityLabel = "HIGH".equals(bug.getPriority()) ? "高" : "MEDIUM".equals(bug.getPriority()) ? "中" : "低";
                String severityLabel = "BLOCKER".equals(bug.getSeverity()) ? "阻塞"
                        : "CRITICAL".equals(bug.getSeverity()) ? "严重"
                        : "MAJOR".equals(bug.getSeverity()) ? "重要"
                        : "MINOR".equals(bug.getSeverity()) ? "一般" : "轻微";
                StringBuilder md = new StringBuilder();
                md.append("**🐞 新缺陷** <font color=\"warning\">[").append(severityLabel).append("]</font>\n");
                md.append("> **").append(bug.getTitle()).append("**\n");
                md.append("> 项目：").append(project.getName())
                  .append(" | 优先级：").append(priorityLabel)
                  .append(" | 提交人：").append(reporterName).append("\n");
                md.append("> [查看详情](/projects/").append(projectId).append("?tab=bugs)");
                imPushService.pushMarkdown(project.getEnterpriseId(), md.toString());
            }
        } catch (Exception ignored) {
        }

        return bug;
    }

    /** 用户展示名：真实姓名优先，用户名兜底 */
    private String resolveDisplayName(String userId) {
        User u = userMapper.selectById(userId);
        return u != null ? u.displayName() : "未知用户";
    }

    @Override
    public Bug update(String id, Bug bug) {
        Bug existing = bugMapper.selectById(id);
        if (existing == null) return null;

        String oldAssigneeId = existing.getAssigneeId();

        // assigneeId 传空串表示"取消指派"：updateById 默认跳过 null 字段，需显式置 NULL
        if ("".equals(bug.getAssigneeId())) {
            bug.setAssigneeId(null);
            bugMapper.update(null, new LambdaUpdateWrapper<Bug>()
                    .eq(Bug::getId, id)
                    .set(Bug::getAssigneeId, null));
        }
        bugMapper.updateById(bug);

        Bug updated = bugMapper.selectById(id);

        // 如果被指派人变更，通知新的被指派人
        if (updated != null && StringUtils.hasText(updated.getAssigneeId())
                && !updated.getAssigneeId().equals(oldAssigneeId)) {
            notificationService.createNotification(
                updated.getAssigneeId(),
                "BUG_UPDATE",
                "Bug 指派变更",
                "你被指派了 Bug: " + updated.getTitle(),
                "/projects/" + updated.getProjectId() + "?tab=bugs"
            );
        }

        return updated;
    }

    @Override
    public Bug changeStatus(String id, String status, String operatorId) {
        Bug bug = bugMapper.selectById(id);
        if (bug == null) return null;

        String oldStatus = bug.getStatus();
        bug.setStatus(status);
        bugMapper.updateById(bug);

        // 状态变更通知对方：负责人改了通知报告人，报告人改了通知负责人
        String notifyUserId = null;
        if (operatorId != null && operatorId.equals(bug.getAssigneeId())) {
            notifyUserId = bug.getReporterId();
        } else if (operatorId != null && operatorId.equals(bug.getReporterId())) {
            notifyUserId = bug.getAssigneeId();
        }
        if (StringUtils.hasText(notifyUserId) && !notifyUserId.equals(operatorId)) {
            notificationService.createNotification(
                notifyUserId,
                "BUG_UPDATE",
                "Bug 状态变更",
                "Bug「" + bug.getTitle() + "」状态由 " + oldStatus + " 变更为 " + status,
                "/projects/" + bug.getProjectId() + "?tab=bugs"
            );
        }
        return bug;
    }

    @Override
    public void delete(String id) {
        bugMapper.deleteById(id);
    }
}
