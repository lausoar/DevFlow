package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.dto.response.DashboardResponse;
import com.devflow.entity.Bug;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
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

        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int inProgressTasks = (int) tasks.stream().filter(t -> "DOING".equals(t.getStatus())).count();
        int todoTasks = (int) tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();

        // 统计 Bug
        List<Bug> bugs = bugMapper.selectList(
            new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId)
        );
        int openBugs = (int) bugs.stream()
            .filter(b -> !"RESOLVED".equals(b.getStatus()) && !"CLOSED".equals(b.getStatus()))
            .count();
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

        // 任务分布
        Map<String, Integer> taskDist = new LinkedHashMap<>();
        taskDist.put("TODO", todoTasks);
        taskDist.put("DOING", inProgressTasks);
        taskDist.put("TESTING", (int) tasks.stream().filter(t -> "TESTING".equals(t.getStatus())).count());
        taskDist.put("DONE", completedTasks);

        // Bug 分布
        Map<String, Integer> bugDist = new LinkedHashMap<>();
        bugDist.put("NEW", (int) bugs.stream().filter(b -> "NEW".equals(b.getStatus())).count());
        bugDist.put("ASSIGNED", (int) bugs.stream().filter(b -> "ASSIGNED".equals(b.getStatus())).count());
        bugDist.put("DOING", (int) bugs.stream().filter(b -> "DOING".equals(b.getStatus())).count());
        bugDist.put("RESOLVED", (int) bugs.stream().filter(b -> "RESOLVED".equals(b.getStatus())).count());
        bugDist.put("CLOSED", (int) bugs.stream().filter(b -> "CLOSED".equals(b.getStatus())).count());

        // 构建 Sprint 进度
        DashboardResponse.SprintProgress sprintProgress = null;
        if (activeSprint != null) {
            int sprintTasks = (int) tasks.stream()
                .filter(t -> activeSprint.getId().equals(t.getSprintId()))
                .count();
            int sprintCompleted = (int) tasks.stream()
                .filter(t -> activeSprint.getId().equals(t.getSprintId()) && "DONE".equals(t.getStatus()))
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
