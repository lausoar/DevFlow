package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.Task;
import com.devflow.entity.TaskDependency;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.NotificationService;
import com.devflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final com.devflow.service.WorkflowService workflowService;
    private final com.devflow.service.SubscriptionService subscriptionService;
    private final com.devflow.service.NotificationRecipients notificationRecipients;
    private final com.devflow.mapper.TaskDependencyMapper taskDependencyMapper;

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
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus("TODO"); // 兜底：正常路径由 Controller 按工作流初始状态赋值
        }
        taskMapper.insert(task);

        // 自动关注：创建人与被指派人（否则用户会漏掉与自己直接相关的事）
        try {
            subscriptionService.autoSubscribe(reporterId, "TASK", task.getId(), projectId);
            if (StringUtils.hasText(task.getAssigneeId())) {
                subscriptionService.autoSubscribe(task.getAssigneeId(), "TASK", task.getId(), projectId);
            }
        } catch (Exception e) {
            log.debug("自动关注失败（不影响创建）: {}", e.getMessage());
        }

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
    public Task update(String id, Task task, Integer expectedVersion) {
        Task existing = taskMapper.selectById(id);
        if (existing == null) return null;

        // 乐观锁：客户端带上版本号才校验。冲突时抛 409 而不是覆盖别人的修改。
        if (expectedVersion != null) {
            int current = existing.getVersion() == null ? 1 : existing.getVersion();
            if (expectedVersion != current) {
                throw new com.devflow.common.exception.BusinessException(409,
                        "任务已被他人修改（当前版本 v" + current + "），请刷新后再保存");
            }
            task.setVersion(expectedVersion);
        }

        // 记录分配变更，用于通知
        String oldAssigneeId = existing.getAssigneeId();
        String oldStatus = existing.getStatus();

        // 空串表示"清除"（updateById 默认跳过 null 字段，无法用 null 表达"置空"）
        boolean clearAssignee = "".equals(task.getAssigneeId());
        boolean clearSprint = "".equals(task.getSprintId());
        if (clearAssignee) {
            task.setAssigneeId(null);
        }
        if (clearSprint) {
            task.setSprintId(null);
        }

        taskMapper.updateById(task);

        if (clearAssignee || clearSprint) {
            LambdaUpdateWrapper<Task> clear = new LambdaUpdateWrapper<Task>().eq(Task::getId, id);
            if (clearAssignee) {
                clear.set(Task::getAssigneeId, null);
            }
            if (clearSprint) {
                clear.set(Task::getSprintId, null);
            }
            taskMapper.update(null, clear);
        }

        // 重新查询以获取更新后的完整数据
        Task updated = taskMapper.selectById(id);

        // 如果被指派人变更，通知新的被指派人并让他自动关注
        if (updated != null && StringUtils.hasText(updated.getAssigneeId())
                && !updated.getAssigneeId().equals(oldAssigneeId)) {
            try {
                subscriptionService.autoSubscribe(updated.getAssigneeId(), "TASK",
                        updated.getId(), updated.getProjectId());
            } catch (Exception e) {
                log.debug("改派自动关注失败（不影响主流程）: {}", e.getMessage());
            }
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
    public void updateStatus(String id, String status, String operatorId) {
        Task task = taskMapper.selectById(id);
        if (task == null) return;

        task.setStatus(status);
        // 完成时间只在"进入/离开 DONE 分类"时变，不能靠 updated_at 推断
        // （改标题、换负责人都会刷新 updated_at，历史燃尽曲线就被重画了）
        boolean becomingDone = workflowService != null && isDoneStatus(task.getProjectId(), status);
        if (becomingDone && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (!becomingDone) {
            task.setCompletedAt(null);
        }
        taskMapper.updateById(task);

        // 通知任务相关人员（报告人和被指派人）∪ 关注该任务的人
        List<String> explicit = new ArrayList<>();
        if (StringUtils.hasText(task.getReporterId())) explicit.add(task.getReporterId());
        if (StringUtils.hasText(task.getAssigneeId())) explicit.add(task.getAssigneeId());
        List<String> notifyUsers = notificationRecipients.resolve(
                "TASK", task.getId(), operatorId, explicit);

        // 状态展示名由工作流配置决定（支持自定义状态）
        String statusLabel = workflowService != null
                ? workflowService.labelOf(task.getProjectId(),
                        com.devflow.service.WorkflowService.TYPE_TASK, status)
                : status;

        notificationService.notifyMultiple(
            notifyUsers,
            "TASK_UPDATE",
            "任务状态更新",
            "任务 「" + task.getTitle() + "」 状态已更新为: " + statusLabel,
            "/projects/" + task.getProjectId() + "?tab=tasks"
        );
    }

    /** 是否属于该项目的 DONE 分类（项目可自定义终态标签） */
    private boolean isDoneStatus(String projectId, String status) {
        if (status == null) return false;
        try {
            return com.devflow.entity.WorkflowStatus.CATEGORY_DONE.equals(
                    workflowService.categoryOf(projectId, com.devflow.service.WorkflowService.TYPE_TASK, status));
        } catch (Exception e) {
            return "DONE".equals(status);
        }
    }

    /**
     * 删除任务（逻辑删除）。
     *
     * 原实现只删任务行，子表（依赖/工时/清单/评论/需求关联）全部留成孤儿：
     * 已删除的任务还挂在别人的"前置依赖"上，看板徽章会永久显示"前置未完成"。
     * 这里只清理依赖关系（其余子表按 task_id 存在即可，不参与跨对象判断）。
     * 已删除依赖自然消失，任务被恢复时依赖不会自动恢复 —— 这符合直觉：
     * 删掉的任务恢复后重排关系，比带着一堆指向已删任务的阻塞更安全。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void delete(String id) {
        taskDependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, id)
                .or()
                .eq(TaskDependency::getDependsOnTaskId, id));
        taskMapper.deleteById(id);
    }
}
