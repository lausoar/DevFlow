package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.dto.response.*;
import com.devflow.entity.*;
import com.devflow.mapper.*;
import com.devflow.service.StatisticsService;
import com.devflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计报表服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final SprintMapper sprintMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final com.devflow.service.WorkflowService workflowService;
    private final StatisticsAggregateMapper statisticsAggregateMapper;
    private final TaskStatusEventMapper taskStatusEventMapper;


    // ============================================
    // 聚合查询的辅助方法
    // ============================================

    private Set<String> doneCodesOf(String projectId) {
        return new HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_TASK, WorkflowStatus.CATEGORY_DONE));
    }

    private Set<String> progCodesOf(String projectId) {
        return new HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_TASK, WorkflowStatus.CATEGORY_IN_PROGRESS));
    }

    private Set<String> doneRequirementCodesOf(String projectId) {
        return new HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_REQUIREMENT, WorkflowStatus.CATEGORY_DONE));
    }

    /**
     * 缺陷的"已解决"口径。
     *
     * 默认工作流里 RESOLVED 属于 IN_PROGRESS 分类（还要回归），所以不能直接用
     * DONE 分类；但 RESOLVED/CLOSED 一定是"已解决/已关闭"。两者取并集。
     */
    private Set<String> resolvedBugCodesOf(String projectId) {
        Set<String> codes = new HashSet<>(workflowService.statusCodesInCategory(
                projectId, WorkflowService.TYPE_BUG, WorkflowStatus.CATEGORY_DONE));
        codes.add("RESOLVED");
        codes.add("CLOSED");
        return codes;
    }

    private Map<String, Integer> toIntMap(List<Map<String, Object>> rows, String keyField) {
        Map<String, Integer> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(asString(row.get(keyField)), asInt(row.get("n")));
        }
        return out;
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private int asInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    // ============================================
    // 企业级统计
    // ============================================
    @Override
    public EnterpriseStatsResponse getEnterpriseStats(String enterpriseId) {
        List<Project> projects = projectMapper.selectList(
            new LambdaQueryWrapper<Project>()
                .eq(Project::getEnterpriseId, enterpriseId)
        );

        List<String> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            return EnterpriseStatsResponse.builder()
                .projectOverview(EnterpriseStatsResponse.ProjectOverview.builder().build())
                .taskOverview(EnterpriseStatsResponse.TaskOverview.builder().build())
                .bugOverview(EnterpriseStatsResponse.BugOverview.builder().build())
                .requirementOverview(EnterpriseStatsResponse.RequirementOverview.builder().build())
                .projectProgressList(Collections.emptyList())
                .taskTrend(Collections.emptyList())
                .taskStatusDistribution(Collections.emptyMap())
                .bugSeverityDistribution(Collections.emptyMap())
                .requirementStatusDistribution(Collections.emptyMap())
                .build();
        }

        // 所有计数都下推给数据库（GROUP BY），返回行数上限是「项目数 x 状态数」。
        // 原实现把企业全部任务/Bug/需求加载进内存再逐条过滤，万级任务时是数十 MB 堆内存，
        // 且前端每次刷新都会重算一遍。
        List<Map<String, Object>> taskRows =
                statisticsAggregateMapper.taskCountByProjectAndStatus(projectIds);
        List<Map<String, Object>> bugRows =
                statisticsAggregateMapper.bugCountByProjectSeverityStatus(projectIds);
        List<Map<String, Object>> reqRows =
                statisticsAggregateMapper.requirementCountByProjectAndStatus(projectIds);
        Map<String, Integer> memberCountByProject =
                toIntMap(statisticsAggregateMapper.memberCountByProject(projectIds), "projectId");

        // ---- 项目总览 ----
        int activeProjects = (int) projects.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count();
        int planningProjects = (int) projects.stream().filter(p -> "PLANNING".equals(p.getStatus())).count();
        int archivedProjects = (int) projects.stream().filter(p -> "ARCHIVED".equals(p.getStatus())).count();
        Integer distinctMembers = statisticsAggregateMapper.distinctMemberCount(projectIds);

        // ---- 任务总览 ----
        // 每个项目有自己的工作流，所以"完成/进行中"要按项目自己的状态分类判定。
        // 聚合行只给 (project, status, count)，归类在 Java 侧做 —— 见 mapper 上的说明。
        int totalTasks = 0, completedTasks = 0, inProgressTasks = 0, todoTasks = 0, overdueTasks = 0;
        Map<String, Integer> taskStatusDist = new LinkedHashMap<>();
        Map<String, Map<String, int[]>> perProjectTask = new HashMap<>();

        for (Map<String, Object> row : taskRows) {
            String pid = asString(row.get("projectId"));
            String status = asString(row.get("status"));
            int n = asInt(row.get("n"));
            int overdue = asInt(row.get("overdue"));

            totalTasks += n;
            if (status != null) {
                taskStatusDist.merge(status, n, Integer::sum);
            }
            perProjectTask.computeIfAbsent(pid, k -> new HashMap<>()).put(status, new int[]{n, overdue});

            Set<String> doneCodes = doneCodesOf(pid);
            Set<String> progCodes = progCodesOf(pid);
            if (doneCodes.contains(status)) {
                completedTasks += n;
            } else if (progCodes.contains(status)) {
                inProgressTasks += n;
            } else {
                todoTasks += n;
            }
            if (!doneCodes.contains(status)) {
                overdueTasks += overdue;
            }
        }

        // ---- Bug 总览 ----
        int totalBugs = 0, openBugs = 0, resolvedBugs = 0, criticalBugs = 0;
        Map<String, Integer> bugSeverityDist = new LinkedHashMap<>();
        bugSeverityDist.put("BLOCKER", 0);
        bugSeverityDist.put("CRITICAL", 0);
        bugSeverityDist.put("MAJOR", 0);
        bugSeverityDist.put("MINOR", 0);
        bugSeverityDist.put("TRIVIAL", 0);
        Map<String, Map<String, Integer>> perProjectBugs = new HashMap<>();

        for (Map<String, Object> row : bugRows) {
            String pid = asString(row.get("projectId"));
            String severity = asString(row.get("severity"));
            String status = asString(row.get("status"));
            int n = asInt(row.get("n"));

            totalBugs += n;
            // 缺陷的"已解决"口径：RESOLVED/CLOSED，加上项目工作流里属于 DONE 分类的状态
            Set<String> resolvedCodes = resolvedBugCodesOf(pid);
            if (resolvedCodes.contains(status)) {
                resolvedBugs += n;
            } else {
                openBugs += n;
            }
            if ("BLOCKER".equals(severity) || "CRITICAL".equals(severity)) {
                criticalBugs += n;
            }
            if (severity != null) {
                bugSeverityDist.merge(severity, n, Integer::sum);
            }
            perProjectBugs.computeIfAbsent(pid, k -> new HashMap<>()).merge(status, n, Integer::sum);
        }

        // ---- 需求总览 ----
        int totalRequirements = 0, openRequirements = 0, completedRequirements = 0, closedRequirements = 0;
        Map<String, Integer> reqStatusDist = new LinkedHashMap<>();
        for (Map<String, Object> row : reqRows) {
            String pid = asString(row.get("projectId"));
            int n = asInt(row.get("n"));
            String status = asString(row.get("status"));
            totalRequirements += n;
            if (doneRequirementCodesOf(pid).contains(status)) {
                completedRequirements += n;
            } else {
                openRequirements += n;
            }
            if ("CLOSED".equals(status)) closedRequirements += n;
            if (status != null) {
                reqStatusDist.merge(status, n, Integer::sum);
            }
        }

        // ---- 各项目进度 ----
        List<EnterpriseStatsResponse.ProjectProgress> projectProgressList = projects.stream()
            .map(project -> {
                String pid = project.getId();
                Map<String, int[]> statusCounts = perProjectTask.getOrDefault(pid, Map.of());
                Map<String, Integer> bugCounts = perProjectBugs.getOrDefault(pid, Map.of());

                int pTotal = statusCounts.values().stream().mapToInt(v -> v[0]).sum();
                Set<String> pDoneCodes = doneCodesOf(pid);
                int pCompleted = statusCounts.entrySet().stream()
                        .filter(e -> pDoneCodes.contains(e.getKey()))
                        .mapToInt(e -> e.getValue()[0]).sum();

                Set<String> pResolvedBugCodes = resolvedBugCodesOf(pid);
                int pOpenBugs = bugCounts.entrySet().stream()
                        .filter(e -> !pResolvedBugCodes.contains(e.getKey()))
                        .mapToInt(Map.Entry::getValue).sum();

                return EnterpriseStatsResponse.ProjectProgress.builder()
                    .projectId(pid)
                    .projectName(project.getName())
                    .projectKey(project.getProjectKey())
                    .status(project.getStatus())
                    .totalTasks(pTotal)
                    .completedTasks(pCompleted)
                    .totalBugs(bugCounts.values().stream().mapToInt(Integer::intValue).sum())
                    .openBugs(pOpenBugs)
                    .progress(pTotal > 0 ? (double) pCompleted / pTotal * 100 : 0)
                    .memberCount(memberCountByProject.getOrDefault(pid, 0))
                    .build();
            })
            .collect(Collectors.toList());

        // ---- 任务趋势（最近 14 天，按进入 DONE 的事件时间归日） ----
        List<EnterpriseStatsResponse.TrendPoint> taskTrend = buildTaskTrend(projectIds, 14);

        return EnterpriseStatsResponse.builder()
            .projectOverview(EnterpriseStatsResponse.ProjectOverview.builder()
                .totalProjects(projects.size())
                .activeProjects(activeProjects)
                .planningProjects(planningProjects)
                .archivedProjects(archivedProjects)
                .totalMembers(distinctMembers == null ? 0 : distinctMembers)
                .build())
            .taskOverview(EnterpriseStatsResponse.TaskOverview.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .todoTasks(todoTasks)
                .overdueTasks(overdueTasks)
                .completionRate(totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0)
                .overdueRate(totalTasks > 0 ? (double) overdueTasks / totalTasks * 100 : 0)
                .build())
            .bugOverview(EnterpriseStatsResponse.BugOverview.builder()
                .totalBugs(totalBugs)
                .openBugs(openBugs)
                .resolvedBugs(resolvedBugs)
                .criticalBugs(criticalBugs)
                .resolutionRate(totalBugs > 0 ? (double) resolvedBugs / totalBugs * 100 : 0)
                .build())
            .requirementOverview(EnterpriseStatsResponse.RequirementOverview.builder()
                .totalRequirements(totalRequirements)
                .openRequirements(openRequirements)
                .completedRequirements(completedRequirements)
                .closedRequirements(closedRequirements)
                .completionRate(totalRequirements > 0 ? (double) completedRequirements / totalRequirements * 100 : 0)
                .build())
            .projectProgressList(projectProgressList)
            .taskTrend(taskTrend)
            .taskStatusDistribution(taskStatusDist)
            .bugSeverityDistribution(bugSeverityDist)
            .requirementStatusDistribution(reqStatusDist)
            .build();
    }

    // ============================================
    // 项目健康度
    // ============================================
    @Override
    public ProjectHealthResponse getProjectHealth(String projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
        );
        List<Bug> bugs = bugMapper.selectList(
            new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId).eq(Bug::getPending, 0)
        );
        List<Requirement> requirements = requirementMapper.selectList(
            new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
        );
        List<Sprint> sprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId)
        );

        // 计算各维度指标
        int totalTasks = tasks.size();
        java.util.Set<String> doneTaskCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, com.devflow.service.WorkflowService.TYPE_TASK,
                com.devflow.entity.WorkflowStatus.CATEGORY_DONE));
        int completedTasks = (int) tasks.stream().filter(t -> doneTaskCodes.contains(t.getStatus())).count();
        double taskCompletionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        int totalBugs = bugs.size();
        java.util.Set<String> doneBugCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, com.devflow.service.WorkflowService.TYPE_BUG,
                com.devflow.entity.WorkflowStatus.CATEGORY_DONE));
        // 缺陷"已解决"在默认流程里属于 IN_PROGRESS 分类（还需回归），单独取 RESOLVED/CLOSED 语义
        java.util.Set<String> resolvedBugCodes = new java.util.HashSet<>(doneBugCodes);
        resolvedBugCodes.add("RESOLVED");
        int resolvedBugs = (int) bugs.stream().filter(b -> resolvedBugCodes.contains(b.getStatus())).count();
        double bugResolutionRate = totalBugs > 0 ? (double) resolvedBugs / totalBugs * 100 : 0;

        int totalRequirements = requirements.size();
        java.util.Set<String> doneReqCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, com.devflow.service.WorkflowService.TYPE_REQUIREMENT,
                com.devflow.entity.WorkflowStatus.CATEGORY_DONE));
        int completedRequirements = (int) requirements.stream()
                .filter(r -> doneReqCodes.contains(r.getStatus())).count();
        double requirementCompletionRate = totalRequirements > 0 ? (double) completedRequirements / totalRequirements * 100 : 0;

        int overdueTasks = (int) tasks.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now())
                && !doneTaskCodes.contains(t.getStatus()))
            .count();
        double overdueRate = totalTasks > 0 ? (double) overdueTasks / totalTasks * 100 : 0;

        // 计算 Sprint 速率
        List<Sprint> completedSprints = sprints.stream()
            .filter(s -> "COMPLETED".equals(s.getStatus()))
            .collect(Collectors.toList());
        double sprintVelocity = 0;
        if (!completedSprints.isEmpty()) {
            double totalPoints = completedSprints.stream().mapToDouble(s -> {
                return tasks.stream()
                    .filter(t -> s.getId().equals(t.getSprintId()) && doneTaskCodes.contains(t.getStatus()))
                    .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 1)
                    .sum();
            }).sum();
            sprintVelocity = totalPoints / completedSprints.size();
        }

        // 各维度评分（0-100）
        int progressScore = Math.min(100, (int) taskCompletionRate);
        int qualityScore = Math.min(100, (int) bugResolutionRate);
        int scheduleScore = Math.max(0, 100 - (int) overdueRate * 2);
        int requirementScore = Math.min(100, (int) requirementCompletionRate);

        // 工作量均衡度（简化计算）
        int workloadScore = calculateWorkloadBalance(tasks);

        List<ProjectHealthResponse.DimensionScore> dimensions = new ArrayList<>();
        dimensions.add(ProjectHealthResponse.DimensionScore.builder()
            .name("进度").key("progress").score(progressScore).weight(0.30)
            .description("任务完成率").build());
        dimensions.add(ProjectHealthResponse.DimensionScore.builder()
            .name("质量").key("quality").score(qualityScore).weight(0.25)
            .description("Bug 解决率").build());
        dimensions.add(ProjectHealthResponse.DimensionScore.builder()
            .name("进度控制").key("schedule").score(scheduleScore).weight(0.20)
            .description("延期率控制").build());
        dimensions.add(ProjectHealthResponse.DimensionScore.builder()
            .name("需求").key("requirement").score(requirementScore).weight(0.15)
            .description("需求通过率").build());
        dimensions.add(ProjectHealthResponse.DimensionScore.builder()
            .name("团队").key("workload").score(workloadScore).weight(0.10)
            .description("工作量均衡度").build());

        // 综合评分
        int overallScore = (int) dimensions.stream()
            .mapToDouble(d -> d.getScore() * d.getWeight())
            .sum();

        String healthLevel;
        if (overallScore >= 85) healthLevel = "EXCELLENT";
        else if (overallScore >= 70) healthLevel = "GOOD";
        else if (overallScore >= 50) healthLevel = "FAIR";
        else healthLevel = "POOR";

        // 改进建议
        List<String> suggestions = new ArrayList<>();
        if (taskCompletionRate < 60) suggestions.add("任务完成率较低，建议加快任务推进速度");
        if (bugResolutionRate < 60) suggestions.add("Bug 解决率偏低，建议增加测试和修复资源");
        if (overdueRate > 20) suggestions.add("任务延期率较高，建议重新评估任务排期");
        if (requirementCompletionRate < 50) suggestions.add("需求完成率偏低，建议推动需求落地闭环");
        if (workloadScore < 60) suggestions.add("团队工作量分配不均衡，建议重新分配任务");
        if (suggestions.isEmpty()) suggestions.add("项目状态良好，继续保持！");

        return ProjectHealthResponse.builder()
            .projectId(projectId)
            .projectName(project.getName())
            .overallScore(overallScore)
            .healthLevel(healthLevel)
            .dimensions(dimensions)
            .suggestions(suggestions)
            .metrics(ProjectHealthResponse.KeyMetrics.builder()
                .taskCompletionRate(taskCompletionRate)
                .bugResolutionRate(bugResolutionRate)
                .requirementCompletionRate(requirementCompletionRate)
                .overdueRate(overdueRate)
                .avgTaskCompletionHours(0) // 简化：需要活动日志才能计算
                .sprintVelocity(sprintVelocity)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .totalBugs(totalBugs)
                .resolvedBugs(resolvedBugs)
                .totalRequirements(totalRequirements)
                .completedRequirements(completedRequirements)
                .build())
            .build();
    }

    // ============================================
    // 成员工作量
    // ============================================
    @Override
    public MemberWorkloadResponse getMemberWorkload(String projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
            new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );

        // 按 (负责人, 状态) 聚合，不再把项目全部任务/Bug 拉进内存后逐人过滤
        List<Map<String, Object>> taskRows =
                statisticsAggregateMapper.taskCountByAssigneeAndStatus(projectId);
        List<Map<String, Object>> bugRows =
                statisticsAggregateMapper.bugCountByAssigneeAndStatus(projectId);

        Map<String, List<Map<String, Object>>> tasksByUser = new HashMap<>();
        taskRows.forEach(r -> tasksByUser
                .computeIfAbsent(asString(r.get("userId")), k -> new ArrayList<>()).add(r));
        Map<String, List<Map<String, Object>>> bugsByUser = new HashMap<>();
        bugRows.forEach(r -> bugsByUser
                .computeIfAbsent(asString(r.get("userId")), k -> new ArrayList<>()).add(r));

        Set<String> userIds = members.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        Set<String> doneCodes = doneCodesOf(projectId);
        Set<String> progCodes = progCodesOf(projectId);
        Set<String> resolvedBugCodes = resolvedBugCodesOf(projectId);

        List<MemberWorkloadResponse.MemberStats> memberStatsList = members.stream()
            .map(member -> {
                String userId = member.getUserId();
                User user = userMap.get(userId);

                List<Map<String, Object>> myTasks = tasksByUser.getOrDefault(userId, List.of());
                List<Map<String, Object>> myBugs = bugsByUser.getOrDefault(userId, List.of());

                int totalTasks = 0, completedTasks = 0, inProgressTasks = 0;
                int criticalTasks = 0, overdueOpenTasks = 0, testingTasks = 0;
                for (Map<String, Object> row : myTasks) {
                    String status = asString(row.get("status"));
                    int n = asInt(row.get("n"));
                    totalTasks += n;
                    if (doneCodes.contains(status)) {
                        completedTasks += n;
                    } else if (progCodes.contains(status)) {
                        inProgressTasks += n;
                    }
                    if ("TESTING".equals(status)) {
                        testingTasks += n;
                    }
                    criticalTasks += asInt(row.get("criticalCount"));
                    if (!doneCodes.contains(status)) {
                        overdueOpenTasks += asInt(row.get("overdue"));
                    }
                }
                int todoTasks = totalTasks - completedTasks - inProgressTasks;

                int bugCount = 0, resolvedBugCount = 0;
                for (Map<String, Object> row : myBugs) {
                    int n = asInt(row.get("n"));
                    bugCount += n;
                    if (resolvedBugCodes.contains(asString(row.get("status")))) {
                        resolvedBugCount += n;
                    }
                }

                double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

                String loadLevel;
                if (totalTasks >= 10 || inProgressTasks >= 5) loadLevel = "HIGH";
                else if (totalTasks >= 5) loadLevel = "MEDIUM";
                else loadLevel = "LOW";

                Map<String, Integer> taskDist = new LinkedHashMap<>();
                taskDist.put("TODO", todoTasks);
                taskDist.put("DOING", inProgressTasks);
                taskDist.put("TESTING", testingTasks);
                taskDist.put("DONE", completedTasks);

                return MemberWorkloadResponse.MemberStats.builder()
                    .userId(userId)
                    .username(user != null ? user.displayName() : "未知")
                    .avatar(user != null ? user.getAvatar() : null)
                    .totalTasks(totalTasks)
                    .completedTasks(completedTasks)
                    .inProgressTasks(inProgressTasks)
                    .todoTasks(todoTasks)
                    .bugCount(bugCount)
                    .resolvedBugCount(resolvedBugCount)
                    .criticalTasks(criticalTasks)
                    .overdueTasks(overdueOpenTasks)
                    .completionRate(completionRate)
                    .loadLevel(loadLevel)
                    .taskDistribution(taskDist)
                    .build();
            })
            .collect(Collectors.toList());

        double avgTasks = memberStatsList.isEmpty() ? 0 :
            memberStatsList.stream().mapToInt(MemberWorkloadResponse.MemberStats::getTotalTasks).average().orElse(0);
        double avgCompletion = memberStatsList.isEmpty() ? 0 :
            memberStatsList.stream().mapToDouble(MemberWorkloadResponse.MemberStats::getCompletionRate).average().orElse(0);

        String maxLoadMember = memberStatsList.stream()
            .max(Comparator.comparingInt(MemberWorkloadResponse.MemberStats::getTotalTasks))
            .map(MemberWorkloadResponse.MemberStats::getUsername).orElse("-");
        String minLoadMember = memberStatsList.stream()
            .min(Comparator.comparingInt(MemberWorkloadResponse.MemberStats::getTotalTasks))
            .map(MemberWorkloadResponse.MemberStats::getUsername).orElse("-");

        boolean balanced = true;
        if (memberStatsList.size() > 1 && avgTasks > 0) {
            double variance = memberStatsList.stream()
                .mapToDouble(m -> Math.pow(m.getTotalTasks() - avgTasks, 2))
                .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            balanced = (stdDev / avgTasks) < 0.5;
        }

        return MemberWorkloadResponse.builder()
            .projectId(projectId)
            .members(memberStatsList)
            .summary(MemberWorkloadResponse.WorkloadSummary.builder()
                .avgTasksPerMember(avgTasks)
                .avgCompletionRate(avgCompletion)
                .maxLoadMember(maxLoadMember)
                .minLoadMember(minLoadMember)
                .balanced(balanced)
                .build())
            .build();
    }

    // ============================================
    // Sprint 燃尽图
    // ============================================
    private static final class TaskHistorySnapshot {
        private final String category;
        private final TaskStatusEvent lastCompletion;

        private TaskHistorySnapshot(String category, TaskStatusEvent lastCompletion) {
            this.category = category;
            this.lastCompletion = lastCompletion;
        }

        private boolean isDone() {
            return WorkflowStatus.CATEGORY_DONE.equals(category);
        }
    }

    /**
     * 重建某天结束时的任务状态。首个事件之前使用它的 fromCategory，之后依次应用
     * toCategory；不能拿任务当前状态覆盖历史。每天最多记一次完成，且只有当天结束时
     * 仍为 DONE 才计入，避免同日完成后重开制造虚假的完成量。
     */
    private TaskHistorySnapshot taskHistoryAt(List<TaskStatusEvent> events, LocalDate date) {
        if (events.isEmpty()) {
            return new TaskHistorySnapshot(null, null);
        }

        LocalDateTime cutoff = date.plusDays(1).atStartOfDay();
        String category = events.get(0).getFromCategory();
        TaskStatusEvent lastCompletion = null;
        for (TaskStatusEvent event : events) {
            if (!event.getCreatedAt().isBefore(cutoff)) {
                break;
            }
            if (!WorkflowStatus.CATEGORY_DONE.equals(category)
                    && WorkflowStatus.CATEGORY_DONE.equals(event.getToCategory())) {
                lastCompletion = event;
            }
            category = event.getToCategory();
        }
        return new TaskHistorySnapshot(category, lastCompletion);
    }

    @Override
    public BurndownResponse getSprintBurndown(String projectId, String sprintId) {
        Sprint sprint;
        if (sprintId != null && !sprintId.isEmpty()) {
            sprint = sprintMapper.selectById(sprintId);
        } else {
            sprint = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                    .eq(Sprint::getProjectId, projectId)
                    .eq(Sprint::getStatus, "ACTIVE")
                    .last("LIMIT 1")
            );
        }

        if (sprint == null) {
            return BurndownResponse.builder()
                .dataPoints(Collections.emptyList())
                .build();
        }

        List<Task> sprintTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getSprintId, sprint.getId())
        );

        int totalStoryPoints = sprintTasks.stream()
            .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 1)
            .sum();
        int totalTasks = sprintTasks.size();

        LocalDate startDate = sprint.getStartDate() != null ? sprint.getStartDate() : sprint.getCreatedAt().toLocalDate();
        LocalDate endDate = sprint.getEndDate() != null ? sprint.getEndDate() : startDate.plusDays(14);
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (totalDays <= 0) totalDays = 1;

        // 一次取回本迭代全部任务的事件，再按任务和时间重建历史。
        Map<String, List<TaskStatusEvent>> eventsByTask = new HashMap<>();
        if (!sprintTasks.isEmpty()) {
            List<String> taskIds = sprintTasks.stream().map(Task::getId).collect(Collectors.toList());
            List<TaskStatusEvent> events = taskStatusEventMapper.selectList(
                new LambdaQueryWrapper<TaskStatusEvent>()
                    .eq(TaskStatusEvent::getProjectId, projectId)
                    .in(TaskStatusEvent::getTaskId, taskIds)
                    .orderByAsc(TaskStatusEvent::getTaskId)
                    .orderByAsc(TaskStatusEvent::getCreatedAt)
                    .orderByAsc(TaskStatusEvent::getId)
            );
            eventsByTask = events.stream().collect(Collectors.groupingBy(TaskStatusEvent::getTaskId));
            eventsByTask.values().forEach(taskEvents -> taskEvents.sort(
                Comparator.comparing(TaskStatusEvent::getCreatedAt)
                    .thenComparing(TaskStatusEvent::getId)));
        }

        // 计算每天的燃尽数据
        List<BurndownResponse.BurndownPoint> dataPoints = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (long i = 0; i <= totalDays; i++) {
            LocalDate date = startDate.plusDays(i);
            if (date.isAfter(today) && !"COMPLETED".equals(sprint.getStatus())) break;

            // 理想剩余
            int idealRemaining = (int) (totalStoryPoints * (1.0 - (double) i / totalDays));

            int actualCompleted = 0;
            int completedToday = 0;
            int completedTasksOnDate = 0;
            for (Task task : sprintTasks) {
                TaskHistorySnapshot snapshot = taskHistoryAt(
                    eventsByTask.getOrDefault(task.getId(), Collections.emptyList()), date);
                if (!snapshot.isDone()) {
                    continue;
                }

                int points = task.getStoryPoints() != null ? task.getStoryPoints() : 1;
                actualCompleted += points;
                TaskStatusEvent completion = snapshot.lastCompletion;
                if (completion != null && completion.getCreatedAt().toLocalDate().equals(date)) {
                    completedToday += points;
                    completedTasksOnDate++;
                }
            }
            // 重开会让剩余故事点回升；当天完成量只统计进入 DONE，不做负数差分。
            int actualRemaining = Math.max(0, totalStoryPoints - actualCompleted);

            dataPoints.add(BurndownResponse.BurndownPoint.builder()
                .date(date.toString())
                .idealRemaining(idealRemaining)
                .actualRemaining(actualRemaining)
                .completedPoints(completedToday)
                .completedTasks(completedTasksOnDate)
                .build());
        }

        // 迭代开始日期在未来时，上面的循环第一次就 break，dataPoints 为空。
        // 原实现直接 get(size - 1) → IndexOutOfBoundsException → 整个燃尽接口 500，
        // 即"刚建好还没开始的迭代，一打开燃尽图就崩"。空数据是正常状态，不是异常。
        double velocity = 0;
        String estimatedDate = null;
        boolean onTrack = true;
        if (!dataPoints.isEmpty()) {
            int latestRemaining = dataPoints.get(dataPoints.size() - 1).getActualRemaining();
            velocity = totalDays > 0
                    ? (double) (totalStoryPoints - latestRemaining) / dataPoints.size()
                    : 0;
            if (velocity > 0) {
                int daysNeeded = (int) Math.ceil(latestRemaining / velocity);
                estimatedDate = LocalDate.now().plusDays(daysNeeded).toString();
                onTrack = !LocalDate.now().plusDays(daysNeeded).isAfter(endDate);
            }
        }

        return BurndownResponse.builder()
            .sprintId(sprint.getId())
            .sprintName(sprint.getName())
            .sprintStatus(sprint.getStatus())
            .startDate(startDate.toString())
            .endDate(endDate.toString())
            .totalStoryPoints(totalStoryPoints)
            .totalTasks(totalTasks)
            .dataPoints(dataPoints)
            .velocity(velocity)
            .estimatedCompletionDate(estimatedDate)
            .onTrack(onTrack)
            .build();
    }

    // ============================================
    // 甘特图
    // ============================================
    @Override
    public GanttTaskResponse getProjectGantt(String projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        List<Sprint> sprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .orderByAsc(Sprint::getStartDate)
        );

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
        );

        // 甘特进度按工作流分类推断，不写死状态名
        java.util.Set<String> ganttDoneCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, com.devflow.service.WorkflowService.TYPE_TASK,
                com.devflow.entity.WorkflowStatus.CATEGORY_DONE));
        java.util.Set<String> ganttProgCodes = new java.util.HashSet<>(workflowService.statusCodesInCategory(
                projectId, com.devflow.service.WorkflowService.TYPE_TASK,
                com.devflow.entity.WorkflowStatus.CATEGORY_IN_PROGRESS));

        // 获取用户名映射
        Set<String> assigneeIds = tasks.stream()
            .map(Task::getAssigneeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, String> userNameMap = new HashMap<>();
        if (!assigneeIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(assigneeIds);
            users.forEach(u -> userNameMap.put(u.getId(), u.displayName()));
        }

        // Sprint 分组
        List<GanttTaskResponse.SprintGroup> sprintGroups = sprints.stream()
            .map(s -> GanttTaskResponse.SprintGroup.builder()
                .id(s.getId())
                .name(s.getName())
                .status(s.getStatus())
                .startDate(s.getStartDate() != null ? s.getStartDate().toString() : null)
                .endDate(s.getEndDate() != null ? s.getEndDate().toString() : null)
                .build())
            .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();

        // 甘特图任务
        List<GanttTaskResponse.GanttTask> ganttTasks = tasks.stream()
            .map(task -> {
                // 开始日期：使用创建日期
                String startDate = task.getCreatedAt() != null
                    ? task.getCreatedAt().toLocalDate().toString()
                    : LocalDate.now().toString();

                // 结束日期：使用截止日期或预计日期
                String endDate = null;
                if (task.getDueDate() != null) {
                    endDate = task.getDueDate().toLocalDate().toString();
                } else if (task.getUpdatedAt() != null) {
                    endDate = task.getUpdatedAt().toLocalDate().toString();
                } else {
                    endDate = LocalDate.now().plusDays(7).toString();
                }

                // 进度：按工作流分类推断（终态 100%，进行中 50%，待办 0%），
                // 不再依赖写死的状态名
                int progress;
                if (ganttDoneCodes.contains(task.getStatus())) {
                    progress = 100;
                } else if (ganttProgCodes.contains(task.getStatus())) {
                    progress = 50;
                } else {
                    progress = 0;
                }

                boolean overdue = task.getDueDate() != null
                    && task.getDueDate().isBefore(now)
                    && !ganttDoneCodes.contains(task.getStatus());

                return GanttTaskResponse.GanttTask.builder()
                    .id(task.getId())
                    .title(task.getTitle())
                    .sprintId(task.getSprintId())
                    .assigneeId(task.getAssigneeId())
                    .assigneeName(task.getAssigneeId() != null ? userNameMap.getOrDefault(task.getAssigneeId(), "未分配") : "未分配")
                    .status(task.getStatus())
                    .priority(task.getPriority())
                    .startDate(startDate)
                    .endDate(endDate)
                    .progress(progress)
                    .storyPoints(task.getStoryPoints())
                    .overdue(overdue)
                    .dependencies(Collections.emptyList())
                    .build();
            })
            .collect(Collectors.toList());

        return GanttTaskResponse.builder()
            .projectId(projectId)
            .projectName(project.getName())
            .sprintGroups(sprintGroups)
            .tasks(ganttTasks)
            .build();
    }

    // ============================================
    // 辅助方法
    // ============================================

    /**
     * 构建任务趋势数据（最近 N 天的新建数 / 完成数）。
     *
     * 两次 GROUP BY 查询拿回 (项目, 日期) 的新建数与进入 DONE 的事件数。
     * 原实现把全部任务加载进内存每一天过滤一遍 —— 14 天 × N 条任务。
     */
    private List<EnterpriseStatsResponse.TrendPoint> buildTaskTrend(List<String> projectIds, int days) {
        List<EnterpriseStatsResponse.TrendPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();

        Map<String, Map<String, Integer>> createdByDate = new HashMap<>();
        for (Map<String, Object> row : statisticsAggregateMapper.taskCreatedPerDay(projectIds, days)) {
            createdByDate.computeIfAbsent(asString(row.get("d")), k -> new HashMap<>())
                    .merge(asString(row.get("projectId")), asInt(row.get("n")), Integer::sum);
        }

        Map<String, Map<String, Integer>> completedByDate = new HashMap<>();
        for (Map<String, Object> row : statisticsAggregateMapper.taskCompletedPerDay(projectIds, days)) {
            completedByDate.computeIfAbsent(asString(row.get("d")), k -> new HashMap<>())
                    .merge(asString(row.get("projectId")), asInt(row.get("n")), Integer::sum);
        }

        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            int created = createdByDate.getOrDefault(date, Map.of())
                    .values().stream().mapToInt(Integer::intValue).sum();
            int completed = completedByDate.getOrDefault(date, Map.of())
                    .values().stream().mapToInt(Integer::intValue).sum();

            trend.add(EnterpriseStatsResponse.TrendPoint.builder()
                .date(date)
                .created(created)
                .completed(completed)
                .build());
        }

        return trend;
    }

    /** 计算工作量均衡度 */
    private int calculateWorkloadBalance(List<Task> tasks) {
        Map<String, Integer> assigneeTaskCount = tasks.stream()
            .filter(t -> t.getAssigneeId() != null)
            .collect(Collectors.groupingBy(Task::getAssigneeId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        if (assigneeTaskCount.isEmpty() || assigneeTaskCount.size() == 1) return 100;

        double avg = assigneeTaskCount.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        if (avg == 0) return 100;

        double variance = assigneeTaskCount.values().stream()
            .mapToDouble(c -> Math.pow(c - avg, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = stdDev / avg; // 变异系数

        // 变异系数 < 0.3 为均衡，> 0.7 为严重不均
        if (cv < 0.3) return 95;
        if (cv < 0.5) return 80;
        if (cv < 0.7) return 60;
        return 40;
    }

}
