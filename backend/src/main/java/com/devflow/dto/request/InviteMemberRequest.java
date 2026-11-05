package com.devflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邀请成员请求
 */
@Data
public class InviteMemberRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 真实姓名（可选，邀请已知姓名的成员时填写） */
    private String realName;

    /** 角色: ADMIN / MANAGER / MEMBER */
    private String role;

    /** 部门 ID（可选） */
    private String departmentId;
}
