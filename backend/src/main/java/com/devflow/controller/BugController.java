package com.devflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.entity.Bug;
import com.devflow.service.BugService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Bug 控制器
 * 业务逻辑委托给 BugService，Service 内部自动生成通知
 */
@Tag(name = "Bug 管理", description = "Bug 的增删改查")
@RestController
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;
    private final PermissionService permissionService;
    private final com.devflow.service.GuestService guestService;
    private final ProjectAccessService projectAccessService;

    /** 按项目获取 Bug 列表（分页） */
    @Operation(summary = "获取项目的 Bug 列表")
    @GetMapping("/projects/{projectId}/bugs")
    public R<Page<Bug>> listByProject(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return R.ok(bugService.listByProject(projectId, page, pageSize, keyword, status));
    }

    /** 我的 Bug（跨项目，按严重程度排序） */
    @Operation(summary = "获取我的 Bug（跨项目）")
    @GetMapping("/bugs/my")
    public R<java.util.List<Bug>> myBugs(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(bugService.listMyBugs(userId));
    }

    /** 获取 Bug 详情 */
    @Operation(summary = "获取 Bug 详情")
    @GetMapping("/bugs/{id}")
    public R<Bug> getById(@PathVariable String id, Authentication authentication) {
        Bug bugGate = bugService.getById(id);
        if (bugGate == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), bugGate.getProjectId())) {
            throw new com.devflow.common.exception.BusinessException(404, "Bug 不存在或已被删除");
        }
        return R.ok(bugService.getById(id));
    }

    /** 创建 Bug（仅项目测试人员） */
    @Operation(summary = "创建 Bug（仅测试人员）")
    @PostMapping("/projects/{projectId}/bugs")
    public R<Bug> create(@PathVariable String projectId,
                         @RequestBody Bug bug,
                         Authentication authentication) {
        String reporterId = (String) authentication.getPrincipal();
        requireBugPermission(projectId, reporterId, PermissionCodes.BUG_CREATE);
        if (bug.getPriority() == null) {
            bug.setPriority("MEDIUM");
        }
        if (bug.getSeverity() == null) {
            bug.setSeverity("MAJOR");
        }
        return R.ok(bugService.create(projectId, bug, reporterId));
    }

    /** 更新 Bug（仅项目测试人员） */
    @Operation(summary = "更新 Bug（仅测试人员）")
    @PutMapping("/bugs/{id}")
    public R<Bug> update(@PathVariable String id,
                         @RequestBody Bug bug,
                         Authentication authentication) {
        Bug existing = bugService.getById(id);
        if (existing == null) {
            return R.fail("Bug 不存在");
        }
        requireBugPermission(existing.getProjectId(), (String) authentication.getPrincipal(), PermissionCodes.BUG_EDIT);
        bug.setId(id);
        Bug updated = bugService.update(id, bug);
        return updated != null ? R.ok(updated) : R.fail("Bug 不存在");
    }

    /** 变更 Bug 状态（仅负责人或报告人） */
    @Operation(summary = "变更 Bug 状态（仅负责人/报告人）")
    @PutMapping("/bugs/{id}/status")
    public R<Bug> changeStatus(@PathVariable String id,
                               @RequestBody java.util.Map<String, String> body,
                               Authentication authentication) {
        String status = body.get("status");
        if (!ALLOWED_STATUS.contains(status)) {
            return R.fail(400, "无效的 Bug 状态");
        }
        Bug bug = bugService.getById(id);
        if (bug == null) {
            return R.fail("Bug 不存在");
        }
        String userId = (String) authentication.getPrincipal();
        boolean isAssignee = userId.equals(bug.getAssigneeId());
        boolean isReporter = userId.equals(bug.getReporterId());
        if (!isAssignee && !isReporter) {
            return R.forbidden("仅 Bug 负责人或创建人可变更状态");
        }
        Bug updated = bugService.changeStatus(id, status, userId);
        return updated != null ? R.ok(updated) : R.fail("Bug 不存在");
    }

    /** 确认游客提交的 Bug（仅测试人员及以上） */
    @Operation(summary = "确认游客提交的 Bug")
    @PostMapping("/bugs/{id}/confirm")
    public R<Void> confirmGuestBug(@PathVariable String id, Authentication authentication) {
        Bug existing = bugService.getById(id);
        if (existing == null) {
            return R.fail("Bug 不存在");
        }
        requireBugPermission(existing.getProjectId(), (String) authentication.getPrincipal(), PermissionCodes.BUG_EDIT);
        guestService.confirmGuestBug(id, (String) authentication.getPrincipal());
        return R.ok();
    }

    /** 删除 Bug（仅项目测试人员） */
    @Operation(summary = "删除 Bug（逻辑删除，仅测试人员）")
    @DeleteMapping("/bugs/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        Bug existing = bugService.getById(id);
        if (existing != null) {
            requireBugPermission(existing.getProjectId(), (String) authentication.getPrincipal(), PermissionCodes.BUG_DELETE);
        }
        bugService.delete(id);
        return R.ok();
    }

    // ============================================
    // 权限
    // ============================================

    /** 允许的 Bug 状态 */
    private static final java.util.Set<String> ALLOWED_STATUS = java.util.Set.of(
            "NEW", "ASSIGNED", "DOING", "RESOLVED", "REOPENED", "CLOSED");

    /** 校验操作者在项目上拥有指定 Bug 权限点（角色映射见 sys_role_permission），否则拒绝 */
    private void requireBugPermission(String projectId, String userId, String permissionCode) {
        if (!permissionService.hasProjectPermission(userId, projectId, permissionCode)) {
            throw new com.devflow.common.exception.BusinessException(403, "仅测试人员或项目管理员可新建或修改 Bug");
        }
    }
}
