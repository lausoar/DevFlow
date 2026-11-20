package com.devflow.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求详情响应
 */
@Data
@Builder
public class RequirementResponse {

    private String id;
    private String title;
    private String description;
    private String projectId;
    private String projectName;
    private String requesterId;
    private String requesterName;
    private String requesterAvatar;
    private String approverId;
    private String approverName;
    private String status;
    private String priority;
    private String source;
    private String category;
    private Double estimatedHours;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private String rejectReason;
    private String parentId;
    /** 所属目标（OKR） */
    private String objectiveId;
    private Integer sort;

    /** RICE 评分输入与派生分数（riceScore 由数据库生成列计算，未评分为 null） */
    private Integer reach;
    private java.math.BigDecimal impact;
    private Integer confidence;
    private java.math.BigDecimal effort;
    private java.math.BigDecimal riceScore;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联的任务列表 */
    private List<LinkedTask> linkedTasks;

    /** 审批记录 */
    private List<ApprovalRecord> approvalRecords;

    /**
     * 关联任务信息
     */
    @Data
    @Builder
    public static class LinkedTask {
        private String taskId;
        private String taskTitle;
        private String taskStatus;
        private String assigneeName;
    }

    /**
     * 审批记录
     */
    @Data
    @Builder
    public static class ApprovalRecord {
        private String id;
        private String approverId;
        private String approverName;
        private String approverAvatar;
        private Integer approvalLevel;
        private String action;
        private String comment;
        private LocalDateTime createdAt;
    }
}
