package com.devflow.service;

import com.devflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token, long expireMillis) {
        if (expireMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "1",
                    expireMillis,
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            log.error("Token 撤销写入失败: {}", e.getMessage());
            throw new BusinessException(503, "登录状态撤销服务不可用，请稍后重试");
        }
    }

    public boolean consume(String token, long expireMillis) {
        if (expireMillis <= 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    BLACKLIST_PREFIX + token,
                    "1",
                    expireMillis,
                    TimeUnit.MILLISECONDS
            ));
        } catch (Exception e) {
            log.error("Refresh Token 轮换失败: {}", e.getMessage());
            throw new BusinessException(503, "登录状态服务不可用，请稍后重试");
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            log.error("Token 撤销状态读取失败，按已撤销处理: {}", e.getMessage());
            return true;
        }
    }
}
