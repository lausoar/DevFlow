package com.devflow.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 需求统计响应
 */
@Data
@Builder
public class RequirementStatsResponse {

    /** 总需求数 */
    private Integer total;

    /** 开放中数 */
    private Integer open;

    /** 已完成数 */
    private Integer completed;

    /** 已关闭数 */
    private Integer closed;

    /** 按优先级分布 */
    private Map<String, Integer> priorityDistribution;

    /** 按来源分布 */
    private Map<String, Integer> sourceDistribution;

    /** 按分类分布 */
    private Map<String, Integer> categoryDistribution;
}
