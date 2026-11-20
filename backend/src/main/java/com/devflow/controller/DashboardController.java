package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import com.devflow.dto.response.DashboardResponse;
import com.devflow.entity.Bug;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.entity.WorkflowStatus;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 项目 Dashboard 控制器
 */
@Tag(name = "项目Dashboard", description = "项目统计、概览")
@RestController
@RequestMapping("/projects/{projectId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TaskMapper taskMapper;
    private final SprintMapper sprintMapper;
    private final BugMapper bugMapper;
    private final ProjectAccessService projectAccessService;
    private final WorkflowService workflowService;

    @Operation(summary = "获取项目 Dashboard 数据")
    @GetMapping
    public R<DashboardResponse> getDashboard(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        // 统计任务
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
        );

        // 按工作流分类聚合（自定义状态也正确计入）
        java.util.Set<String> doneTaskCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_TASK, WorkflowStatus.CATEGORY_DONE));
        java.util.Set<String> progTaskCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_TASK, WorkflowStatus.CATEGORY_IN_PROGRESS));
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> doneTaskCodes.contains(t.getStatus())).count();
        int inProgressTasks = (int) tasks.stream().filter(t -> progTaskCodes.contains(t.getStatus())).count();
        int todoTasks = totalTasks - completedTasks - inProgressTasks;

        // 统计 Bug
        List<Bug> bugs = bugMapper.selectList(
            new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId).eq(Bug::getPending, 0)
        );
        java.util.Set<String> resolvedBugCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_BUG, WorkflowStatus.CATEGORY_DONE));
        resolvedBugCodes.add("RESOLVED");
        int openBugs = (int) bugs.stream().filter(b -> !resolvedBugCodes.contains(b.getStatus())).count();
        int criticalBugs = (int) bugs.stream()
            .filter(b -> "BLOCKER".equals(b.getSeverity()) || "CRITICAL".equals(b.getSeverity()))
            .count();

        // 当前 Sprint
        Sprint activeSprint = sprintMapper.selectOne(
            new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .eq(Sprint::getStatus, "ACTIVE")
                .last("LIMIT 1")
        );

        // 任务分布：按项目工作流的实际状态码统计（前端按 label 渲染）
        Map<String, Integer> taskDist = new LinkedHashMap<>();
        com.devflow.entity.Workflow taskWorkflow = workflowService.resolve(projectId, WorkflowService.TYPE_TASK);
        if (taskWorkflow != null) {
            for (WorkflowStatus st : workflowService.statuses(taskWorkflow.getId())) {
                taskDist.put(st.getCode(), (int) tasks.stream()
                        .filter(t -> st.getCode().equals(t.getStatus())).count());
            }
        }
        tasks.stream()
                .filter(t -> t.getStatus() != null && !taskDist.containsKey(t.getStatus()))
                .forEach(t -> taskDist.merge(t.getStatus(), 1, Integer::sum));

        // Bug 分布：同上，按缺陷工作流状态码
        Map<String, Integer> bugDist = new LinkedHashMap<>();
        com.devflow.entity.Workflow bugWorkflow = workflowService.resolve(projectId, WorkflowService.TYPE_BUG);
        if (bugWorkflow != null) {
            for (WorkflowStatus st : workflowService.statuses(bugWorkflow.getId())) {
                bugDist.put(st.getCode(), (int) bugs.stream()
                        .filter(b -> st.getCode().equals(b.getStatus())).count());
            }
        }
        bugs.stream()
                .filter(b -> b.getStatus() != null && !bugDist.containsKey(b.getStatus()))
                .forEach(b -> bugDist.merge(b.getStatus(), 1, Integer::sum));

        // 构建 Sprint 进度
        DashboardResponse.SprintProgress sprintProgress = null;
        if (activeSprint != null) {
            int sprintTasks = (int) tasks.stream()
                .filter(t -> activeSprint.getId().equals(t.getSprintId()))
                .count();
            int sprintCompleted = (int) tasks.stream()
                .filter(t -> activeSprint.getId().equals(t.getSprintId()) && doneTaskCodes.contains(t.getStatus()))
                .count();

            sprintProgress = DashboardResponse.SprintProgress.builder()
                .id(activeSprint.getId())
                .name(activeSprint.getName())
                .status(activeSprint.getStatus())
                .totalTasks(sprintTasks)
                .completedTasks(sprintCompleted)
                .progress(sprintTasks > 0 ? (double) sprintCompleted / sprintTasks * 100 : 0)
                .build();
        }

        DashboardResponse response = DashboardResponse.builder()
            .stats(DashboardResponse.StatsOverview.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .todoTasks(todoTasks)
                .openBugs(openBugs)
                .criticalBugs(criticalBugs)
                .completionRate(totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0)
                .build())
            .sprint(sprintProgress)
            .taskDistribution(taskDist)
            .bugDistribution(bugDist)
            .build();

        return R.ok(response);
    }
}
