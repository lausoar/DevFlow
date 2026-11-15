package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 *
 * mfaCode 用于启用双因素时的第二次提交（同一接口，带 mfaToken）；
 * 首次提交只需 username + password。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少 6 位")
    private String password;

    /** 双因素短期凭证（首次提交密码后由服务端下发） */
    private String mfaToken;

    /** 6 位动态码 */
    private String mfaCode;
}
