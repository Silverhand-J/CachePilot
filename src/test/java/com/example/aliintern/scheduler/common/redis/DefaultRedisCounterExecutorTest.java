package com.example.aliintern.scheduler.common.redis;

import com.example.aliintern.scheduler.common.redis.impl.DefaultRedisCounterExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultRedisCounterExecutor 单元测试
 * 
 * 测试范围：
 * - 单 Key 计数功能
 * - 批量 Key 计数功能
 * - Lua 脚本原子性
 * - Redis 异常处理
 * - 健康检查
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 计数执行层测试")
class DefaultRedisCounterExecutorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    private DefaultRedisCounterExecutor executor;

    @BeforeEach
    void setUp() {
        // Mock ValueOperations 链式调用
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        executor = new DefaultRedisCounterExecutor(redisTemplate);
        executor.init();
    }

    @Test
    @DisplayName("单 Key 计数 - 首次访问应返回 1")
    void testIncrement_firstTime_shouldReturnOne() {
        // Given
        String key = "test:counter";
        long ttl = 60L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        // When
        long result = executor.increment(key, ttl);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(redisTemplate, times(1)).execute(
                any(RedisScript.class),
                eq(Collections.singletonList(key)),
                eq("60")
        );
    }

    @Test
    @DisplayName("单 Key 计数 - 第二次访问应返回 2")
    void testIncrement_secondTime_shouldReturnTwo() {
        // Given
        String key = "test:counter";
        long ttl = 60L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(2L);

        // When
        long result = executor.increment(key, ttl);

        // Then
        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("单 Key 计数 - Redis 返回 null 应抛异常")
    void testIncrement_redisReturnsNull_shouldThrowException() {
        // Given
        String key = "test:counter";
        long ttl = 60L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> executor.increment(key, ttl))
                .isInstanceOf(RedisCounterException.class)
                .hasMessageContaining("Failed to increment counter");
    }

    @Test
    @DisplayName("单 Key 计数 - Redis 异常应抛 RedisCounterException")
    void testIncrement_redisException_shouldThrowRedisCounterException() {
        // Given
        String key = "test:counter";
        long ttl = 60L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> executor.increment(key, ttl))
                .isInstanceOf(RedisCounterException.class)
                .hasMessageContaining("Failed to increment counter")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("批量计数 - 应一次性返回多个结果")
    void testBatchIncrement_multipleKeys_shouldReturnAllResults() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        keyTtlMap.put("key1", 60L);
        keyTtlMap.put("key2", 120L);
        keyTtlMap.put("key3", 30L);

        List<Long> mockResults = Arrays.asList(1L, 2L, 3L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        // When
        Map<String, Long> results = executor.batchIncrement(keyTtlMap);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get("key1")).isEqualTo(1L);
        assertThat(results.get("key2")).isEqualTo(2L);
        assertThat(results.get("key3")).isEqualTo(3L);
        
        // 验证只调用了一次 Redis（性能优化点）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("批量计数 - 空 Map 应返回空结果")
    void testBatchIncrement_emptyMap_shouldReturnEmpty() {
        // Given
        Map<String, Long> emptyMap = new HashMap<>();

        // When
        Map<String, Long> results = executor.batchIncrement(emptyMap);

        // Then
        assertThat(results).isEmpty();
        verify(redisTemplate, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("批量计数 - null 输入应返回空结果")
    void testBatchIncrement_nullInput_shouldReturnEmpty() {
        // When
        Map<String, Long> results = executor.batchIncrement(null);

        // Then
        assertThat(results).isEmpty();
        verify(redisTemplate, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("批量计数 - Redis 返回结果数量不匹配应抛异常")
    void testBatchIncrement_resultSizeMismatch_shouldThrowException() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        keyTtlMap.put("key1", 60L);
        keyTtlMap.put("key2", 120L);

        // Redis 只返回 1 个结果，但请求了 2 个 key
        List<Long> mockResults = Collections.singletonList(1L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(mockResults);

        // When & Then
        assertThatThrownBy(() -> executor.batchIncrement(keyTtlMap))
                .isInstanceOf(RedisCounterException.class)
                .hasMessageContaining("Failed to batch increment counters");
    }

    @Test
    @DisplayName("批量计数 - Redis 返回 null 应抛异常")
    void testBatchIncrement_redisReturnsNull_shouldThrowException() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        keyTtlMap.put("key1", 60L);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> executor.batchIncrement(keyTtlMap))
                .isInstanceOf(RedisCounterException.class)
                .hasMessageContaining("Failed to batch increment counters");
    }

    @Test
    @DisplayName("批量计数 - Redis 异常应抛 RedisCounterException")
    void testBatchIncrement_redisException_shouldThrowRedisCounterException() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        keyTtlMap.put("key1", 60L);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> executor.batchIncrement(keyTtlMap))
                .isInstanceOf(RedisCounterException.class)
                .hasMessageContaining("Failed to batch increment counters")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("批量计数 - 验证 Key 顺序保持一致")
    void testBatchIncrement_keyOrderPreserved_shouldMaintainOrder() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        keyTtlMap.put("key1", 60L);
        keyTtlMap.put("key2", 120L);
        keyTtlMap.put("key3", 30L);

        List<Long> mockResults = Arrays.asList(10L, 20L, 30L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        // When
        Map<String, Long> results = executor.batchIncrement(keyTtlMap);

        // Then - 验证返回顺序与输入顺序一致
        List<String> resultKeys = new ArrayList<>(results.keySet());
        assertThat(resultKeys).containsExactly("key1", "key2", "key3");
        assertThat(results.get("key1")).isEqualTo(10L);
        assertThat(results.get("key2")).isEqualTo(20L);
        assertThat(results.get("key3")).isEqualTo(30L);
    }

    @Test
    @DisplayName("健康检查 - Redis 可用应返回 true")
    void testIsAvailable_redisAvailable_shouldReturnTrue() {
        // Given
        when(valueOperations.get(anyString())).thenReturn("ok");

        // When
        boolean available = executor.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("健康检查 - Redis 不可用应返回 false")
    void testIsAvailable_redisUnavailable_shouldReturnFalse() {
        // Given
        when(valueOperations.get(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        // When
        boolean available = executor.isAvailable();

        // Then
        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("性能测试 - 批量比单次调用更高效")
    void testPerformance_batchVsSingle_batchShouldBeFaster() {
        // Given
        Map<String, Long> keyTtlMap = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            keyTtlMap.put("key" + i, 60L);
        }

        // 模拟批量返回
        List<Long> mockResults = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            mockResults.add((long) i);
        }
        when(redisTemplate.execute(any(RedisScript.class), anyList(), 
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        // When
        executor.batchIncrement(keyTtlMap);

        // Then - 验证只调用了 1 次（而不是 10 次）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), 
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("边界值测试 - TTL 为 0 应正常处理")
    void testIncrement_zeroTtl_shouldWork() {
        // Given
        String key = "test:counter";
        long ttl = 0L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        // When
        long result = executor.increment(key, ttl);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(redisTemplate, times(1)).execute(
                any(RedisScript.class),
                anyList(),
                eq("0")
        );
    }

    @Test
    @DisplayName("边界值测试 - 大 TTL 值应正常处理")
    void testIncrement_largeTtl_shouldWork() {
        // Given
        String key = "test:counter";
        long ttl = 86400L; // 1 天
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        // When
        long result = executor.increment(key, ttl);

        // Then
        assertThat(result).isEqualTo(1L);
        verify(redisTemplate, times(1)).execute(
                any(RedisScript.class),
                anyList(),
                eq("86400")
        );
    }
}
