package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.dto.response.*;
import com.devflow.entity.*;
import com.devflow.mapper.*;
import com.devflow.service.StatisticsService;
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

    // ============================================
    // 企业级统计
    // ============================================
    @Override
    public EnterpriseStatsResponse getEnterpriseStats(String enterpriseId) {
        // 查询企业下所有项目
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

        // 查询所有任务、Bug、需求
        List<Task> allTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().in(Task::getProjectId, projectIds)
        );
        List<Bug> allBugs = bugMapper.selectList(
            new LambdaQueryWrapper<Bug>().in(Bug::getProjectId, projectIds)
        );
        List<Requirement> allRequirements = requirementMapper.selectList(
            new LambdaQueryWrapper<Requirement>().in(Requirement::getProjectId, projectIds)
        );

        // 项目总览
        int activeProjects = (int) projects.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count();
        int planningProjects = (int) projects.stream().filter(p -> "PLANNING".equals(p.getStatus())).count();
        int archivedProjects = (int) projects.stream().filter(p -> "ARCHIVED".equals(p.getStatus())).count();

        // 统计总成员数（去重）
        Set<String> memberIds = new HashSet<>();
        for (String pid : projectIds) {
            List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, pid)
            );
            members.forEach(m -> memberIds.add(m.getUserId()));
        }

        // 任务总览
        int totalTasks = allTasks.size();
        int completedTasks = (int) allTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int inProgressTasks = (int) allTasks.stream().filter(t -> "DOING".equals(t.getStatus())).count();
        int todoTasks = (int) allTasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
        int overdueTasks = (int) allTasks.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now())
                && !"DONE".equals(t.getStatus()))
            .count();

        // Bug 总览
        int totalBugs = allBugs.size();
        int openBugs = (int) allBugs.stream()
            .filter(b -> !"RESOLVED".equals(b.getStatus()) && !"CLOSED".equals(b.getStatus()))
            .count();
        int resolvedBugs = (int) allBugs.stream()
            .filter(b -> "RESOLVED".equals(b.getStatus()) || "CLOSED".equals(b.getStatus()))
            .count();
        int criticalBugs = (int) allBugs.stream()
            .filter(b -> "BLOCKER".equals(b.getSeverity()) || "CRITICAL".equals(b.getSeverity()))
            .count();

        // 需求总览
        int totalRequirements = allRequirements.size();
        int openRequirements = (int) allRequirements.stream().filter(r -> "OPEN".equals(r.getStatus())).count();
        int completedRequirements = (int) allRequirements.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        int closedRequirements = (int) allRequirements.stream().filter(r -> "CLOSED".equals(r.getStatus())).count();

        // 各项目进度
        List<EnterpriseStatsResponse.ProjectProgress> projectProgressList = projects.stream()
            .map(project -> {
                List<Task> projectTasks = allTasks.stream()
                    .filter(t -> project.getId().equals(t.getProjectId()))
                    .collect(Collectors.toList());
                List<Bug> projectBugs = allBugs.stream()
                    .filter(b -> project.getId().equals(b.getProjectId()))
                    .collect(Collectors.toList());

                int pTotal = projectTasks.size();
                int pCompleted = (int) projectTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
                int pOpenBugs = (int) projectBugs.stream()
                    .filter(b -> !"RESOLVED".equals(b.getStatus()) && !"CLOSED".equals(b.getStatus()))
                    .count();

                List<ProjectMember> pMembers = projectMemberMapper.selectList(
                    new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, project.getId())
                );

                return EnterpriseStatsResponse.ProjectProgress.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .projectKey(project.getProjectKey())
                    .status(project.getStatus())
                    .totalTasks(pTotal)
                    .completedTasks(pCompleted)
                    .totalBugs(projectBugs.size())
                    .openBugs(pOpenBugs)
                    .progress(pTotal > 0 ? (double) pCompleted / pTotal * 100 : 0)
                    .memberCount(pMembers.size())
                    .build();
            })
            .collect(Collectors.toList());

        // 任务趋势（最近 14 天）
        List<EnterpriseStatsResponse.TrendPoint> taskTrend = buildTaskTrend(allTasks, 14);

        // 分布统计
        Map<String, Integer> taskStatusDist = new LinkedHashMap<>();
        taskStatusDist.put("TODO", todoTasks);
        taskStatusDist.put("DOING", inProgressTasks);
        taskStatusDist.put("TESTING", (int) allTasks.stream().filter(t -> "TESTING".equals(t.getStatus())).count());
        taskStatusDist.put("DONE", completedTasks);

        Map<String, Integer> bugSeverityDist = new LinkedHashMap<>();
        bugSeverityDist.put("BLOCKER", (int) allBugs.stream().filter(b -> "BLOCKER".equals(b.getSeverity())).count());
        bugSeverityDist.put("CRITICAL", (int) allBugs.stream().filter(b -> "CRITICAL".equals(b.getSeverity())).count());
        bugSeverityDist.put("MAJOR", (int) allBugs.stream().filter(b -> "MAJOR".equals(b.getSeverity())).count());
        bugSeverityDist.put("MINOR", (int) allBugs.stream().filter(b -> "MINOR".equals(b.getSeverity())).count());
        bugSeverityDist.put("TRIVIAL", (int) allBugs.stream().filter(b -> "TRIVIAL".equals(b.getSeverity())).count());

        Map<String, Integer> reqStatusDist = new LinkedHashMap<>();
        reqStatusDist.put("OPEN", openRequirements);
        reqStatusDist.put("COMPLETED", completedRequirements);
        reqStatusDist.put("CLOSED", closedRequirements);

        return EnterpriseStatsResponse.builder()
            .projectOverview(EnterpriseStatsResponse.ProjectOverview.builder()
                .totalProjects(projects.size())
                .activeProjects(activeProjects)
                .planningProjects(planningProjects)
                .archivedProjects(archivedProjects)
                .totalMembers(memberIds.size())
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
            new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId)
        );
        List<Requirement> requirements = requirementMapper.selectList(
            new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId)
        );
        List<Sprint> sprints = sprintMapper.selectList(
            new LambdaQueryWrapper<Sprint>().eq(Sprint::getProjectId, projectId)
        );

        // 计算各维度指标
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        double taskCompletionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        int totalBugs = bugs.size();
        int resolvedBugs = (int) bugs.stream()
            .filter(b -> "RESOLVED".equals(b.getStatus()) || "CLOSED".equals(b.getStatus()))
            .count();
        double bugResolutionRate = totalBugs > 0 ? (double) resolvedBugs / totalBugs * 100 : 0;

        int totalRequirements = requirements.size();
        int completedRequirements = (int) requirements.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        double requirementCompletionRate = totalRequirements > 0 ? (double) completedRequirements / totalRequirements * 100 : 0;

        int overdueTasks = (int) tasks.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now())
                && !"DONE".equals(t.getStatus()))
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
                    .filter(t -> s.getId().equals(t.getSprintId()) && "DONE".equals(t.getStatus()))
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
        // 获取项目成员
        List<ProjectMember> members = projectMemberMapper.selectList(
            new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );

        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
        );
        List<Bug> bugs = bugMapper.selectList(
            new LambdaQueryWrapper<Bug>().eq(Bug::getProjectId, projectId)
        );

        // 获取成员用户名映射
        Set<String> userIds = members.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            users.forEach(u -> userMap.put(u.getId(), u));
        }

        LocalDateTime now = LocalDateTime.now();

        List<MemberWorkloadResponse.MemberStats> memberStatsList = members.stream()
            .map(member -> {
                String userId = member.getUserId();
                User user = userMap.get(userId);

                List<Task> memberTasks = tasks.stream()
                    .filter(t -> userId.equals(t.getAssigneeId()))
                    .collect(Collectors.toList());
                List<Bug> memberBugs = bugs.stream()
                    .filter(b -> userId.equals(b.getAssigneeId()))
                    .collect(Collectors.toList());

                int totalTasks = memberTasks.size();
                int completedTasks = (int) memberTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
                int inProgressTasks = (int) memberTasks.stream().filter(t -> "DOING".equals(t.getStatus())).count();
                int todoTasks = (int) memberTasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
                int bugCount = memberBugs.size();
                int resolvedBugCount = (int) memberBugs.stream()
                    .filter(b -> "RESOLVED".equals(b.getStatus()) || "CLOSED".equals(b.getStatus()))
                    .count();
                int criticalTasks = (int) memberTasks.stream()
                    .filter(t -> "CRITICAL".equals(t.getPriority()) || "HIGH".equals(t.getPriority()))
                    .count();
                int overdueTasks = (int) memberTasks.stream()
                    .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(now)
                        && !"DONE".equals(t.getStatus()))
                    .count();
                double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

                // 负载等级
                String loadLevel;
                if (totalTasks >= 10 || inProgressTasks >= 5) loadLevel = "HIGH";
                else if (totalTasks >= 5) loadLevel = "MEDIUM";
                else loadLevel = "LOW";

                Map<String, Integer> taskDist = new LinkedHashMap<>();
                taskDist.put("TODO", todoTasks);
                taskDist.put("DOING", inProgressTasks);
                taskDist.put("TESTING", (int) memberTasks.stream().filter(t -> "TESTING".equals(t.getStatus())).count());
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
                    .overdueTasks(overdueTasks)
                    .completionRate(completionRate)
                    .loadLevel(loadLevel)
                    .taskDistribution(taskDist)
                    .build();
            })
            .collect(Collectors.toList());

        // 汇总
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

        // 判断是否均衡（标准差 / 平均值 < 0.5 视为均衡）
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

        // 计算每天的燃尽数据
        List<BurndownResponse.BurndownPoint> dataPoints = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (long i = 0; i <= totalDays; i++) {
            LocalDate date = startDate.plusDays(i);
            if (date.isAfter(today) && !"COMPLETED".equals(sprint.getStatus())) break;

            // 理想剩余
            int idealRemaining = (int) (totalStoryPoints * (1.0 - (double) i / totalDays));

            // 实际剩余（截止到该日期完成的任务）
            final LocalDate currentDate = date;
            int actualCompleted = sprintTasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().toLocalDate().isBefore(currentDate.plusDays(1)))
                .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 1)
                .sum();
            int actualRemaining = Math.max(0, totalStoryPoints - actualCompleted);

            // 当天完成
            final LocalDate prevDate = date.minusDays(1);
            int completedBefore = sprintTasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().toLocalDate().isBefore(prevDate.plusDays(1)))
                .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 1)
                .sum();
            int completedToday = actualCompleted - completedBefore;

            long completedTasksOnDate = sprintTasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().toLocalDate().isEqual(date))
                .count();

            dataPoints.add(BurndownResponse.BurndownPoint.builder()
                .date(date.toString())
                .idealRemaining(idealRemaining)
                .actualRemaining(actualRemaining)
                .completedPoints(Math.max(0, completedToday))
                .completedTasks((int) completedTasksOnDate)
                .build());
        }

        // 计算速率和预计完成日期
        double velocity = totalDays > 0 ? (double) (totalStoryPoints - dataPoints.get(dataPoints.size() - 1).getActualRemaining()) / dataPoints.size() : 0;
        String estimatedDate = null;
        boolean onTrack = true;
        if (velocity > 0) {
            int remaining = dataPoints.get(dataPoints.size() - 1).getActualRemaining();
            int daysNeeded = (int) Math.ceil(remaining / velocity);
            estimatedDate = LocalDate.now().plusDays(daysNeeded).toString();
            onTrack = LocalDate.now().plusDays(daysNeeded).isBefore(endDate) ||
                LocalDate.now().plusDays(daysNeeded).isEqual(endDate);
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

                // 进度
                int progress;
                switch (task.getStatus()) {
                    case "DONE": progress = 100; break;
                    case "TESTING": progress = 80; break;
                    case "DOING": progress = 50; break;
                    case "TODO": progress = 0; break;
                    default: progress = 0;
                }

                boolean overdue = task.getDueDate() != null
                    && task.getDueDate().isBefore(now)
                    && !"DONE".equals(task.getStatus());

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

    /** 构建任务趋势数据 */
    private List<EnterpriseStatsResponse.TrendPoint> buildTaskTrend(List<Task> tasks, int days) {
        List<EnterpriseStatsResponse.TrendPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            final LocalDate dateEnd = date.plusDays(1);

            int created = (int) tasks.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().isEqual(date))
                .count();

            int completed = (int) tasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().toLocalDate().isEqual(date))
                .count();

            trend.add(EnterpriseStatsResponse.TrendPoint.builder()
                .date(date.toString())
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
