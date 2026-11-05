package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 Bug 请求
 */
@Data
public class CreateBugRequest {

    @NotBlank(message = "Bug 标题不能为空")
    private String title;

    private String description;
    private String assigneeId;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 严重程度: BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL */
    private String severity;

    private String environment;
}
