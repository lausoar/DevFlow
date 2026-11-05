package com.devflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 成员工作量统计响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberWorkloadResponse {

    /** 项目ID */
    private String projectId;

    /** 成员工作量列表 */
    private List<MemberStats> members;

    /** 工作量汇总 */
    private WorkloadSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberStats {
        /** 用户ID */
        private String userId;
        /** 用户名 */
        private String username;
        /** 头像 */
        private String avatar;
        /** 总任务数 */
        private int totalTasks;
        /** 已完成任务数 */
        private int completedTasks;
        /** 进行中任务数 */
        private int inProgressTasks;
        /** 待办任务数 */
        private int todoTasks;
        /** 负责 Bug 数 */
        private int bugCount;
        /** 已解决 Bug 数 */
        private int resolvedBugCount;
        /** 紧急任务数 */
        private int criticalTasks;
        /** 延期任务数 */
        private int overdueTasks;
        /** 完成率 */
        private double completionRate;
        /** 工作负载评分（高/中/低） */
        private String loadLevel;
        /** 任务状态分布 */
        private Map<String, Integer> taskDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkloadSummary {
        /** 平均任务数/人 */
        private double avgTasksPerMember;
        /** 平均完成率 */
        private double avgCompletionRate;
        /** 最高负载成员 */
        private String maxLoadMember;
        /** 最低负载成员 */
        private String minLoadMember;
        /** 负载是否均衡 */
        private boolean balanced;
    }
}
