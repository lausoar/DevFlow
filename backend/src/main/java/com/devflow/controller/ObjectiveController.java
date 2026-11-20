package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.Objective;
import com.devflow.security.AccessGuard;
import com.devflow.service.ObjectiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企业目标（OKR）：读 = 企业可见，写 = enterprise:manage。
 * 与项目集同口径，不新增企业权限码（新权限码不会自动进入既有企业的权限快照）。
 */
@Tag(name = "企业目标", description = "OKR 目标、项目对齐与投入产出汇总")
@RestController
@RequiredArgsConstructor
public class ObjectiveController {

    private final ObjectiveService objectiveService;
    private final AccessGuard guard;

    @Operation(summary = "企业下的目标列表")
    @GetMapping("/enterprises/{enterpriseId}/objectives")
    public R<List<Objective>> list(@PathVariable String enterpriseId, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, enterpriseId);
        return R.ok(objectiveService.listByEnterprise(enterpriseId));
    }

    @Operation(summary = "目标详情与投入产出汇总")
    @GetMapping("/objectives/{id}")
    public R<Map<String, Object>> rollup(@PathVariable String id, Authentication authentication) {
        Objective objective = objectiveService.get(id);
        guard.requireEnterpriseMember(guard.userId(authentication), objective.getEnterpriseId());
        return R.ok(objectiveService.rollup(id));
    }

    @Operation(summary = "创建目标")
    @PostMapping("/enterprises/{enterpriseId}/objectives")
    public R<Objective> create(@PathVariable String enterpriseId,
                               @RequestBody Objective objective,
                               Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, enterpriseId, PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(objectiveService.create(enterpriseId, objective, userId));
    }

    @Operation(summary = "更新目标")
    @PutMapping("/objectives/{id}")
    public R<Objective> update(@PathVariable String id,
                               @RequestBody Objective patch,
                               Authentication authentication) {
        String userId = guard.userId(authentication);
        Objective objective = objectiveService.get(id);
        guard.requireEnterprisePermission(userId, objective.getEnterpriseId(), PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(objectiveService.update(id, patch, userId));
    }

    @Operation(summary = "删除目标（项目与需求会被移出）")
    @DeleteMapping("/objectives/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Objective objective = objectiveService.get(id);
        guard.requireEnterprisePermission(userId, objective.getEnterpriseId(), PermissionCodes.ENTERPRISE_MANAGE);
        objectiveService.delete(id, userId);
        return R.ok();
    }

    @Data
    public static class AssignProjectRequest {
        /** 为空表示把项目移出目标 */
        private String objectiveId;
    }

    @Operation(summary = "把项目挂到目标 / 移出目标")
    @PutMapping("/projects/{projectId}/objective")
    public R<Void> assignProject(@PathVariable String projectId,
                                 @RequestBody AssignProjectRequest request,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_MANAGE);
        objectiveService.assignProject(request.getObjectiveId(), projectId, userId);
        return R.ok();
    }
}
