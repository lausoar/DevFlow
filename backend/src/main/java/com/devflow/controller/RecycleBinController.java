package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.AuditService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回收站：项目内已删除任务/Bug/需求的查看与恢复
 */
@Tag(name = "回收站", description = "已删除任务/Bug/需求的查看与恢复")
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectMapper projectMapper;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;

    /** 回收站列表 */
    @Operation(summary = "回收站列表（项目内已删除任务/Bug/需求）")
    @GetMapping("/projects/{projectId}/recycle-bin")
    public R<Map<String, Object>> list(@PathVariable String projectId, Authentication authentication) {
        requireVisibleProject(projectId, authentication);
        return R.ok(Map.of(
                "tasks", taskMapper.selectDeletedByProject(projectId),
                "bugs", bugMapper.selectDeletedByProject(projectId),
                "requirements", requirementMapper.selectDeletedByProject(projectId)
        ));
    }

    @Operation(summary = "恢复已删除任务")
    @PostMapping("/recycle-bin/tasks/{id}/restore")
    public R<Void> restoreTask(@PathVariable String id, Authentication authentication) {
        Task task = taskMapper.selectByIdIgnoreDeleted(id);
        if (task == null || task.getDeleted() == null || task.getDeleted() != 1) {
            throw new BusinessException(404, "回收站中不存在该任务");
        }
        Project project = requireVisibleProject(task.getProjectId(), authentication);
        taskMapper.restoreById(id);
        audit(project, "task.restore", "task", id, "恢复任务「" + task.getTitle() + "」", authentication);
        return R.ok();
    }

    @Operation(summary = "恢复已删除 Bug")
    @PostMapping("/recycle-bin/bugs/{id}/restore")
    public R<Void> restoreBug(@PathVariable String id, Authentication authentication) {
        Bug bug = bugMapper.selectByIdIgnoreDeleted(id);
        if (bug == null || bug.getDeleted() == null || bug.getDeleted() != 1) {
            throw new BusinessException(404, "回收站中不存在该 Bug");
        }
        Project project = requireVisibleProject(bug.getProjectId(), authentication);
        bugMapper.restoreById(id);
        audit(project, "bug.restore", "bug", id, "恢复 Bug「" + bug.getTitle() + "」", authentication);
        return R.ok();
    }

    @Operation(summary = "恢复已删除需求")
    @PostMapping("/recycle-bin/requirements/{id}/restore")
    public R<Void> restoreRequirement(@PathVariable String id, Authentication authentication) {
        Requirement requirement = requirementMapper.selectByIdIgnoreDeleted(id);
        if (requirement == null || requirement.getDeleted() == null || requirement.getDeleted() != 1) {
            throw new BusinessException(404, "回收站中不存在该需求");
        }
        Project project = requireVisibleProject(requirement.getProjectId(), authentication);
        requirementMapper.restoreById(id);
        audit(project, "requirement.restore", "requirement", id, "恢复需求「" + requirement.getTitle() + "」", authentication);
        return R.ok();
    }

    /** 不可见时与"不存在"同响应，不泄露存在性 */
    private Project requireVisibleProject(String projectId, Authentication authentication) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return project;
    }

    private void audit(Project project, String action, String targetType, String targetId, String detail, Authentication authentication) {
        try {
            auditService.log((String) authentication.getPrincipal(), project.getEnterpriseId(),
                    project.getId(), action, targetType, targetId, detail);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }
}
