package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final com.devflow.service.NotificationRecipients notificationRecipients;
    private final com.devflow.service.SubscriptionService subscriptionService;
    private final com.devflow.service.WorkflowService workflowService;
    private final com.devflow.service.ProjectAccessService projectAccessService;

    @Override
    public Page<Bug> listByProject(String projectId, int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Bug> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bug::getProjectId, projectId);
        // 待确认的游客 Bug 只出现在分诊收件箱，不能混进正式列表（否则等于没有闸门）
        wrapper.eq(Bug::getPending, 0);
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
        List<String> visibleProjectIds = projectAccessService.visibleProjectIds(userId);
        if (visibleProjectIds.isEmpty()) {
            return java.util.List.of();
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bug> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(Bug::getAssigneeId, userId);
        // 历史指派不会随成员移除自动清空，读取时必须按当前可见项目收口
        wrapper.in(Bug::getProjectId, visibleProjectIds);
        wrapper.eq(Bug::getPending, 0);
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
        // 调用方已经按项目工作流选定初始状态时不得覆盖；缺省时再取工作流初态
        if (!StringUtils.hasText(bug.getStatus())) {
            bug.setStatus(workflowService.initialStatus(projectId,
                    com.devflow.service.WorkflowService.TYPE_BUG));
        }
        // 显式给初值：@Version 字段为 null 时插入语句会跳过该列，库里落默认值 1，
        // 但返回给客户端的实体仍是 null —— 前端拿不到版本号，首次编辑时
        // expectedVersion 就是 undefined，乐观锁形同失效。
        if (bug.getVersion() == null) {
            bug.setVersion(1);
        }
        bugMapper.insert(bug);

        // 自动关注：报告人与被指派人
        try {
            subscriptionService.autoSubscribe(reporterId, "BUG", bug.getId(), projectId);
            if (StringUtils.hasText(bug.getAssigneeId())) {
                subscriptionService.autoSubscribe(bug.getAssigneeId(), "BUG", bug.getId(), projectId);
            }
        } catch (Exception e) {
            log.debug("Bug 自动关注失败（不影响创建）: {}", e.getMessage());
        }

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

        // 自动化规则：BUG_CREATED —— 先做自动指派（会落库并通知被指派人），再跑规则里的通知
        try {
            automationRuleService.autoAssignBug(projectId, bug, reporterId);
        } catch (Exception e) {
            log.warn("Bug 自动指派失败（不影响创建）: bugId={}, 原因={}", bug.getId(), e.getMessage());
        }
        try {
            automationRuleService.fire("BUG_CREATED", projectId, bug.getId(), bug.getTitle(), reporterId);
        } catch (Exception e) {
            log.warn("BUG_CREATED 自动化规则执行失败: bugId={}, 原因={}", bug.getId(), e.getMessage());
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
    public Bug update(String id, Bug bug, Integer expectedVersion) {
        Bug existing = bugMapper.selectById(id);
        if (existing == null) return null;

        // 乐观锁：显式版本先给出友好提示，数据库版本条件兜底并发竞争
        int current = existing.getVersion() == null ? 1 : existing.getVersion();
        if (expectedVersion != null && expectedVersion != current) {
            throw new com.devflow.common.exception.BusinessException(409,
                    "Bug 已被他人修改（当前版本 v" + current + "），请刷新后再保存");
        }
        bug.setVersion(expectedVersion != null ? expectedVersion : current);
        bug.setId(id);

        String oldAssigneeId = existing.getAssigneeId();

        // assigneeId 传空串表示"取消指派"；未传该字段时保留原负责人。
        // assigneeId 的 updateStrategy=ALWAYS 使 NULL 与其他字段在同一版本化 UPDATE 中落库。
        if ("".equals(bug.getAssigneeId())) {
            bug.setAssigneeId(null);
        } else if (bug.getAssigneeId() == null) {
            bug.setAssigneeId(oldAssigneeId);
        }
        int updatedRows = bugMapper.updateById(bug);
        if (updatedRows == 0) {
            throw new com.devflow.common.exception.BusinessException(409,
                    "Bug 已被他人修改，请刷新后再保存");
        }

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
        // 与任务一致：乐观锁冲突（影响 0 行）时返回 409，而不是静默失败
        int updatedRows = bugMapper.updateById(bug);
        if (updatedRows == 0) {
            throw new com.devflow.common.exception.BusinessException(409,
                    "Bug 状态已被他人修改，请刷新后重试");
        }

        // 状态变更通知对方：负责人改了通知报告人，报告人改了通知负责人
        String notifyUserId = null;
        if (operatorId != null && operatorId.equals(bug.getAssigneeId())) {
            notifyUserId = bug.getReporterId();
        } else if (operatorId != null && operatorId.equals(bug.getReporterId())) {
            notifyUserId = bug.getAssigneeId();
        }
        // 收件人 = 直接关系人（负责人/报告人之一）∪ 关注该 Bug 的人，排除操作者
        List<String> recipients = notificationRecipients.resolveOne(
                "BUG", bug.getId(), operatorId, notifyUserId);
        for (String uid : recipients) {
            notificationService.createNotification(
                uid,
                "BUG_UPDATE",
                "Bug 状态变更",
                "Bug「" + bug.getTitle() + "」状态由 " + oldStatus + " 变更为 " + status,
                "/projects/" + bug.getProjectId() + "?tab=bugs"
            );
        }

        // 自动化规则：BUG_FAIL —— 缺陷被重新打开（REOPENED）表示修复失败/回归，需要通知规则成员
        if ("REOPENED".equals(status) && !"REOPENED".equals(oldStatus)) {
            try {
                automationRuleService.fire("BUG_FAIL", bug.getProjectId(), bug.getId(),
                        bug.getTitle(), operatorId);
            } catch (Exception e) {
                log.warn("BUG_FAIL 自动化规则执行失败: bugId={}, 原因={}", id, e.getMessage());
            }
        }
        return bug;
    }

    @Override
    public void delete(String id, String operatorId) {
        // 带审计的软删除（回收站需要真实删除时间与删除人）
        bugMapper.softDelete(id, operatorId);
    }
}
