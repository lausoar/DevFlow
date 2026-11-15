package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.Program;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目集：跨项目汇报视角。
 * 读 = 企业可见；写 = enterprise:manage（项目集是管理层的组织工具）。
 */
@Tag(name = "项目集", description = "项目集管理、跨项目概览、项目归集")
@RestController
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;
    private final AccessGuard guard;

    @Operation(summary = "企业下的项目集列表")
    @GetMapping("/enterprises/{enterpriseId}/programs")
    public R<List<Program>> list(@PathVariable String enterpriseId, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, enterpriseId);
        return R.ok(programService.listByEnterprise(enterpriseId));
    }

    @Operation(summary = "项目集概览（含项目进度汇总）")
    @GetMapping("/programs/{id}")
    public R<Map<String, Object>> overview(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Program program = programService.get(id);
        guard.requireEnterpriseMember(userId, program.getEnterpriseId());
        return R.ok(programService.overview(id));
    }

    @Operation(summary = "创建项目集")
    @PostMapping("/enterprises/{enterpriseId}/programs")
    public R<Program> create(@PathVariable String enterpriseId,
                             @RequestBody Program program,
                             Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, enterpriseId, PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(programService.create(enterpriseId, program, userId));
    }

    @Operation(summary = "更新项目集")
    @PutMapping("/programs/{id}")
    public R<Program> update(@PathVariable String id,
                             @RequestBody Program patch,
                             Authentication authentication) {
        String userId = guard.userId(authentication);
        Program program = programService.get(id);
        guard.requireEnterprisePermission(userId, program.getEnterpriseId(), PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(programService.update(id, patch, userId));
    }

    @Operation(summary = "删除项目集（项目会被移出）")
    @DeleteMapping("/programs/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Program program = programService.get(id);
        guard.requireEnterprisePermission(userId, program.getEnterpriseId(), PermissionCodes.ENTERPRISE_MANAGE);
        programService.delete(id, userId);
        return R.ok();
    }

    @Data
    public static class AssignProjectRequest {
        /** 为空表示把项目移出项目集 */
        private String programId;
    }

    @Operation(summary = "把项目移入/移出项目集")
    @PutMapping("/projects/{projectId}/program")
    public R<Void> assignProject(@PathVariable String projectId,
                                 @RequestBody AssignProjectRequest request,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_MANAGE);
        programService.assignProject(request.getProgramId(), projectId, userId);
        return R.ok();
    }
}
