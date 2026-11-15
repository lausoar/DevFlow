package com.devflow.dto.response;

import com.devflow.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * 认证响应 DTO
 *
 * 双因素：启用 TOTP 的用户首次提交密码后不直接发放 token，
 * 而是返回 mfaRequired=true + mfaToken（短期一次性凭证），
 * 前端再提交动态码换取正式 token。
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private User user;

    /** 需要继续完成双因素校验 */
    private Boolean mfaRequired;

    /** 双因素校验用的短期凭证（5 分钟有效，只能用于 /auth/mfa/verify） */
    private String mfaToken;

    /** 密码待修改（首次登录 / 超管重置后） */
    private Boolean mustChangePassword;
}
