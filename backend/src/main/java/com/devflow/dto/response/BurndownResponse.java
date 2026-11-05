package com.devflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sprint 燃尽图数据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurndownResponse {

    /** Sprint ID */
    private String sprintId;

    /** Sprint 名称 */
    private String sprintName;

    /** Sprint 状态 */
    private String sprintStatus;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 总故事点 */
    private int totalStoryPoints;

    /** 总任务数 */
    private int totalTasks;

    /** 燃尽数据点列表 */
    private List<BurndownPoint> dataPoints;

    /** 燃尽速率（每天完成的故事点） */
    private double velocity;

    /** 预计完成日期 */
    private String estimatedCompletionDate;

    /** 是否_on_track */
    private boolean onTrack;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BurndownPoint {
        /** 日期 */
        private String date;
        /** 理想剩余故事点 */
        private int idealRemaining;
        /** 实际剩余故事点 */
        private int actualRemaining;
        /** 当天完成的故事点 */
        private int completedPoints;
        /** 当天完成的任务数 */
        private int completedTasks;
    }
}
