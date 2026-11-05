package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 创建任务请求
 */
@Data
public class CreateTaskRequest {

    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String description;
    private String sprintId;
    private String assigneeId;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    private Integer storyPoints;
    private LocalDateTime dueDate;
}
