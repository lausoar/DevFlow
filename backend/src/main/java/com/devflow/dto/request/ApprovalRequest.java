package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 需求审批请求
 */
@Data
public class ApprovalRequest {

    /** 审批动作: APPROVE / REJECT */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** 审批意见 */
    private String comment;
}
