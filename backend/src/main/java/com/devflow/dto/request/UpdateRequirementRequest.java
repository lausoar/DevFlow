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

    /** 所属目标（OKR；传空串表示取消对齐） */
    private String objectiveId;

    /** RICE 触达人数 */
    @jakarta.validation.constraints.Min(value = 1, message = "触达人数至少为 1")
    @jakarta.validation.constraints.Max(value = 1_000_000, message = "触达人数过大")
    private Integer reach;

    /** RICE 影响程度（0.25 / 0.5 / 1 / 2 / 3） */
    @jakarta.validation.constraints.DecimalMin(value = "0.25", message = "影响程度至少为 0.25")
    @jakarta.validation.constraints.DecimalMax(value = "3", message = "影响程度最大为 3")
    private java.math.BigDecimal impact;

    /** RICE 置信度百分比 1-100 */
    @jakarta.validation.constraints.Min(value = 1, message = "置信度至少为 1%")
    @jakarta.validation.constraints.Max(value = 100, message = "置信度最大为 100%")
    private Integer confidence;

    /** RICE 工作量（人日） */
    @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "工作量至少为 0.1 人日")
    @jakarta.validation.constraints.DecimalMax(value = "999.9", message = "工作量过大")
    private java.math.BigDecimal effort;
}
