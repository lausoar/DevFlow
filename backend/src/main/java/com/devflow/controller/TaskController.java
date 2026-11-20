package com.devflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.dto.request.CreateTaskRequest;
import com.devflow.dto.request.UpdateTaskRequest;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.entity.Sprint;
import com.devflow.entity.WorkflowStatus;
import com.devflow.entity.Task;
import com.devflow.entity.TaskDependency;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.TaskService;
import com.devflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务控制器
 * 业务逻辑委托给 TaskService，Service 内部自动生成通知。
 *
 * 鉴权：所有读写都以"任务所属项目可见 + task:manage 权限点"为门槛，
 * 状态变更额外受白名单约束。
 */
@Slf4j
@Tag(name = "任务管理", description = "任务的增删改查、状态变更")
@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final com.devflow.mapper.TaskMapper taskMapper;
    private final ProjectAccessService projectAccessService;
    private final com.devflow.mapper.TaskDependencyMapper taskDependencyMapper;
    private final com.devflow.service.AutomationRuleService automationRuleService;
    private final com.devflow.service.NotificationService notificationService;
    private final SprintMapper sprintMapper;
    private final AccessGuard guard;
    private final WorkflowService workflowService;
    private final com.devflow.mapper.ProjectMapper projectMapper;

    /** 按项目获取任务列表（分页） */
    @Operation(summary = "获取项目的任务列表")
    @GetMapping("/projects/{projectId}/tasks")
    public R<Page<Task>> listByProject(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(taskService.listByProject(projectId, page, pageSize, keyword, status));
    }

    /** 按 Sprint 获取任务列表（校验迭代所属项目可见性） */
    @Operation(summary = "按 Sprint 获取任务")
    @GetMapping("/sprints/{sprintId}/tasks")
    public R<List<Task>> listBySprint(@PathVariable String sprintId, Authentication authentication) {
        String userId = guard.userId(authentication);
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) {
            throw new BusinessException(404, "迭代不存在");
        }
        guard.requireView(userId, sprint.getProjectId());
        return R.ok(taskService.listBySprint(sprintId));
    }

    /** 我的任务（跨项目，按截止时间排序） */
    @Operation(summary = "获取我的任务（跨项目）")
    @GetMapping("/tasks/my")
    public R<List<Task>> myTasks(Authentication authentication) {
        String userId = guard.userId(authentication);
        return R.ok(taskService.listMyTasks(userId));
    }

    /** 获取任务详情 */
    @Operation(summary = "获取任务详情")
    @GetMapping("/tasks/{id}")
    public R<Task> getById(@PathVariable String id, Authentication authentication) {
        return R.ok(requireTask(guard.userId(authentication), id).task());
    }

    /** 创建任务（需项目可见 + task:manage） */
    @Operation(summary = "创建任务")
    @PostMapping("/projects/{projectId}/tasks")
    public R<Task> create(@PathVariable String projectId,
                          @Valid @RequestBody CreateTaskRequest request,
                          Authentication authentication) {
        String reporterId = guard.userId(authentication);
        guard.requireProjectPermission(reporterId, projectId, PermissionCodes.TASK_MANAGE);

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setSprintId(request.getSprintId());
        task.setAssigneeId(request.getAssigneeId());
        task.setPriority(request.getPriority() == null ? "MEDIUM" : request.getPriority());
        task.setStoryPoints(request.getStoryPoints());
        task.setDueDate(request.getDueDate());
        task.setFixVersionId(request.getFixVersionId());
        // 初始状态由工作流给定，而不是写死 TODO
        task.setStatus(workflowService.initialStatus(projectId, WorkflowService.TYPE_TASK));
        return R.ok(taskService.create(projectId, task, reporterId));
    }

    /** 更新任务（字段白名单，不能改状态/归属） */
    @Operation(summary = "更新任务")
    @PutMapping("/tasks/{id}")
    public R<Task> update(@PathVariable String id,
                          @Valid @RequestBody UpdateTaskRequest request,
                          Authentication authentication) {
        TaskRef ref = requireTask(guard.userId(authentication), id);
        guard.requireProjectPermission(ref.userId(), ref.task().getProjectId(), PermissionCodes.TASK_MANAGE);

        Task patch = new Task();
        patch.setId(id);
        patch.setTitle(request.getTitle());
        patch.setDescription(request.getDescription());
        patch.setSprintId(request.getSprintId());
        patch.setAssigneeId(request.getAssigneeId());
        patch.setPriority(request.getPriority());
        patch.setStoryPoints(request.getStoryPoints());
        patch.setDueDate(request.getDueDate());
        patch.setFixVersionId(request.getFixVersionId());

        Task updated = taskService.update(id, patch, request.getExpectedVersion());
        if (updated == null) {
            throw new BusinessException(404, "任务不存在或已被删除");
        }
        return R.ok(updated);
    }

    /** 更新任务状态（仅 TODO/DOING/TESTING/DONE；只有真正流转到 DONE 才触发完成规则） */
    @Operation(summary = "更新任务状态")
    @PutMapping("/tasks/{id}/status")
    @org.springframework.transaction.annotation.Transactional
    public R<Void> updateStatus(@PathVariable String id,
                                @RequestBody Map<String, String> body,
                                Authentication authentication) {
        String status = body.get("status");
        TaskRef ref = requireTask(guard.userId(authentication), id);
        String projectId = ref.task().getProjectId();
        guard.requireProjectPermission(ref.userId(), projectId, PermissionCodes.TASK_MANAGE);
        if (projectMapper.selectForUpdate(projectId) == null) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }

        String from = ref.task().getStatus();
        // 状态取值与流转合法性都由项目工作流决定（不再硬编码四态白名单）
        workflowService.assertTransitionAllowed(projectId, WorkflowService.TYPE_TASK, from, status);

        // "完成"按 status_category 判定：自定义的任意终态标签都算完成
        String doneCategory = WorkflowService.TYPE_TASK;
        boolean becomingDone = WorkflowStatus.CATEGORY_DONE.equals(
                workflowService.categoryOf(projectId, doneCategory, status))
                && !WorkflowStatus.CATEGORY_DONE.equals(workflowService.categoryOf(projectId, doneCategory, from));
        taskService.updateStatus(id, status, ref.userId());

        if (becomingDone) {
            // 自动化规则：任务完成（仅在状态真正变为 DONE 时触发）
            try {
                Task doneTask = taskMapper.selectById(id);
                if (doneTask != null) {
                    automationRuleService.fire("TASK_DONE", doneTask.getProjectId(),
                            id, doneTask.getTitle(), null);
                }
            } catch (Exception e) {
                log.warn("TASK_DONE 自动化规则执行失败: taskId={}, 原因={}", id, e.getMessage());
            }

            // 前置任务完成时，通知依赖它的任务负责人可以开工
            try {
                Task done = taskService.getById(id);
                if (done != null) {
                    for (TaskDependency dep : taskDependencyMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskDependency>()
                                    .eq(TaskDependency::getDependsOnTaskId, id))) {
                        Task next = taskService.getById(dep.getTaskId());
                        if (next != null && next.getAssigneeId() != null
                                && !next.getAssigneeId().equals(done.getAssigneeId())) {
                            notificationService.createNotification(
                                    next.getAssigneeId(), "TASK_UPDATE",
                                    "前置任务已完成，可以开工",
                                    "「" + done.getTitle() + "」已完成，依赖它的「" + next.getTitle() + "」可以开始了",
                                    "/projects/" + next.getProjectId() + "?tab=tasks");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("前置任务完成通知发送失败: taskId={}, 原因={}", id, e.getMessage());
            }
        }
        return R.ok();
    }

    /** 删除任务（需项目可见 + task:manage） */
    @Operation(summary = "删除任务（逻辑删除）")
    @DeleteMapping("/tasks/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        TaskRef ref = requireTask(guard.userId(authentication), id);
        guard.requireProjectPermission(ref.userId(), ref.task().getProjectId(), PermissionCodes.TASK_MANAGE);
        taskService.delete(id, ref.userId());
        return R.ok();
    }

    // ============================================
    // 内部
    // ============================================

    /** 任务 + 当前用户，任务不可见时 404（不泄露存在性） */
    private record TaskRef(String userId, Task task) {
    }

    private TaskRef requireTask(String userId, String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !projectAccessService.canView(userId, task.getProjectId())) {
            throw new BusinessException(404, "任务不存在或已被删除");
        }
        return new TaskRef(userId, task);
    }
}
