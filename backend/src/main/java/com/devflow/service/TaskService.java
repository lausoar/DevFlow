package com.devflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.Task;

import java.util.List;

/**
 * 任务服务接口
 */
public interface TaskService {

    Page<Task> listByProject(String projectId, int page, int pageSize, String keyword, String status);

    List<Task> listBySprint(String sprintId);

    List<Task> listMyTasks(String userId);

    Task getById(String id);

    Task create(String projectId, Task task, String reporterId);

    Task update(String id, Task task);

    void updateStatus(String id, String status);

    void delete(String id);
}
