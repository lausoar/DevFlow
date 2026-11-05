package com.devflow.service;

import com.devflow.dto.request.LoginRequest;
import com.devflow.dto.request.RegisterRequest;
import com.devflow.dto.response.AuthResponse;
import com.devflow.entity.User;

/**
 * 认证服务接口
 */
public interface AuthService {

    /** 用户登录 */
    AuthResponse login(LoginRequest request);

    /** 用户注册 */
    AuthResponse register(RegisterRequest request);

    /** 刷新 Token */
    AuthResponse refreshToken(String refreshToken);

    /** 获取当前用户 */
    User getCurrentUser(String userId);

    /** 退出登录（将 Token 加入黑名单） */
    void logout(String accessToken, String refreshToken);
}
