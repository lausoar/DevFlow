package com.devflow.service;

/**
 * 双因素认证（TOTP）服务。
 *
 * 流程：
 *   1. 用户请求启用 → 服务端生成密钥并返回 otpauth 链接（此时**尚未启用**）；
 *   2. 用户用验证器 App 扫码，回传一次动态码确认绑定；
 *   3. 之后登录需要「密码 + 动态码」两个因子。
 *
 * 短期凭证（mfaToken）只用于换取正式 token，5 分钟过期，
 * 且绑定 userId —— 不能被挪用于其他账号。
 */
public interface MfaService {

    /** 发起绑定：返回密钥与 otpauth 链接（不落库，等用户确认后才写） */
    BindResult beginBind(String userId);

    /** 确认绑定：校验动态码后写入密钥并置 totp_enabled=1 */
    void confirmBind(String userId, String secret, String code);

    /** 关闭双因素（需要当前动态码，避免会话被劫持后直接关掉） */
    void disable(String userId, String code);

    /** 是否已启用 */
    boolean isEnabled(String userId);

    /** 签发短期挑战凭证（登录时密码已通过、等待动态码） */
    String issueChallenge(String userId);

    /** 校验挑战凭证 + 动态码；失败抛 401 并消耗凭证 */
    void verifyChallenge(String challengeToken, String expectedUserId, String code);

    /** 绑定结果 */
    record BindResult(String secret, String otpauthUrl) {
    }
}
