package com.devflow.service;

import com.devflow.entity.DeployEnvironment;
import com.devflow.entity.Deployment;

import java.util.List;
import java.util.Map;

/**
 * 部署与发布闭环。
 *
 * 这是"部署上线"环节在本平台的落点，也是 DORA 四指标的数据源：
 * - 环境是一等对象（不是 CI 变量），受保护环境需要审批；
 * - 每次部署落一条不可变记录（版本、提交、触发人、审批链、结果）；
 * - 回滚是独立动作，生成新记录而不篡改历史。
 */
public interface DeploymentService {

    // ==================== 环境 ====================

    List<DeployEnvironment> listEnvironments(String projectId);

    DeployEnvironment createEnvironment(String projectId, DeployEnvironment env, String operatorId);

    DeployEnvironment updateEnvironment(String envId, DeployEnvironment patch, String operatorId);

    void deleteEnvironment(String envId, String operatorId);

    // ==================== 部署记录 ====================

    /** 项目部署记录（倒序，可按环境/版本过滤） */
    List<Deployment> listDeployments(String projectId, String environmentId, String releaseId, int limit);

    Deployment getDeployment(String deploymentId);

    /**
     * 发起部署：需要审批的环境进入 PENDING_APPROVAL，否则直接 APPROVED。
     * 同一环境不允许存在并发的未完成部署（环境互斥）。
     */
    Deployment createDeployment(String projectId, String environmentId, Deployment request, String operatorId);

    /** 审批通过（仅项目 OWNER/MANAGER，且不能自审） */
    Deployment approve(String deploymentId, String operatorId, String note);

    /** 驳回 */
    Deployment reject(String deploymentId, String operatorId, String note);

    /** 标记开始 / 结束（成功或失败） */
    Deployment markDeploying(String deploymentId, String operatorId);

    Deployment markFinished(String deploymentId, boolean success, String note, String operatorId);

    /** 回滚：以指定部署记录的版本再发一次，生成新记录并链接 rollbackOf */
    Deployment rollback(String deploymentId, String operatorId, String note);

    // ==================== DORA ====================

    /**
     * DORA 四指标（近 N 天）：
     * 部署频率、变更前置时间、变更失败率、平均恢复时间。
     * 只看 PROD 环境的成功部署；样本不足时明确返回样本数。
     */
    Map<String, Object> doraMetrics(String projectId, int days);
}
