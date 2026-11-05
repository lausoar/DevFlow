package com.devflow.service;

import com.devflow.dto.response.*;

/**
 * 统计报表服务接口
 */
public interface StatisticsService {

    /**
     * 获取企业级统计报表
     *
     * @param enterpriseId 企业ID
     * @return 企业统计报表
     */
    EnterpriseStatsResponse getEnterpriseStats(String enterpriseId);

    /**
     * 获取项目健康度评估
     *
     * @param projectId 项目ID
     * @return 项目健康度
     */
    ProjectHealthResponse getProjectHealth(String projectId);

    /**
     * 获取成员工作量统计
     *
     * @param projectId 项目ID
     * @return 成员工作量
     */
    MemberWorkloadResponse getMemberWorkload(String projectId);

    /**
     * 获取 Sprint 燃尽图数据
     *
     * @param projectId 项目ID
     * @param sprintId  Sprint ID（为空时取当前活跃 Sprint）
     * @return 燃尽图数据
     */
    BurndownResponse getSprintBurndown(String projectId, String sprintId);

    /**
     * 获取项目甘特图数据
     *
     * @param projectId 项目ID
     * @return 甘特图数据
     */
    GanttTaskResponse getProjectGantt(String projectId);
}
