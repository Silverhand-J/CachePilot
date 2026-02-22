package com.example.aliintern.scheduler.ratelimit;

import com.example.aliintern.scheduler.common.redis.RedisCounterException;
import com.example.aliintern.scheduler.common.redis.RedisCounterExecutor;
import com.example.aliintern.scheduler.config.SchedulerProperties;
import com.example.aliintern.scheduler.ratelimit.impl.DefaultRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DefaultRateLimiterService 单元测试
 * 测试限流的核心逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("限流服务测试")
class DefaultRateLimiterServiceTest {

    @Mock
    private RedisCounterExecutor redisCounterExecutor;

    @Mock
    private SchedulerProperties schedulerProperties;

    @Mock
    private SchedulerProperties.RateLimitConfig rateLimitConfig;

    private DefaultRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        lenient().when(schedulerProperties.getRateLimit()).thenReturn(rateLimitConfig);
        
        // 默认配置
        lenient().when(rateLimitConfig.getEnableGlobalLimit()).thenReturn(true);
        lenient().when(rateLimitConfig.getEnableBizTypeLimit()).thenReturn(true);
        lenient().when(rateLimitConfig.getEnableBizKeyLimit()).thenReturn(true);
        lenient().when(rateLimitConfig.getGlobalQpsLimit()).thenReturn(10000);
        lenient().when(rateLimitConfig.getBizTypeQpsLimit()).thenReturn(5000);
        lenient().when(rateLimitConfig.getBizKeyQpsLimit()).thenReturn(1000);
        lenient().when(rateLimitConfig.getWindowSeconds()).thenReturn(1L);
        lenient().when(rateLimitConfig.getKeyPrefix()).thenReturn("ratelimit");
        lenient().when(rateLimitConfig.getFailOpen()).thenReturn(true);
        // 超时配置（新增）
        lenient().when(rateLimitConfig.getRedisTimeout()).thenReturn(100);

        rateLimiterService = new DefaultRateLimiterService(redisCounterExecutor, schedulerProperties);
    }

    private Map<String, Long> createMockResult(long globalCount, long bizTypeCount, long bizKeyCount) {
        return new HashMap<String, Long>() {{
            put("mockGlobalKey", globalCount);
            put("mockBizTypeKey", bizTypeCount);
            put("mockBizKeyKey", bizKeyCount);
        }};
    }

    @Test
    @DisplayName("所有维度未超限 - 应放行")
    void testAcquire_allDimensionsWithinLimit_shouldAllow() throws Exception {
        // Given - 使用动态匹配返回合适的值
        when(redisCounterExecutor.batchIncrement(any())).thenAnswer(invocation -> {
            Map<String, Long> keys = invocation.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            for (String key : keys.keySet()) {
                result.put(key, 100L); // 所有维度都返回小于限流阈值的值
            }
            return result;
        });

        // When
        rateLimiterService.acquire("order", "sku001");

        // Then
        verify(redisCounterExecutor, times(1)).batchIncrement(any());
    }

    @Test
    @DisplayName("全局维度超限 - 应拒绝")
    void testAcquire_globalLimitExceeded_shouldReject() throws Exception {
        // Given
        when(redisCounterExecutor.batchIncrement(any())).thenAnswer(invocation -> {
            Map<String, Long> keys = invocation.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            for (String key : keys.keySet()) {
                if (key.contains(":global:")) {
                    result.put(key, 12000L); // 超过 10000
                } else {
                    result.put(key, 50L);
                }
            }
            return result;
        });

        // When & Then
        assertThatThrownBy(() -> rateLimiterService.acquire("order", "sku001"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("GLOBAL");
    }

    @Test
    @DisplayName("bizType 维度超限 - 应拒绝")
    void testAcquire_bizTypeLimitExceeded_shouldReject() throws Exception {
        // Given
        when(redisCounterExecutor.batchIncrement(any())).thenAnswer(invocation -> {
            Map<String, Long> keys = invocation.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            for (String key : keys.keySet()) {
                if (key.contains(":biztype:")) {
                    result.put(key, 6000L); // 超过 5000
                } else {
                    result.put(key, 50L);
                }
            }
            return result;
        });

        // When & Then
        assertThatThrownBy(() -> rateLimiterService.acquire("order", "sku001"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("BIZ_TYPE");
    }

    @Test
    @DisplayName("bizKey 维度超限 - 应拒绝")
    void testAcquire_bizKeyLimitExceeded_shouldReject() throws Exception {
        // Given
        when(redisCounterExecutor.batchIncrement(any())).thenAnswer(invocation -> {
            Map<String, Long> keys = invocation.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            for (String key : keys.keySet()) {
                if (key.contains(":bizkey:")) {
                    result.put(key, 1500L); // 超过 1000
                } else {
                    result.put(key, 50L);
                }
            }
            return result;
        });

        // When & Then
        assertThatThrownBy(() -> rateLimiterService.acquire("order", "sku001"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("BIZ_KEY");
    }

    @Test
    @DisplayName("关闭所有维度 - 应直接放行")
    void testAcquire_allDimensionsDisabled_shouldAllow() throws Exception {
        // Given
        when(rateLimitConfig.getEnableGlobalLimit()).thenReturn(false);
        when(rateLimitConfig.getEnableBizTypeLimit()).thenReturn(false);
        when(rateLimitConfig.getEnableBizKeyLimit()).thenReturn(false);

        // When
        rateLimiterService.acquire("order", "sku001");

        // Then - 不应调用 Redis
        verify(redisCounterExecutor, never()).batchIncrement(any());
    }

    @Test
    @DisplayName("Redis 异常 + fail-open - 应放行")
    void testAcquire_redisFailureWithFailOpen_shouldAllow() throws Exception {
        // Given
        when(rateLimitConfig.getFailOpen()).thenReturn(true);
        when(redisCounterExecutor.batchIncrement(any()))
                .thenThrow(new RedisCounterException("Redis connection failed"));

        // When - 不应抛出异常
        rateLimiterService.acquire("order", "sku001");

        // Then
        verify(redisCounterExecutor, times(1)).batchIncrement(any());
    }

    @Test
    @DisplayName("Redis 异常 + fail-closed - 应拒绝")
    void testAcquire_redisFailureWithFailClosed_shouldReject() throws Exception {
        // Given
        when(rateLimitConfig.getFailOpen()).thenReturn(false);
        when(redisCounterExecutor.batchIncrement(any()))
                .thenThrow(new RedisCounterException("Redis connection failed"));

        // When & Then
        assertThatThrownBy(() -> rateLimiterService.acquire("order", "sku001"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("REDIS_FAILURE");
    }

    @Test
    @DisplayName("批量执行 - 应一次调用完成所有维度检查")
    void testAcquire_batchExecution_shouldCallRedisOnce() throws Exception {
        // Given
        when(redisCounterExecutor.batchIncrement(any())).thenAnswer(invocation -> {
            Map<String, Long> keys = invocation.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            for (String key : keys.keySet()) {
                result.put(key, 100L);
            }
            return result;
        });

        // When
        rateLimiterService.acquire("order", "sku001");

        // Then - 验证只调用了一次 Redis
        verify(redisCounterExecutor, times(1)).batchIncrement(any());
    }
}
