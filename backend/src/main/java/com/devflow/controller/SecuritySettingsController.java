package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 账户安全设置：双因素认证、密码策略状态。
 *
 * 所有接口只作用于"当前登录用户自己"，不接受 userId 参数 —— 双因素设置
 * 天然属于个人，管理员重置他人密码走企业管理的独立端点。
 */
@Tag(name = "账户安全", description = "双因素认证、密码策略")
@RestController
@RequiredArgsConstructor
public class SecuritySettingsController {

    private final MfaService mfaService;
    private final UserMapper userMapper;
    private final AccessGuard guard;
    private final AuditService auditService;

    // ==================== 双因素 ====================

    @Operation(summary = "查看双因素认证状态")
    @GetMapping("/security/mfa")
    public R<Map<String, Object>> status(Authentication authentication) {
        String userId = guard.userId(authentication);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", Integer.valueOf(1).equals(user.getTotpEnabled()));
        m.put("boundAt", user.getTotpBoundAt());
        m.put("mustChangePassword", Integer.valueOf(1).equals(user.getMustChangePassword()));
        m.put("passwordUpdatedAt", user.getPasswordUpdatedAt());
        return R.ok(m);
    }

    @Operation(summary = "发起双因素绑定（返回密钥与 otpauth 链接）")
    @PostMapping("/security/mfa/bind")
    public R<MfaService.BindResult> beginBind(Authentication authentication) {
        return R.ok(mfaService.beginBind(guard.userId(authentication)));
    }

    @Data
    public static class ConfirmBindRequest {
        private String secret;
        private String code;
    }

    @Operation(summary = "确认双因素绑定（提交验证器生成的动态码）")
    @PostMapping("/security/mfa/confirm")
    public R<Void> confirmBind(@RequestBody ConfirmBindRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);
        mfaService.confirmBind(userId, request.getSecret(), request.getCode());
        auditService.log(userId, guard.enterpriseIdOf(userId), null,
                "security.mfa.enable", "user", userId, "启用双因素认证");
        return R.ok();
    }

    @Data
    public static class CodeRequest {
        private String code;
    }

    @Operation(summary = "关闭双因素认证（需当前动态码）")
    @PostMapping("/security/mfa/disable")
    public R<Void> disable(@RequestBody CodeRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);
        mfaService.disable(userId, request.getCode());
        auditService.log(userId, guard.enterpriseIdOf(userId), null,
                "security.mfa.disable", "user", userId, "关闭双因素认证");
        return R.ok();
    }

    // ==================== 密码策略 ====================

    @Operation(summary = "企业密码策略（供前端提示，不暴露任何密钥）")
    @GetMapping("/security/password-policy")
    public R<Map<String, Object>> passwordPolicy(Authentication authentication) {
        String userId = guard.userId(authentication);
        User user = userMapper.selectById(userId);
        Map<String, Object> m = new HashMap<>();
        m.put("minLength", PASSWORD_MIN_LENGTH);
        m.put("requireComplexity", true);
        m.put("mustChangePassword", user != null && Integer.valueOf(1).equals(user.getMustChangePassword()));
        m.put("passwordUpdatedAt", user != null ? user.getPasswordUpdatedAt() : null);
        m.put("expireDays", 0); // 0 = 不强制过期，仅提示（企业合规可改为 90）
        return R.ok(m);
    }

    @Operation(summary = "密码强度校验说明（前端可即时调用）")
    @PostMapping("/security/password/check")
    public R<Map<String, Object>> checkPassword(@RequestBody CodeRequest request) {
        List<String> problems = com.devflow.util.PasswordPolicy.validate(request.getCode());
        return R.ok(Map.of("ok", problems.isEmpty(), "problems", problems));
    }

    private static final int PASSWORD_MIN_LENGTH = com.devflow.util.PasswordPolicy.MIN_LENGTH;
}
