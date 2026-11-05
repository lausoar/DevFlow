package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.entity.Task;
import com.devflow.entity.TaskComment;
import com.devflow.mapper.TaskCommentMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.NotificationService;
import com.devflow.service.TaskCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务评论服务实现
 * 在创建评论时通知任务被指派人和被回复者
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCommentServiceImpl implements TaskCommentService {

    private final TaskCommentMapper taskCommentMapper;
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;
    private final com.devflow.service.MentionService mentionService;

    @Override
    public List<TaskComment> listByTask(String taskId) {
        LambdaQueryWrapper<TaskComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskComment::getTaskId, taskId);
        wrapper.orderByAsc(TaskComment::getCreatedAt);
        return taskCommentMapper.selectList(wrapper);
    }

    @Override
    public TaskComment create(String taskId, String content, String userId, String replyToId) {
        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setContent(content);
        comment.setUserId(userId);
        comment.setReplyToId(replyToId);
        taskCommentMapper.insert(comment);

        // 收集需要通知的用户
        List<String> notifyUsers = new ArrayList<>();

        // 如果回复某人，通知被回复者
        if (StringUtils.hasText(replyToId)) {
            TaskComment parentComment = taskCommentMapper.selectById(replyToId);
            if (parentComment != null && !parentComment.getUserId().equals(userId)) {
                notifyUsers.add(parentComment.getUserId());
            }
        } else {
            // 新评论通知任务被指派人和报告人
            Task task = taskMapper.selectById(taskId);
            if (task != null) {
                if (StringUtils.hasText(task.getAssigneeId()) && !task.getAssigneeId().equals(userId)) {
                    notifyUsers.add(task.getAssigneeId());
                }
                if (StringUtils.hasText(task.getReporterId()) && !task.getReporterId().equals(userId)) {
                    notifyUsers.add(task.getReporterId());
                }
            }
        }

        if (!notifyUsers.isEmpty()) {
            Task namedTask = taskMapper.selectById(taskId);
            notificationService.notifyMultiple(
                notifyUsers,
                "COMMENT",
                "任务「" + (namedTask != null ? namedTask.getTitle() : "") + "」有新评论",
                preview(content),
                "/projects/" + (namedTask != null ? namedTask.getProjectId() : "") + "?tab=tasks"
            );
        }

        // @提及：评论里 @ 项目成员（用户名/真实姓名）额外通知
        Task commentTask = taskMapper.selectById(taskId);
        if (commentTask != null) {
            mentionService.notifyMentions(
                commentTask.getProjectId(), userId, content,
                "任务「" + commentTask.getTitle() + "」的评论",
                "/projects/" + commentTask.getProjectId() + "?tab=tasks");
        }

        return comment;
    }

    @Override
    public void delete(String id) {
        taskCommentMapper.deleteById(id);
    }

    private String preview(String content) {
        return content != null && content.length() > 100 ? content.substring(0, 100) + "…" : content;
    }
}
