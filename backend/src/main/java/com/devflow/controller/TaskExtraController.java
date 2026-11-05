package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Task;
import com.devflow.entity.User;
import com.devflow.entity.TaskChecklistItem;
import com.devflow.entity.TaskDependency;
import com.devflow.mapper.TaskChecklistMapper;
import com.devflow.mapper.TaskDependencyMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.NotificationService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务清单（子任务）与任务依赖。
 * 读写都以任务所在项目的可见性为门槛（项目成员可协作编辑）。
 */
@Tag(name = "任务清单与依赖", description = "子任务清单、任务依赖关系")
@RestController
@RequiredArgsConstructor
public class TaskExtraController {

    private final TaskChecklistMapper checklistMapper;
    private final com.devflow.mapper.TaskWorklogMapper worklogMapper;
    private final com.devflow.service.ImPushService imPushService;
    private final com.devflow.mapper.UserMapper userMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final TaskMapper taskMapper;
    private final ProjectAccessService projectAccessService;
    private final NotificationService notificationService;

    // ==================== 清单（子任务） ====================

    @Operation(summary = "获取任务清单")
    @GetMapping("/tasks/{taskId}/checklist")
    public R<List<TaskChecklistItem>> checklist(@PathVariable String taskId, Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        return R.ok(checklistMapper.selectList(new LambdaQueryWrapper<TaskChecklistItem>()
                .eq(TaskChecklistItem::getTaskId, taskId)
                .orderByAsc(TaskChecklistItem::getCreatedAt)));
    }

    @Operation(summary = "添加清单项")
    @PostMapping("/tasks/{taskId}/checklist")
    public R<TaskChecklistItem> addChecklistItem(@PathVariable String taskId,
                                                 @RequestBody Map<String, String> body,
                                                 Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) {
            throw new BusinessException(400, "清单内容不能为空");
        }
        if (title.length() > 200) {
            throw new BusinessException(400, "清单内容不能超过 200 字");
        }
        TaskChecklistItem item = new TaskChecklistItem();
        item.setTaskId(taskId);
        item.setTitle(title);
        item.setDone(0);
        checklistMapper.insert(item);
        return R.ok(item);
    }

    @Operation(summary = "更新清单项（勾选完成/改名）")
    @PutMapping("/tasks/{taskId}/checklist/{itemId}")
    public R<TaskChecklistItem> updateChecklistItem(@PathVariable String taskId,
                                                    @PathVariable String itemId,
                                                    @RequestBody Map<String, Object> body,
                                                    Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        TaskChecklistItem item = checklistMapper.selectById(itemId);
        if (item == null || !item.getTaskId().equals(taskId)) {
            throw new BusinessException(404, "清单项不存在");
        }
        if (body.containsKey("title")) {
            String title = String.valueOf(body.getOrDefault("title", "")).trim();
            if (title.isEmpty()) {
                throw new BusinessException(400, "清单内容不能为空");
            }
            item.setTitle(title);
        }
        if (body.containsKey("done")) {
            item.setDone(Boolean.TRUE.equals(body.get("done")) || Integer.valueOf(1).equals(body.get("done")) ? 1 : 0);
        }
        checklistMapper.updateById(item);
        return R.ok(item);
    }

    @Operation(summary = "删除清单项")
    @DeleteMapping("/tasks/{taskId}/checklist/{itemId}")
    public R<Void> deleteChecklistItem(@PathVariable String taskId,
                                       @PathVariable String itemId,
                                       Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        TaskChecklistItem item = checklistMapper.selectById(itemId);
        if (item == null || !item.getTaskId().equals(taskId)) {
            throw new BusinessException(404, "清单项不存在");
        }
        checklistMapper.deleteById(itemId);
        return R.ok();
    }

    // ==================== 工时登记 ====================

    /** 工时列表（含合计） */
    @Operation(summary = "获取任务工时列表")
    @GetMapping("/tasks/{taskId}/worklogs")
    public R<Map<String, Object>> worklogs(@PathVariable String taskId, Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        List<com.devflow.entity.TaskWorklog> logs = worklogMapper.selectByTask(taskId);
        java.math.BigDecimal total = logs.stream()
                .map(com.devflow.entity.TaskWorklog::getHours)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("totalHours", total);
        return R.ok(result);
    }

    @Operation(summary = "登记工时")
    @PostMapping("/tasks/{taskId}/worklogs")
    public R<Map<String, Object>> addWorklog(@PathVariable String taskId,
                                             @RequestBody Map<String, Object> body,
                                             Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        String userId = (String) authentication.getPrincipal();

        java.math.BigDecimal hours;
        try {
            hours = new java.math.BigDecimal(String.valueOf(body.getOrDefault("hours", "0")));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "工时格式不正确");
        }
        if (hours.compareTo(java.math.BigDecimal.ZERO) <= 0 || hours.compareTo(new java.math.BigDecimal("24")) > 0) {
            throw new BusinessException(400, "工时需在 0.1 ~ 24 小时之间");
        }

        com.devflow.entity.TaskWorklog log = new com.devflow.entity.TaskWorklog();
        log.setTaskId(taskId);
        log.setUserId(userId);
        log.setHours(hours);
        Object date = body.get("workDate");
        try {
            log.setWorkDate(date == null || String.valueOf(date).isBlank()
                    ? java.time.LocalDate.now()
                    : java.time.LocalDate.parse(String.valueOf(date)));
        } catch (Exception e) {
            throw new BusinessException(400, "日期格式不正确");
        }
        Object note = body.get("note");
        if (note != null && !String.valueOf(note).isBlank()) {
            log.setNote(String.valueOf(note).trim());
        }
        worklogMapper.insert(log);

        List<com.devflow.entity.TaskWorklog> logs = worklogMapper.selectByTask(taskId);
        java.math.BigDecimal total = logs.stream()
                .map(com.devflow.entity.TaskWorklog::getHours)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<String, Object> result = new HashMap<>();
        result.put("log", log);
        result.put("totalHours", total);
        return R.ok(result);
    }

    @Operation(summary = "删除自己的工时记录")
    @DeleteMapping("/tasks/{taskId}/worklogs/{logId}")
    public R<Void> deleteWorklog(@PathVariable String taskId,
                                 @PathVariable String logId,
                                 Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        com.devflow.entity.TaskWorklog log = worklogMapper.selectById(logId);
        if (log == null || !log.getTaskId().equals(taskId)) {
            throw new BusinessException(404, "工时记录不存在");
        }
        String userId = (String) authentication.getPrincipal();
        User operator = null;
        // 仅本人或企业管理员以上可删
        if (!log.getUserId().equals(userId)) {
            operator = userMapper.selectById(userId);
            if (operator == null || com.devflow.common.EnterpriseRoles.level(operator.getRole())
                    < com.devflow.common.EnterpriseRoles.level(com.devflow.common.EnterpriseRoles.ADMIN)) {
                throw new BusinessException(403, "只能删除自己的工时记录");
            }
        }
        worklogMapper.deleteById(logId);
        return R.ok();
    }

    // ==================== 任务依赖 ====================

    /** 依赖视图：blockedBy = 我的前置（含状态），blocking = 被我阻塞的任务 */
    @Operation(summary = "获取任务依赖")
    @GetMapping("/tasks/{taskId}/dependencies")
    public R<Map<String, Object>> dependencies(@PathVariable String taskId, Authentication authentication) {
        requireTaskVisible(taskId, authentication);

        List<TaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId));
        List<Map<String, Object>> blockedBy = deps.stream().map(d -> {
            Task pre = taskMapper.selectById(d.getDependsOnTaskId());
            Map<String, Object> m = new HashMap<>();
            m.put("dependencyId", d.getId());
            m.put("taskId", d.getDependsOnTaskId());
            m.put("title", pre != null ? pre.getTitle() : "(已删除)");
            m.put("status", pre != null ? pre.getStatus() : null);
            m.put("done", pre != null && "DONE".equals(pre.getStatus()));
            return m;
        }).collect(Collectors.toList());

        List<TaskDependency> dependents = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getDependsOnTaskId, taskId));
        List<Map<String, Object>> blocking = dependents.stream().map(d -> {
            Task t = taskMapper.selectById(d.getTaskId());
            Map<String, Object> m = new HashMap<>();
            m.put("dependencyId", d.getId());
            m.put("taskId", d.getTaskId());
            m.put("title", t != null ? t.getTitle() : "(已删除)");
            m.put("status", t != null ? t.getStatus() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("blockedBy", blockedBy);
        result.put("blocking", blocking);
        return R.ok(result);
    }

    @Operation(summary = "添加依赖（我依赖某前置任务）")
    @PostMapping("/tasks/{taskId}/dependencies")
    public R<TaskDependency> addDependency(@PathVariable String taskId,
                                           @RequestBody Map<String, String> body,
                                           Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        String dependsOn = body.getOrDefault("dependsOnTaskId", "").trim();
        if (dependsOn.isEmpty()) {
            throw new BusinessException(400, "请选择前置任务");
        }
        if (dependsOn.equals(taskId)) {
            throw new BusinessException(400, "任务不能依赖自己");
        }
        Task pre = taskMapper.selectById(dependsOn);
        if (pre == null) {
            throw new BusinessException(404, "前置任务不存在");
        }
        requireTaskVisible(dependsOn, authentication);

        // 环检测：从前置沿依赖链向上走，不能回到自己
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(dependsOn);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!visited.add(cur)) {
                continue;
            }
            if (cur.equals(taskId)) {
                throw new BusinessException(400, "存在循环依赖：该前置任务（直接或间接）依赖当前任务");
            }
            queue.addAll(dependencyMapper.selectDependsOnIds(cur));
        }

        Long dup = dependencyMapper.selectCount(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId)
                .eq(TaskDependency::getDependsOnTaskId, dependsOn));
        if (dup > 0) {
            throw new BusinessException(400, "该依赖已存在");
        }

        TaskDependency dep = new TaskDependency();
        dep.setTaskId(taskId);
        dep.setDependsOnTaskId(dependsOn);
        dependencyMapper.insert(dep);

        // 通知前置任务负责人：有任务依赖你
        if (pre.getAssigneeId() != null && !pre.getAssigneeId().equals(authentication.getName())) {
            Task self = taskMapper.selectById(taskId);
            notificationService.createNotification(
                    pre.getAssigneeId(), "TASK_UPDATE",
                    "有任务依赖你的任务",
                    "「" + (self != null ? self.getTitle() : "一个任务") + "」依赖「" + pre.getTitle() + "」的完成",
                    "/projects/" + (self != null ? self.getProjectId() : "") + "?tab=tasks");
        }
        return R.ok(dep);
    }

    @Operation(summary = "删除依赖")
    @DeleteMapping("/tasks/{taskId}/dependencies/{dependencyId}")
    public R<Void> removeDependency(@PathVariable String taskId,
                                    @PathVariable String dependencyId,
                                    Authentication authentication) {
        requireTaskVisible(taskId, authentication);
        TaskDependency dep = dependencyMapper.selectById(dependencyId);
        if (dep == null || !dep.getTaskId().equals(taskId)) {
            throw new BusinessException(404, "依赖不存在");
        }
        dependencyMapper.deleteById(dependencyId);
        return R.ok();
    }

    // ==================== 看板徽章汇总 ====================

    /** 项目内所有任务的清单进度与未完成前置数（看板卡片徽章，一次查询） */
    @Operation(summary = "项目任务扩展数据汇总（清单进度/未完成前置数）")
    @GetMapping("/projects/{projectId}/task-extras")
    public R<Map<String, Object>> extras(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }

        // 清单进度：按任务聚合 total/done
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId));
        List<String> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());

        Map<String, Map<String, Integer>> checklist = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (TaskChecklistItem item : checklistMapper.selectList(new LambdaQueryWrapper<TaskChecklistItem>()
                    .in(TaskChecklistItem::getTaskId, taskIds))) {
                checklist.computeIfAbsent(item.getTaskId(), k -> new HashMap<>());
                Map<String, Integer> agg = checklist.get(item.getTaskId());
                agg.merge("total", 1, Integer::sum);
                if (Integer.valueOf(1).equals(item.getDone())) {
                    agg.merge("done", 1, Integer::sum);
                }
            }
        }

        // 未完成前置数
        Map<String, Integer> undone = new HashMap<>();
        for (Map<String, Object> row : dependencyMapper.countUndoneBlockersByProject(projectId)) {
            Object taskId = row.get("taskId");
            Object cnt = row.get("undone");
            if (taskId != null && cnt != null) {
                undone.put(taskId.toString(), Integer.valueOf(cnt.toString()));
            }
        }

        // 工时合计（按任务）
        Map<String, java.math.BigDecimal> worklogHours = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (com.devflow.entity.TaskWorklog w : worklogMapper.selectList(
                    new LambdaQueryWrapper<com.devflow.entity.TaskWorklog>()
                            .in(com.devflow.entity.TaskWorklog::getTaskId, taskIds))) {
                if (w.getHours() != null) {
                    worklogHours.merge(w.getTaskId(), w.getHours(), java.math.BigDecimal::add);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checklist", checklist);
        result.put("undoneBlockers", undone);
        result.put("worklogHours", worklogHours);
        return R.ok(result);
    }

    // ==================== 内部 ====================

    private void requireTaskVisible(String taskId, Authentication authentication) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), task.getProjectId())) {
            throw new BusinessException(404, "任务不存在或已被删除");
        }
    }
}
