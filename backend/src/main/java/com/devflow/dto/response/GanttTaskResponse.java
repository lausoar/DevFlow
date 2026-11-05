package com.devflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目甘特图数据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GanttTaskResponse {

    /** 项目ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** Sprint 列表（甘特图分组） */
    private List<SprintGroup> sprintGroups;

    /** 任务列表（甘特图数据） */
    private List<GanttTask> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SprintGroup {
        /** Sprint ID */
        private String id;
        /** Sprint 名称 */
        private String name;
        /** 状态 */
        private String status;
        /** 开始日期 */
        private String startDate;
        /** 结束日期 */
        private String endDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GanttTask {
        /** 任务ID */
        private String id;
        /** 任务标题 */
        private String title;
        /** Sprint ID */
        private String sprintId;
        /** 负责人ID */
        private String assigneeId;
        /** 负责人名称 */
        private String assigneeName;
        /** 状态 */
        private String status;
        /** 优先级 */
        private String priority;
        /** 开始日期 */
        private String startDate;
        /** 结束日期（预计或实际） */
        private String endDate;
        /** 进度百分比（0-100） */
        private int progress;
        /** 故事点 */
        private Integer storyPoints;
        /** 是否延期 */
        private boolean overdue;
        /** 依赖的任务ID列表 */
        private List<String> dependencies;
    }
}
