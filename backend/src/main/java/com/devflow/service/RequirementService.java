package com.devflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.dto.request.CreateRequirementRequest;
import com.devflow.dto.request.UpdateRequirementRequest;
import com.devflow.dto.response.RequirementResponse;
import com.devflow.dto.response.RequirementStatsResponse;
import com.devflow.entity.Requirement;

/**
 * 需求服务接口
 */
public interface RequirementService {

    /**
     * 创建需求
     */
    Requirement createRequirement(String projectId, CreateRequirementRequest request, String requesterId);

    /**
     * 获取需求详情
     */
    RequirementResponse getRequirementDetail(String requirementId);

    /**
     * 分页查询需求
     */
    Page<RequirementResponse> listRequirements(String projectId, String status, String priority,
                                                String keyword, int page, int size);

    /**
     * 更新需求
     */
    Requirement updateRequirement(String requirementId, UpdateRequirementRequest request, String userId);

    /**
     * 删除需求
     */
    void deleteRequirement(String requirementId, String userId);




    /**
     * 关联任务
     */
    void linkTask(String requirementId, String taskId);

    /**
     * 取消关联任务
     */
    void unlinkTask(String requirementId, String taskId);

    /**
     * 获取需求统计
     */
    RequirementStatsResponse getStats(String projectId);
}
