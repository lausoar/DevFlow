package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

/**
 * 创建 Sprint 请求
 */
@Data
public class CreateSprintRequest {

    @NotBlank(message = "Sprint 名称不能为空")
    private String name;

    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
