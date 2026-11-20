package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.Deployment;
import com.devflow.entity.Project;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.DeploymentMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 移动端收件箱：把"需要我处理的事"聚合成一条流。
 *
 * 移动端不做全功能，只回答三个问题：有什么等我处理、我在做什么、谁在等我确认。
 * 每次查询都先按 ProjectAccessService 收口到可见项目，再逐桶查询，
 * 避免任何一条 SQL 可能返回无权项目的数据。
 */
@Tag(name = "收件箱", description = "跨项目的待处理事务聚合（移动端/桌面共用）")
@RestController
@RequiredArgsConstructor
public class InboxController {

    private static final int BUCKET_LIMIT = 20;

    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final DeploymentMapper deploymentMapper;
    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;
    private final WorkflowService workflowService;
    private final AccessGuard guard;

    @Operation(summary = "我的待处理收件箱")
    @GetMapping("/inbox")
    public R<Map<String, Object>> inbox(Authentication authentication) {
        String userId = guard.userId(authentication);
        List<String> projectIds = projectAccessService.visibleProjectIds(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (projectIds.isEmpty()) {
            result.put("assignments", List.of());
            result.put("approvals", List.of());
            result.put("triage", List.of());
            result.put("total", 0);
            return R.ok(result);
        }

        // 1) 指派给我的未完成任务（按各项目工作流判定"未完成"）
        List<Map<String, Object>> assignments = new ArrayList<>();
        List<Task> myTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getProjectId, projectIds)
                .eq(Task::getAssigneeId, userId)
                .orderByDesc(Task::getUpdatedAt)
                .last("LIMIT " + BUCKET_LIMIT));
        for (Task task : myTasks) {
            var doneCodes = workflowService.statusCodesInCategory(
                    task.getProjectId(), WorkflowService.TYPE_TASK, "DONE");
            if (doneCodes.contains(task.getStatus())) {
                continue;
            }
            assignments.add(taskRow(task));
        }
        result.put("assignments", assignments);

        // 2) 等我审批的部署：项目要求审批，且不是我发起的
        List<Map<String, Object>> approvals = new ArrayList<>();
        List<Deployment> pending = deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                .in(Deployment::getProjectId, projectIds)
                .eq(Deployment::getStatus, "PENDING_APPROVAL")
                .orderByDesc(Deployment::getCreatedAt)
                .last("LIMIT " + BUCKET_LIMIT));
        for (Deployment deployment : pending) {
            if (userId.equals(deployment.getTriggeredBy())) {
                continue; // 自己的部署自己不能审批
            }
            if (!permissionService.hasProjectPermission(userId, deployment.getProjectId(),
                    PermissionCodes.PROJECT_EDIT)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", deployment.getId());
            row.put("type", "DEPLOYMENT");
            row.put("projectId", deployment.getProjectId());
            row.put("title", "部署 " + (deployment.getVersion() == null ? "" : deployment.getVersion()) + " 待审批");
            row.put("createdAt", deployment.getCreatedAt());
            approvals.add(row);
        }
        result.put("approvals", approvals);

        // 3) 我负责项目里的待分诊外部提交（只给有 bug:edit 的人看处理动作）
        List<Map<String, Object>> triage = new ArrayList<>();
        List<Bug> guestBugs = bugMapper.selectList(new LambdaQueryWrapper<Bug>()
                .in(Bug::getProjectId, projectIds)
                .eq(Bug::getPending, 1)
                .orderByDesc(Bug::getCreatedAt)
                .last("LIMIT " + BUCKET_LIMIT));
        for (Bug bug : guestBugs) {
            if (!permissionService.hasProjectPermission(userId, bug.getProjectId(), PermissionCodes.BUG_EDIT)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", bug.getId());
            row.put("type", "TRIAGE");
            row.put("projectId", bug.getProjectId());
            row.put("title", bug.getTitle());
            row.put("submitterName", bug.getGuestNickname());
            row.put("createdAt", bug.getCreatedAt());
            triage.add(row);
        }
        result.put("triage", triage);

        result.put("total", assignments.size() + approvals.size() + triage.size());
        return R.ok(result);
    }

    private Map<String, Object> taskRow(Task task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", task.getId());
        row.put("type", "TASK");
        row.put("projectId", task.getProjectId());
        row.put("title", task.getTitle());
        row.put("status", task.getStatus());
        row.put("priority", task.getPriority());
        row.put("dueDate", task.getDueDate());
        row.put("createdAt", task.getCreatedAt());
        return row;
    }
}
