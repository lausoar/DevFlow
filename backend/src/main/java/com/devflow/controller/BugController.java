package com.devflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.dto.request.CreateBugRequest;
import com.devflow.dto.request.UpdateBugRequest;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.entity.Bug;
import com.devflow.service.BugService;
import com.devflow.service.PermissionService;
import com.devflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Bug 控制器
 * 业务逻辑委托给 BugService，Service 内部自动生成通知。
 *
 * 鉴权：读写都以项目可见性为门槛，写操作叠加 bug:create/edit/delete 权限点；
 * 状态变更走资源关系（仅负责人/报告人）。请求体为白名单 DTO，
 * 归属字段（projectId/reporterId/reporterType/pending）不可由客户端改写。
 */
@Tag(name = "Bug 管理", description = "Bug 的增删改查")
@RestController
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;
    private final PermissionService permissionService;
    private final com.devflow.service.GuestService guestService;
    private final ProjectAccessService projectAccessService;
    private final AccessGuard guard;
    private final WorkflowService workflowService;

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
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(bugService.listByProject(projectId, page, pageSize, keyword, status));
    }

    /** 我的 Bug（跨项目，按严重程度排序） */
    @Operation(summary = "获取我的 Bug（跨项目）")
    @GetMapping("/bugs/my")
    public R<java.util.List<Bug>> myBugs(Authentication authentication) {
        String userId = guard.userId(authentication);
        return R.ok(bugService.listMyBugs(userId));
    }

    /** 获取 Bug 详情 */
    @Operation(summary = "获取 Bug 详情")
    @GetMapping("/bugs/{id}")
    public R<Bug> getById(@PathVariable String id, Authentication authentication) {
        return R.ok(requireBug(guard.userId(authentication), id));
    }

    /** 创建 Bug（仅项目测试人员） */
    @Operation(summary = "创建 Bug（仅测试人员）")
    @PostMapping("/projects/{projectId}/bugs")
    public R<Bug> create(@PathVariable String projectId,
                         @Valid @RequestBody CreateBugRequest request,
                         Authentication authentication) {
        String reporterId = guard.userId(authentication);
        guard.requireProjectPermission(reporterId, projectId, PermissionCodes.BUG_CREATE);

        Bug bug = new Bug();
        bug.setTitle(request.getTitle());
        bug.setDescription(request.getDescription());
        bug.setAssigneeId(request.getAssigneeId());
        bug.setPriority(request.getPriority() == null ? "MEDIUM" : request.getPriority());
        bug.setSeverity(request.getSeverity() == null ? "MAJOR" : request.getSeverity());
        bug.setEnvironment(request.getEnvironment());
        bug.setScreenshots(request.getScreenshots());
        bug.setFixVersionId(request.getFixVersionId());
        return R.ok(bugService.create(projectId, bug, reporterId));
    }

    /** 更新 Bug（仅项目测试人员；字段白名单） */
    @Operation(summary = "更新 Bug（仅测试人员）")
    @PutMapping("/bugs/{id}")
    public R<Bug> update(@PathVariable String id,
                         @Valid @RequestBody UpdateBugRequest request,
                         Authentication authentication) {
        String userId = guard.userId(authentication);
        Bug existing = requireBug(userId, id);
        requireBugPermission(existing.getProjectId(), userId, PermissionCodes.BUG_EDIT);

        Bug patch = new Bug();
        patch.setId(id);
        patch.setTitle(request.getTitle());
        patch.setDescription(request.getDescription());
        patch.setAssigneeId(request.getAssigneeId());
        patch.setPriority(request.getPriority());
        patch.setSeverity(request.getSeverity());
        patch.setEnvironment(request.getEnvironment());
        patch.setScreenshots(request.getScreenshots());
        patch.setFixVersionId(request.getFixVersionId());

        Bug updated = bugService.update(id, patch, request.getExpectedVersion());
        if (updated == null) {
            throw new BusinessException(404, "Bug 不存在或已被删除");
        }
        return R.ok(updated);
    }

    /** 变更 Bug 状态（仅负责人或报告人） */
    @Operation(summary = "变更 Bug 状态（仅负责人/报告人）")
    @PutMapping("/bugs/{id}/status")
    public R<Bug> changeStatus(@PathVariable String id,
                               @RequestBody java.util.Map<String, String> body,
                               Authentication authentication) {
        String status = body.get("status");
        String userId = guard.userId(authentication);
        Bug bug = requireBug(userId, id);
        // 状态取值与流转由项目工作流决定
        workflowService.assertTransitionAllowed(bug.getProjectId(), WorkflowService.TYPE_BUG,
                bug.getStatus(), status);
        boolean isAssignee = userId.equals(bug.getAssigneeId());
        boolean isReporter = userId.equals(bug.getReporterId());
        if (!isAssignee && !isReporter) {
            throw new BusinessException(403, "仅 Bug 负责人或创建人可变更状态");
        }
        Bug updated = bugService.changeStatus(id, status, userId);
        return R.ok(updated);
    }

    /** 确认游客提交的 Bug（仅测试人员及以上） */
    @Operation(summary = "确认游客提交的 Bug")
    @PostMapping("/bugs/{id}/confirm")
    public R<Void> confirmGuestBug(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Bug existing = requireBug(userId, id);
        requireBugPermission(existing.getProjectId(), userId, PermissionCodes.BUG_EDIT);
        guestService.confirmGuestBug(id, userId);
        return R.ok();
    }

    /** 删除 Bug（仅项目测试人员） */
    @Operation(summary = "删除 Bug（逻辑删除，仅测试人员）")
    @DeleteMapping("/bugs/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Bug existing = requireBug(userId, id);
        requireBugPermission(existing.getProjectId(), userId, PermissionCodes.BUG_DELETE);
        bugService.delete(id);
        return R.ok();
    }

    // ============================================
    // 权限
    // ============================================

    /** Bug 必须存在且所属项目对当前用户可见，否则 404（不泄露存在性） */
    private Bug requireBug(String userId, String bugId) {
        Bug bug = bugService.getById(bugId);
        if (bug == null || !projectAccessService.canView(userId, bug.getProjectId())) {
            throw new BusinessException(404, "Bug 不存在或已被删除");
        }
        return bug;
    }

    /** 校验操作者在项目上拥有指定 Bug 权限点（角色映射见 sys_role_permission），否则拒绝 */
    private void requireBugPermission(String projectId, String userId, String permissionCode) {
        if (!permissionService.hasProjectPermission(userId, projectId, permissionCode)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可新建或修改 Bug");
        }
    }
}
