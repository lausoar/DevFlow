package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.DeployEnvironment;
import com.devflow.entity.Deployment;
import com.devflow.security.AccessGuard;
import com.devflow.service.DeploymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 部署与环境。
 *
 * 权限口径：
 * - 读：项目可见；
 * - 环境增删改、发起部署：project:edit（OWNER/MANAGER）；
 * - 审批：同 project:edit，且不能自审（服务层强制）。
 */
@Tag(name = "部署管理", description = "环境、部署记录、审批、回滚、DORA 指标")
@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final AccessGuard guard;

    // ==================== 环境 ====================

    @Operation(summary = "项目环境列表")
    @GetMapping("/projects/{projectId}/environments")
    public R<List<DeployEnvironment>> listEnvironments(@PathVariable String projectId,
                                                       Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(deploymentService.listEnvironments(projectId));
    }

    @Operation(summary = "创建环境")
    @PostMapping("/projects/{projectId}/environments")
    public R<DeployEnvironment> createEnvironment(@PathVariable String projectId,
                                                  @RequestBody DeployEnvironment env,
                                                  Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_EDIT);
        return R.ok(deploymentService.createEnvironment(projectId, env, userId));
    }

    @Operation(summary = "更新环境")
    @PutMapping("/environments/{id}")
    public R<DeployEnvironment> updateEnvironment(@PathVariable String id,
                                                  @RequestBody DeployEnvironment patch,
                                                  Authentication authentication) {
        String userId = guard.userId(authentication);
        DeployEnvironment env = requireEnv(id);
        guard.requireProjectPermission(userId, env.getProjectId(), PermissionCodes.PROJECT_EDIT);
        return R.ok(deploymentService.updateEnvironment(id, patch, userId));
    }

    @Operation(summary = "删除环境（有部署记录时拒绝）")
    @DeleteMapping("/environments/{id}")
    public R<Void> deleteEnvironment(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        DeployEnvironment env = requireEnv(id);
        guard.requireProjectPermission(userId, env.getProjectId(), PermissionCodes.PROJECT_EDIT);
        deploymentService.deleteEnvironment(id, userId);
        return R.ok();
    }

    // ==================== 部署记录 ====================

    @Operation(summary = "部署记录列表")
    @GetMapping("/projects/{projectId}/deployments")
    public R<List<Deployment>> listDeployments(
            @PathVariable String projectId,
            @RequestParam(required = false) String environmentId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(defaultValue = "50") Integer limit,
            Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(deploymentService.listDeployments(projectId, environmentId, releaseId, limit));
    }

    @Operation(summary = "部署详情")
    @GetMapping("/deployments/{id}")
    public R<Deployment> getDeployment(@PathVariable String id, Authentication authentication) {
        Deployment d = deploymentService.getDeployment(id);
        guard.requireView(guard.userId(authentication), d.getProjectId());
        return R.ok(d);
    }

    @Operation(summary = "发起部署（受保护环境进入待审批）")
    @PostMapping("/projects/{projectId}/environments/{environmentId}/deployments")
    public R<Deployment> createDeployment(@PathVariable String projectId,
                                          @PathVariable String environmentId,
                                          @RequestBody Deployment request,
                                          Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_EDIT);
        return R.ok(deploymentService.createDeployment(projectId, environmentId, request, userId));
    }

    @Operation(summary = "审批通过部署")
    @PostMapping("/deployments/{id}/approve")
    public R<Deployment> approve(@PathVariable String id,
                                 @RequestBody(required = false) Map<String, String> body,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        Deployment d = deploymentService.getDeployment(id);
        guard.requireProjectPermission(userId, d.getProjectId(), PermissionCodes.PROJECT_EDIT);
        String note = body != null ? body.get("note") : null;
        return R.ok(deploymentService.approve(id, userId, note));
    }

    @Operation(summary = "驳回部署")
    @PostMapping("/deployments/{id}/reject")
    public R<Deployment> reject(@PathVariable String id,
                                @RequestBody(required = false) Map<String, String> body,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        Deployment d = deploymentService.getDeployment(id);
        guard.requireProjectPermission(userId, d.getProjectId(), PermissionCodes.PROJECT_EDIT);
        String note = body != null ? body.get("note") : null;
        return R.ok(deploymentService.reject(id, userId, note));
    }

    @Operation(summary = "开始部署")
    @PostMapping("/deployments/{id}/start")
    public R<Deployment> start(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Deployment d = deploymentService.getDeployment(id);
        guard.requireProjectPermission(userId, d.getProjectId(), PermissionCodes.PROJECT_EDIT);
        return R.ok(deploymentService.markDeploying(id, userId));
    }

    @Operation(summary = "结束部署（成功 / 失败）")
    @PostMapping("/deployments/{id}/finish")
    public R<Deployment> finish(@PathVariable String id,
                                @RequestParam(defaultValue = "true") boolean success,
                                @RequestBody(required = false) Map<String, String> body,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        Deployment d = deploymentService.getDeployment(id);
        guard.requireProjectPermission(userId, d.getProjectId(), PermissionCodes.PROJECT_EDIT);
        String note = body != null ? body.get("note") : null;
        return R.ok(deploymentService.markFinished(id, success, note, userId));
    }

    @Operation(summary = "回滚到该次部署的版本")
    @PostMapping("/deployments/{id}/rollback")
    public R<Deployment> rollback(@PathVariable String id,
                                  @RequestBody(required = false) Map<String, String> body,
                                  Authentication authentication) {
        String userId = guard.userId(authentication);
        Deployment d = deploymentService.getDeployment(id);
        guard.requireProjectPermission(userId, d.getProjectId(), PermissionCodes.PROJECT_EDIT);
        String note = body != null ? body.get("note") : null;
        return R.ok(deploymentService.rollback(id, userId, note));
    }

    // ==================== DORA ====================

    @Operation(summary = "DORA 四指标")
    @GetMapping("/projects/{projectId}/dora")
    public R<Map<String, Object>> dora(@PathVariable String projectId,
                                       @RequestParam(defaultValue = "30") Integer days,
                                       Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(deploymentService.doraMetrics(projectId, days));
    }

    private DeployEnvironment requireEnv(String id) {
        DeployEnvironment env = environmentMapper.selectById(id);
        if (env == null) {
            throw new com.devflow.common.exception.BusinessException(404, "环境不存在");
        }
        return env;
    }

    private final com.devflow.mapper.DeployEnvironmentMapper environmentMapper;
}
