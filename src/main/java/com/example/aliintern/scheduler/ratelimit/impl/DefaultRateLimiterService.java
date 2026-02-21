package com.example.aliintern.scheduler.ratelimit.impl;

import com.example.aliintern.scheduler.common.redis.RedisCounterException;
import com.example.aliintern.scheduler.common.redis.RedisCounterExecutor;
import com.example.aliintern.scheduler.config.SchedulerProperties;
import com.example.aliintern.scheduler.config.SchedulerProperties.RateLimitConfig;
import com.example.aliintern.scheduler.ratelimit.RateLimitExceededException;
import com.example.aliintern.scheduler.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 RedisCounterExecutor 的限流服务实现
 * 
 * 本模块是调度系统的"第一道防线"，用于保护系统免受突发流量冲击。
 * 
 * 实现特点：
 * - 使用 RedisCounterExecutor 统一计数层
 * - 支持批量限流检查（一次 Redis 调用）
 * - 支持三个限流维度：全局 / bizType / bizKey
 * - 支持开关控制和容错降级
 * 
 * Key 格式：
 * - 全局：{prefix}:global:{window}
 * - bizType：{prefix}:biztype:{bizType}:{window}
 * - bizKey：{prefix}:bizkey:{bizType}:{bizKey}:{window}
 * 
 * 性能优化：
 * - 原来：3 次 Redis 调用（global + bizType + bizKey）
 * - 现在：1 次 Redis 调用（批量执行）
 * 
 * ⚠️ 职责边界：
 * - 只负责判断请求是否允许进入系统
 * - 不读取访问统计数据
 * - 不判断热点
 * - 不修改缓存策略
 * - 不与 DispatchDecision 耦合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultRateLimiterService implements RateLimiterService {

    private final RedisCounterExecutor redisCounterExecutor;
    private final SchedulerProperties schedulerProperties;

    /**
     * 获取限流配置
     */
    private RateLimitConfig getConfig() {
        return schedulerProperties.getRateLimit();
    }

    @Override
    public void acquire(String bizType, String bizKey) throws RateLimitExceededException {
        RateLimitConfig config = getConfig();
        long windowSeconds = config.getWindowSeconds();

        // 构建需要检查的所有维度的 Key
        Map<String, Long> keysToCheck = new LinkedHashMap<>();
        
        // 1. 全局限流（若开启）
        if (Boolean.TRUE.equals(config.getEnableGlobalLimit())) {
            keysToCheck.put(buildGlobalKey(windowSeconds), windowSeconds);
        }
        
        // 2. bizType 限流（若开启）
        if (Boolean.TRUE.equals(config.getEnableBizTypeLimit()) && bizType != null) {
            keysToCheck.put(buildBizTypeKey(bizType, windowSeconds), windowSeconds);
        }
        
        // 3. bizKey 限流（若开启）
        if (Boolean.TRUE.equals(config.getEnableBizKeyLimit()) && bizType != null && bizKey != null) {
            keysToCheck.put(buildBizKeyKey(bizType, bizKey, windowSeconds), windowSeconds);
        }

        // 如果没有开启任何维度，直接放行
        if (keysToCheck.isEmpty()) {
            log.debug("All rate limit dimensions are disabled, allowing request");
            return;
        }

        try {
            // 批量执行所有维度的限流检查（一次 Redis 调用）
            Map<String, Long> results = redisCounterExecutor.batchIncrement(keysToCheck);
            
            log.debug("Rate limit check results: {}", results);

            // 检查各维度是否超限
            checkLimitExceeded("GLOBAL", buildGlobalKey(windowSeconds), 
                    results.get(buildGlobalKey(windowSeconds)), config.getGlobalQpsLimit(), null, null);
            
            if (bizType != null) {
                checkLimitExceeded("BIZ_TYPE", buildBizTypeKey(bizType, windowSeconds),
                        results.get(buildBizTypeKey(bizType, windowSeconds)), config.getBizTypeQpsLimit(), bizType, null);
            }
            
            if (bizType != null && bizKey != null) {
                checkLimitExceeded("BIZ_KEY", buildBizKeyKey(bizType, bizKey, windowSeconds),
                        results.get(buildBizKeyKey(bizType, bizKey, windowSeconds)), config.getBizKeyQpsLimit(), bizType, bizKey);
            }

        } catch (RateLimitExceededException e) {
            // 限流异常直接抛出
            throw e;
        } catch (RedisCounterException e) {
            // Redis 异常，按配置决定行为
            log.error("Redis error during rate limit check: {}", e.getMessage());
            handleRedisFailure();
        }
    }

    /**
     * 检查是否超限
     *
     * @param dimension     限流维度
     * @param key           Redis key
     * @param currentCount  当前计数
     * @param limit         限流阈值
     * @param bizType       业务类型
     * @param bizKey        业务键
     * @throws RateLimitExceededException 超限时抛出
     */
    private void checkLimitExceeded(String dimension, String key, Long currentCount, int limit,
                                     String bizType, String bizKey) throws RateLimitExceededException {
        if (currentCount == null) {
            return; // 该维度未启用
        }

        if (currentCount > limit) {
            log.warn("Rate limit exceeded: dimension={}, key={}, current={}, limit={}", 
                    dimension, key, currentCount, limit);
            throw new RateLimitExceededException(dimension, bizType, bizKey, currentCount, limit);
        }
    }

    /**
     * 处理 Redis 故障
     * 根据 failOpen 配置决定是放行还是拒绝
     */
    private void handleRedisFailure() throws RateLimitExceededException {
        RateLimitConfig config = getConfig();
        
        if (Boolean.TRUE.equals(config.getFailOpen())) {
            // fail-open：放行
            log.warn("Redis unavailable, fail-open: allowing request");
        } else {
            // fail-closed：拒绝
            log.warn("Redis unavailable, fail-closed: rejecting request");
            throw new RateLimitExceededException("REDIS_FAILURE", null, null, -1, -1);
        }
    }

    /**
     * 构建全局限流 Key
     * 格式：{prefix}:global:{window}
     */
    private String buildGlobalKey(long windowSeconds) {
        String prefix = getConfig().getKeyPrefix();
        long window = System.currentTimeMillis() / (windowSeconds * 1000);
        return String.format("%s:global:%d", prefix, window);
    }

    /**
     * 构建 bizType 限流 Key
     * 格式：{prefix}:biztype:{bizType}:{window}
     */
    private String buildBizTypeKey(String bizType, long windowSeconds) {
        String prefix = getConfig().getKeyPrefix();
        long window = System.currentTimeMillis() / (windowSeconds * 1000);
        return String.format("%s:biztype:%s:%d", prefix, bizType, window);
    }

    /**
     * 构建 bizKey 限流 Key
     * 格式：{prefix}:bizkey:{bizType}:{bizKey}:{window}
     */
    private String buildBizKeyKey(String bizType, String bizKey, long windowSeconds) {
        String prefix = getConfig().getKeyPrefix();
        long window = System.currentTimeMillis() / (windowSeconds * 1000);
        return String.format("%s:bizkey:%s:%s:%d", prefix, bizType, bizKey, window);
    }
}
