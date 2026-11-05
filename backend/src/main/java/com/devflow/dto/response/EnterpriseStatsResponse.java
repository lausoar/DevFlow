package com.devflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 企业级统计报表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseStatsResponse {

    /** 项目总览 */
    private ProjectOverview projectOverview;

    /** 任务总览 */
    private TaskOverview taskOverview;

    /** Bug 总览 */
    private BugOverview bugOverview;

    /** 需求总览 */
    private RequirementOverview requirementOverview;

    /** 各项目的任务完成情况 */
    private List<ProjectProgress> projectProgressList;

    /** 任务趋势数据（按天） */
    private List<TrendPoint> taskTrend;

    /** 任务状态分布 */
    private Map<String, Integer> taskStatusDistribution;

    /** Bug 严重程度分布 */
    private Map<String, Integer> bugSeverityDistribution;

    /** 需求状态分布 */
    private Map<String, Integer> requirementStatusDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectOverview {
        private int totalProjects;
        private int activeProjects;
        private int planningProjects;
        private int archivedProjects;
        private int totalMembers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskOverview {
        private int totalTasks;
        private int completedTasks;
        private int inProgressTasks;
        private int todoTasks;
        private int overdueTasks;
        private double completionRate;
        private double overdueRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BugOverview {
        private int totalBugs;
        private int openBugs;
        private int resolvedBugs;
        private int criticalBugs;
        private double resolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequirementOverview {
        private int totalRequirements;
        private int openRequirements;
        private int completedRequirements;
        private int closedRequirements;
        private double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectProgress {
        private String projectId;
        private String projectName;
        private String projectKey;
        private String status;
        private int totalTasks;
        private int completedTasks;
        private int totalBugs;
        private int openBugs;
        private double progress;
        private int memberCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private int created;
        private int completed;
    }
}
