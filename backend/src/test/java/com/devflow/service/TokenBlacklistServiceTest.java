package com.devflow.service;

import com.devflow.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 撤销与原子消费")
class TokenBlacklistServiceTest {

    private static final String TOKEN = "refresh-token";
    private static final String KEY = "token:blacklist:" + TOKEN;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService service;

    @Test
    @DisplayName("consume 通过 SETNX 原子占用，第二次占用失败")
    void consumeUsesAtomicSetIfAbsentSemantics() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(KEY, "1", 5_000L, TimeUnit.MILLISECONDS))
                .thenReturn(true, false);

        assertThat(service.consume(TOKEN, 5_000L)).isTrue();
        assertThat(service.consume(TOKEN, 5_000L)).isFalse();

        verify(valueOperations, times(2))
                .setIfAbsent(KEY, "1", 5_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("撤销写入失败返回 503，不静默成功")
    void blacklistWriteFailureReturns503() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new IllegalStateException("redis down")).when(valueOperations)
                .set(KEY, "1", 5_000L, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> service.blacklist(TOKEN, 5_000L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(503));
    }

    @Test
    @DisplayName("consume 失败返回 503，不允许换发新令牌")
    void consumeFailureReturns503() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(KEY, "1", 5_000L, TimeUnit.MILLISECONDS))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.consume(TOKEN, 5_000L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(503));
    }

    @Test
    @DisplayName("撤销状态读取失败按已撤销处理")
    void blacklistReadFailureFailsClosed() {
        when(redisTemplate.hasKey(KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.isBlacklisted(TOKEN)).isTrue();
    }
}
