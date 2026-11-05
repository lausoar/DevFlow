package com.devflow.service;

import com.devflow.entity.TaskComment;

import java.util.List;

/**
 * 任务评论服务接口
 */
public interface TaskCommentService {

    List<TaskComment> listByTask(String taskId);

    TaskComment create(String taskId, String content, String userId, String replyToId);

    void delete(String id);
}
