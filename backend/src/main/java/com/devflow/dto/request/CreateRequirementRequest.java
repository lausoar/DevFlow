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

    /** 所属目标（OKR；为空表示不对齐目标） */
    private String objectiveId;

    /** RICE 触达人数（可选；与 impact/confidence/effort 齐全时才有分数） */
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

    /** RICE 工作量（人日，必须大于 0） */
    @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "工作量至少为 0.1 人日")
    @jakarta.validation.constraints.DecimalMax(value = "999.9", message = "工作量过大")
    private java.math.BigDecimal effort;
}
