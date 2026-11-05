package com.devflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.entity.Task;
import com.devflow.entity.TaskDependency;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务控制器
 * 业务逻辑委托给 TaskService，Service 内部自动生成通知
 */
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
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return R.ok(taskService.listByProject(projectId, page, pageSize, keyword, status));
    }

    /** 按 Sprint 获取任务列表 */
    @Operation(summary = "按 Sprint 获取任务")
    @GetMapping("/sprints/{sprintId}/tasks")
    public R<List<Task>> listBySprint(@PathVariable String sprintId) {
        return R.ok(taskService.listBySprint(sprintId));
    }

    /** 我的任务（跨项目，按截止时间排序） */
    @Operation(summary = "获取我的任务（跨项目）")
    @GetMapping("/tasks/my")
    public R<List<Task>> myTasks(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(taskService.listMyTasks(userId));
    }

    /** 获取任务详情 */
    @Operation(summary = "获取任务详情")
    @GetMapping("/tasks/{id}")
    public R<Task> getById(@PathVariable String id, Authentication authentication) {
        Task task = taskMapper.selectById(id);
        if (task == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), task.getProjectId())) {
            throw new com.devflow.common.exception.BusinessException(404, "任务不存在或已被删除");
        }
        return R.ok(task);
    }

    /** 创建任务 */
    @Operation(summary = "创建任务")
    @PostMapping("/projects/{projectId}/tasks")
    public R<Task> create(@PathVariable String projectId,
                          @RequestBody Task task,
                          Authentication authentication) {
        String reporterId = (String) authentication.getPrincipal();
        if (task.getPriority() == null) {
            task.setPriority("MEDIUM");
        }
        return R.ok(taskService.create(projectId, task, reporterId));
    }

    /** 更新任务 */
    @Operation(summary = "更新任务")
    @PutMapping("/tasks/{id}")
    public R<Task> update(@PathVariable String id, @RequestBody Task task) {
        task.setId(id);
        Task updated = taskService.update(id, task);
        return updated != null ? R.ok(updated) : R.fail("任务不存在");
    }

    /** 更新任务状态 */
    @Operation(summary = "更新任务状态")
    @PutMapping("/tasks/{id}/status")
    public R<Void> updateStatus(@PathVariable String id,
                                @RequestBody Map<String, String> body) {
        if (body.containsKey("status")) {
            taskService.updateStatus(id, body.get("status"));

            // 自动化规则：TASK_DONE 触发
            try {
                Task doneTask = taskMapper.selectById(id);
                if (doneTask != null) {
                    automationRuleService.fire("TASK_DONE", doneTask.getProjectId(),
                            id, doneTask.getTitle(), null);
                }
            } catch (Exception ignored) {
            }

            // 前置任务完成时，通知依赖它的任务负责人可以开工
            if ("DONE".equals(body.get("status"))) {
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
                } catch (Exception ignored) {
                    // 通知失败不影响状态变更
                }
            }
        }
        return R.ok();
    }

    /** 删除任务 */
    @Operation(summary = "删除任务（逻辑删除）")
    @DeleteMapping("/tasks/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        Task task = taskMapper.selectById(id);
        if (task == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), task.getProjectId())) {
            throw new com.devflow.common.exception.BusinessException(404, "任务不存在或已被删除");
        }
        taskService.delete(id);
        return R.ok();
    }
}
