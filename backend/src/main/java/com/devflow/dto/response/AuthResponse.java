package com.devflow.dto.response;

import com.devflow.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * 认证响应 DTO
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private User user;
}
