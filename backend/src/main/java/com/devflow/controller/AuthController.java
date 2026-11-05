package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.dto.request.LoginRequest;
import com.devflow.dto.request.RegisterRequest;
import com.devflow.dto.response.AuthResponse;
import com.devflow.entity.User;
import com.devflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户登录、注册、Token 刷新")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户登录 */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 用户注册 */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    /** 刷新 Token */
    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public R<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refreshToken(request.getRefreshToken()));
    }

    /** 退出登录 */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request,
                          @RequestBody(required = false) LogoutRequest body) {
        // 从请求头提取 Access Token
        String header = request.getHeader("Authorization");
        String accessToken = (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;

        // 从请求体提取 Refresh Token（可选）
        String refreshToken = (body != null) ? body.getRefreshToken() : null;

        authService.logout(accessToken, refreshToken);
        return R.ok(null);
    }

    /** 退出登录请求体 */
    @lombok.Data
    public static class LogoutRequest {
        private String refreshToken;
    }

    /** 获取当前用户信息 */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<User> getCurrentUser(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(authService.getCurrentUser(userId));
    }

    /** 刷新 Token 请求体 */
    @lombok.Data
    public static class RefreshTokenRequest {
        private String refreshToken;
    }
}
