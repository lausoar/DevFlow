package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建需求请求
 */
@Data
public class CreateRequirementRequest {

    @NotBlank(message = "需求标题不能为空")
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

    /** 父需求ID */
    private String parentId;
}
