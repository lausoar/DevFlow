package com.devflow.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 项目 Dashboard 响应
 */
@Data
@Builder
public class DashboardResponse {

    /** 项目基本信息 */
    private ProjectInfo project;

    /** 统计概览 */
    private StatsOverview stats;

    /** Sprint 进度 */
    private SprintProgress sprint;

    /** 任务状态分布 */
    private Map<String, Integer> taskDistribution;

    /** Bug 状态分布 */
    private Map<String, Integer> bugDistribution;

    /** 最近活动 */
    private List<ActivityItem> recentActivities;

    /** 团队成员负载 */
    private List<MemberWorkload> memberWorkloads;

    @Data
    @Builder
    public static class ProjectInfo {
        private String id;
        private String name;
        private String key;
        private String status;
        private int memberCount;
        private int totalTasks;
        private int totalBugs;
    }

    @Data
    @Builder
    public static class StatsOverview {
        private int totalTasks;
        private int completedTasks;
        private int inProgressTasks;
        private int todoTasks;
        private int openBugs;
        private int criticalBugs;
        private double completionRate;  // 完成率
        private double overdueRate;     // 延期率
    }

    @Data
    @Builder
    public static class SprintProgress {
        private String id;
        private String name;
        private String status;
        private int totalTasks;
        private int completedTasks;
        private int remainingDays;
        private double progress;
        private List<BurndownPoint> burndownData;
    }

    @Data
    @Builder
    public static class BurndownPoint {
        private String date;
        private int ideal;
        private int actual;
    }

    @Data
    @Builder
    public static class ActivityItem {
        private String userId;
        private String username;
        private String action;
        private String target;
        private String time;
    }

    @Data
    @Builder
    public static class MemberWorkload {
        private String userId;
        private String username;
        private int taskCount;
        private int completedCount;
        private int bugCount;
    }
}
