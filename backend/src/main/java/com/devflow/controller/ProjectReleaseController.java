package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.ProjectRelease;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 版本 / 发布。
 *
 * 读：项目可见；
 * 写：project:edit（OWNER/MANAGER）——发布是对外承诺，不应由普通成员触发。
 */
@Tag(name = "版本发布", description = "版本管理、发布说明生成、标记发布")
@RestController
@RequiredArgsConstructor
public class ProjectReleaseController {

    private final ProjectReleaseService releaseService;
    private final AccessGuard guard;

    @Operation(summary = "项目版本列表")
    @GetMapping("/projects/{projectId}/releases")
    public R<List<ProjectRelease>> list(@PathVariable String projectId, Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(releaseService.list(projectId));
    }

    @Operation(summary = "版本详情")
    @GetMapping("/releases/{id}")
    public R<ProjectRelease> get(@PathVariable String id, Authentication authentication) {
        ProjectRelease release = releaseService.get(id);
        guard.requireView(guard.userId(authentication), release.getProjectId());
        return R.ok(release);
    }

    @Operation(summary = "创建版本")
    @PostMapping("/projects/{projectId}/releases")
    public R<ProjectRelease> create(@PathVariable String projectId,
                                    @RequestBody ProjectRelease release,
                                    Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_EDIT);
        return R.ok(releaseService.create(projectId, release, userId));
    }

    @Operation(summary = "更新版本")
    @PutMapping("/releases/{id}")
    public R<ProjectRelease> update(@PathVariable String id,
                                    @RequestBody ProjectRelease patch,
                                    Authentication authentication) {
        String userId = guard.userId(authentication);
        ProjectRelease release = releaseService.get(id);
        guard.requireProjectPermission(userId, release.getProjectId(), PermissionCodes.PROJECT_EDIT);
        return R.ok(releaseService.update(id, patch, userId));
    }

    @Operation(summary = "删除版本")
    @DeleteMapping("/releases/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        ProjectRelease release = releaseService.get(id);
        guard.requireProjectPermission(userId, release.getProjectId(), PermissionCodes.PROJECT_EDIT);
        releaseService.delete(id, userId);
        return R.ok();
    }

    /** 生成发布说明：默认覆盖（overwrite=true），前端可先提示 */
    @Operation(summary = "生成发布说明（按版本聚合需求/任务/缺陷）")
    @PostMapping("/releases/{id}/notes")
    public R<Map<String, String>> generateNotes(@PathVariable String id,
                                                @RequestParam(defaultValue = "true") boolean overwrite,
                                                Authentication authentication) {
        String userId = guard.userId(authentication);
        ProjectRelease release = releaseService.get(id);
        guard.requireProjectPermission(userId, release.getProjectId(), PermissionCodes.PROJECT_EDIT);
        return R.ok(Map.of("releaseNotes", releaseService.generateReleaseNotes(id, overwrite)));
    }

    @Operation(summary = "标记版本已发布")
    @PostMapping("/releases/{id}/publish")
    public R<ProjectRelease> publish(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        ProjectRelease release = releaseService.get(id);
        guard.requireProjectPermission(userId, release.getProjectId(), PermissionCodes.PROJECT_EDIT);
        return R.ok(releaseService.markReleased(id, userId));
    }

    @Operation(summary = "版本统计（发布前检查）")
    @GetMapping("/releases/{id}/stats")
    public R<Map<String, Object>> stats(@PathVariable String id, Authentication authentication) {
        ProjectRelease release = releaseService.get(id);
        guard.requireView(guard.userId(authentication), release.getProjectId());
        return R.ok(releaseService.stats(id));
    }
}
