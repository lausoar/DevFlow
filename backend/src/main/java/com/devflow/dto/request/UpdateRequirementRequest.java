package com.devflow.dto.request;

import lombok.Data;

/**
 * 更新需求请求
 */
@Data
public class UpdateRequirementRequest {

    /** 需求标题 */
    private String title;

    /** 需求描述 */
    private String description;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 需求来源: CUSTOMER / INTERNAL / MARKET / TECHNICAL */
    private String source;

    /** 需求分类: FEATURE / IMPROVEMENT / BUGFIX / OTHER */
    private String category;

    /** 预估工时（小时，支持小数） */
    private Double estimatedHours;

    /** 状态: OPEN / COMPLETED / CLOSED */
    private String status;
}
