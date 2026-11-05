package com.devflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 * 用于在用户退出登录时将 Token 加入黑名单，使其失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 将 Token 加入黑名单
     *
     * @param token          JWT Token
     * @param expireMillis   Token 剩余有效时间（毫秒）
     */
    public void blacklist(String token, long expireMillis) {
        try {
            if (expireMillis > 0) {
                redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "1",
                    expireMillis,
                    TimeUnit.MILLISECONDS
                );
            }
        } catch (Exception e) {
            // Redis 不可用时仅记录日志，不影响退出流程
            log.warn("Token 黑名单写入失败（Redis 不可用）: {}", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            // Redis 不可用时视为不在黑名单，允许请求通过
            log.warn("Token 黑名单读取失败（Redis 不可用）: {}", e.getMessage());
            return false;
        }
    }
}
