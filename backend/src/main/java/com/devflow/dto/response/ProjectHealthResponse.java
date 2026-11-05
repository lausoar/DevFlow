package com.devflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目健康度评估响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectHealthResponse {

    /** 项目ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 综合健康分数（0-100） */
    private int overallScore;

    /** 健康等级：EXCELLENT/GOOD/FAIR/POOR */
    private String healthLevel;

    /** 各维度评分 */
    private List<DimensionScore> dimensions;

    /** 改进建议 */
    private List<String> suggestions;

    /** 关键指标 */
    private KeyMetrics metrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionScore {
        /** 维度名称 */
        private String name;
        /** 维度标识 */
        private String key;
        /** 分数（0-100） */
        private int score;
        /** 权重 */
        private double weight;
        /** 描述 */
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyMetrics {
        /** 任务完成率 */
        private double taskCompletionRate;
        /** Bug 解决率 */
        private double bugResolutionRate;
        /** 需求通过率 */
        private double requirementCompletionRate;
        /** 延期率 */
        private double overdueRate;
        /** 平均任务完成时长（小时） */
        private double avgTaskCompletionHours;
        /** Sprint 速率（每 Sprint 完成的故事点） */
        private double sprintVelocity;
        /** 总任务数 */
        private int totalTasks;
        /** 已完成任务数 */
        private int completedTasks;
        /** 总 Bug 数 */
        private int totalBugs;
        /** 已解决 Bug 数 */
        private int resolvedBugs;
        /** 总需求数 */
        private int totalRequirements;
        /** 已完成需求数 */
        private int completedRequirements;
    }
}
