package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.entity.TaskComment;
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
 */
@Tag(name = "任务评论", description = "任务评论的增删查")
@RestController
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService taskCommentService;
    private final com.devflow.mapper.TaskMapper taskMapper;
    private final ProjectAccessService projectAccessService;

    /** 获取任务的评论列表 */
    @Operation(summary = "获取任务评论")
    @GetMapping("/tasks/{taskId}/comments")
    public R<List<TaskComment>> listByTask(@PathVariable String taskId, Authentication authentication) {
        com.devflow.entity.Task commentTask = taskMapper.selectById(taskId);
        if (commentTask == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), commentTask.getProjectId())) {
            throw new com.devflow.common.exception.BusinessException(404, "任务不存在或已被删除");
        }

        return R.ok(taskCommentService.listByTask(taskId));
    }

    /** 创建评论 */
    @Operation(summary = "创建评论")
    @PostMapping("/tasks/{taskId}/comments")
    public R<TaskComment> create(@PathVariable String taskId,
                                 @RequestBody Map<String, String> body,
                                 Authentication authentication) {
        com.devflow.entity.Task commentTask = taskMapper.selectById(taskId);
        if (commentTask == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), commentTask.getProjectId())) {
            throw new com.devflow.common.exception.BusinessException(404, "任务不存在或已被删除");
        }

        String userId = (String) authentication.getPrincipal();
        String content = body.get("content");
        String replyToId = body.get("replyToId");
        return R.ok(taskCommentService.create(taskId, content, userId, replyToId));
    }

    /** 删除评论 */
    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public R<Void> delete(@PathVariable String id) {
        taskCommentService.delete(id);
        return R.ok();
    }
}
