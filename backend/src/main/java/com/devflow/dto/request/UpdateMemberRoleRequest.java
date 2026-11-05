package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新成员角色请求
 */
@Data
public class UpdateMemberRoleRequest {

    @NotBlank(message = "角色不能为空")
    private String role;
}
