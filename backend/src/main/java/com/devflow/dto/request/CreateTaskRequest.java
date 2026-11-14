package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建任务请求（白名单字段：状态固定 TODO，报告人取当前登录用户）
 */
@Data
public class CreateTaskRequest {

    @NotBlank(message = "任务标题不能为空")
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

    /** 修复版本（project_release.id，可空） */
    private String fixVersionId;
}
