package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.TaskComment;
import com.devflow.entity.User;
import com.devflow.mapper.TaskCommentMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.TaskCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务评论控制器
 *
 * 删除权限：评论作者本人，或同企业的企业管理员及以上（与 Bug 评论口径一致）。
 */
@Tag(name = "任务评论", description = "任务评论的增删查")
@RestController
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService taskCommentService;
    private final TaskCommentMapper taskCommentMapper;
    private final UserMapper userMapper;
    private final com.devflow.mapper.TaskMapper taskMapper;
    private final ProjectAccessService projectAccessService;
    private final AccessGuard guard;

    /** 获取任务的评论列表（附评论人展示名，避免前端显示 UUID 片段） */
    @Operation(summary = "获取任务评论")
    @GetMapping("/tasks/{taskId}/comments")
    public R<List<Map<String, Object>>> listByTask(@PathVariable String taskId, Authentication authentication) {
        requireTaskVisible(guard.userId(authentication), taskId);
        List<TaskComment> comments = taskCommentService.listByTask(taskId);
        return R.ok(comments.stream().map(c -> {
            User author = userMapper.selectById(c.getUserId());
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", c.getId());
            row.put("taskId", c.getTaskId());
            row.put("userId", c.getUserId());
            row.put("content", c.getContent());
            row.put("replyToId", c.getReplyToId());
            row.put("createdAt", c.getCreatedAt());
            row.put("username", author != null ? author.displayName() : null);
            row.put("avatar", author != null ? author.getAvatar() : null);
            return row;
        }).toList());
    }

    /** 创建评论 */
    @Operation(summary = "创建评论")
    @PostMapping("/tasks/{taskId}/comments")
    public R<TaskComment> create(@PathVariable String taskId,
                                 @RequestBody Map<String, String> body,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        requireTaskVisible(userId, taskId);

        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BusinessException(400, "评论不能超过 2000 字");
        }
        String replyToId = body.get("replyToId");
        return R.ok(taskCommentService.create(taskId, content.trim(), userId, replyToId));
    }

    /** 删除评论：仅评论作者本人或企业管理员及以上 */
    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        TaskComment comment = taskCommentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        com.devflow.entity.Task task = taskMapper.selectById(comment.getTaskId());
        if (task == null || !projectAccessService.canView(userId, task.getProjectId())) {
            throw new BusinessException(404, "评论不存在");
        }

        if (!userId.equals(comment.getUserId())) {
            User operator = userMapper.selectById(userId);
            boolean admin = operator != null
                    && com.devflow.common.EnterpriseRoles.level(operator.getRole())
                    >= com.devflow.common.EnterpriseRoles.level(com.devflow.common.EnterpriseRoles.ADMIN);
            // 管理员只能删本企业项目的评论
            User author = userMapper.selectById(comment.getUserId());
            boolean sameEnterprise = operator != null && author != null
                    && operator.getEnterpriseId() != null
                    && operator.getEnterpriseId().equals(author.getEnterpriseId());
            if (!(admin && sameEnterprise)) {
                throw new BusinessException(403, "只能删除自己的评论");
            }
        }
        taskCommentService.delete(id);
        return R.ok();
    }

    private void requireTaskVisible(String userId, String taskId) {
        com.devflow.entity.Task task = taskMapper.selectById(taskId);
        if (task == null || !projectAccessService.canView(userId, task.getProjectId())) {
            throw new BusinessException(404, "任务不存在或已被删除");
        }
    }
}
