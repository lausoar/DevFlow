package com.devflow.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新任务请求（白名单字段）。
 * projectId / reporterId / status / deleted 不允许通过本接口改写：
 * 状态走 PUT /tasks/{id}/status，归属字段只在创建时确定。
 */
@Data
public class UpdateTaskRequest {

    @Size(max = 200, message = "任务标题不能超过 200 字")
    private String title;

    @Size(max = 10000, message = "任务描述过长")
    private String description;

    private String sprintId;
    private String assigneeId;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    private Integer storyPoints;
    private LocalDateTime dueDate;
}
