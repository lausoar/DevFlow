package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.Task;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.NotificationService;
import com.devflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务服务实现
 * 在任务创建、状态变更、分配时自动生成通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    @Override
    public Page<Task> listByProject(String projectId, int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Task::getTitle, keyword)
                    .or().like(Task::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Task::getStatus, status);
        }
        wrapper.orderByDesc(Task::getCreatedAt);
        return taskMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public List<Task> listBySprint(String sprintId) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getSprintId, sprintId);
        wrapper.orderByAsc(Task::getPriority).orderByDesc(Task::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }

    @Override
    public List<Task> listMyTasks(String userId) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getAssigneeId, userId);
        wrapper.orderByAsc(Task::getDueDate).orderByDesc(Task::getCreatedAt);
        wrapper.last("LIMIT 200");
        return taskMapper.selectList(wrapper);
    }

    @Override
    public Task getById(String id) {
        return taskMapper.selectById(id);
    }

    @Override
    public Task create(String projectId, Task task, String reporterId) {
        task.setProjectId(projectId);
        task.setReporterId(reporterId);
        task.setStatus("TODO");
        taskMapper.insert(task);

        // 通知被指派的人
        if (StringUtils.hasText(task.getAssigneeId()) && !task.getAssigneeId().equals(reporterId)) {
            notificationService.createNotification(
                task.getAssigneeId(),
                "TASK_UPDATE",
                "新任务指派",
                "你被指派了一个新任务: " + task.getTitle(),
                "/projects/" + task.getProjectId() + "?tab=tasks"
            );
        }

        return task;
    }

    @Override
    public Task update(String id, Task task) {
        Task existing = taskMapper.selectById(id);
        if (existing == null) return null;

        // 记录分配变更，用于通知
        String oldAssigneeId = existing.getAssigneeId();
        String oldStatus = existing.getStatus();

        taskMapper.updateById(task);

        // 重新查询以获取更新后的完整数据
        Task updated = taskMapper.selectById(id);

        // 如果被指派人变更，通知新的被指派人
        if (updated != null && StringUtils.hasText(updated.getAssigneeId())
                && !updated.getAssigneeId().equals(oldAssigneeId)) {
            notificationService.createNotification(
                updated.getAssigneeId(),
                "TASK_UPDATE",
                "任务指派变更",
                "你被指派了任务: " + updated.getTitle(),
                "/projects/" + updated.getProjectId() + "?tab=tasks"
            );
        }

        return updated;
    }

    @Override
    public void updateStatus(String id, String status) {
        Task task = taskMapper.selectById(id);
        if (task == null) return;

        task.setStatus(status);
        taskMapper.updateById(task);

        // 通知任务相关人员（报告人和被指派人）
        List<String> notifyUsers = new ArrayList<>();
        if (StringUtils.hasText(task.getReporterId())) notifyUsers.add(task.getReporterId());
        if (StringUtils.hasText(task.getAssigneeId())) notifyUsers.add(task.getAssigneeId());

        String statusLabel = switch (status) {
            case "IN_PROGRESS" -> "进行中";
            case "IN_REVIEW" -> "评审中";
            case "DONE" -> "已完成";
            default -> status;
        };

        notificationService.notifyMultiple(
            notifyUsers,
            "TASK_UPDATE",
            "任务状态更新",
            "任务 「" + task.getTitle() + "」 状态已更新为: " + statusLabel,
            "/projects/" + task.getProjectId() + "?tab=tasks"
        );
    }

    @Override
    public void delete(String id) {
        taskMapper.deleteById(id);
    }
}
