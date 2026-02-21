package com.example.aliintern.scheduler.statistics.impl;

import com.example.aliintern.scheduler.common.model.StatResult;
import com.example.aliintern.scheduler.common.redis.RedisCounterException;
import com.example.aliintern.scheduler.common.redis.RedisCounterExecutor;
import com.example.aliintern.scheduler.config.SchedulerProperties;
import com.example.aliintern.scheduler.statistics.AccessStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 访问统计模块实现
 * 使用 RedisCounterExecutor 统一计数层实现高并发、原子性的访问计数
 * 
 * 设计要点：
 * 1. 双窗口统计：短窗口（瞬时热点）+ 长窗口（稳定热度）
 * 2. Key格式：{keyPrefix}:{bizType}:{bizKey}:{window}
 * 3. 使用 RedisCounterExecutor 批量执行，消除重复 Lua 逻辑
 * 4. 不使用本地内存，支持多实例部署
 * 5. 完整的容错机制：超时、重试、降级
 * 
 * 性能优化：
 * - 原来：2 次 Redis 调用（shortWindow + longWindow）
 * - 现在：1 次 Redis 调用（批量执行）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisAccessStatisticsService implements AccessStatisticsService {

    private final RedisCounterExecutor redisCounterExecutor;
    private final SchedulerProperties schedulerProperties;

    @Override
    public StatResult record(String bizType, String bizKey) {
        if (bizType == null || bizType.isEmpty() || bizKey == null || bizKey.isEmpty()) {
            log.warn("无效的统计参数: bizType={}, bizKey={}", bizType, bizKey);
            return StatResult.empty();
        }

        SchedulerProperties.StatConfig config = schedulerProperties.getStat();

        try {
            // 构建双窗口的 Redis Key
            String keyShort = buildStatKey(bizType, bizKey, formatWindowKey(config.getShortWindowSeconds()));
            String keyLong = buildStatKey(bizType, bizKey, config.getLongWindowSeconds() + "s");

            // 计算过期时间（向上取整）
            long ttlShort = (long) Math.ceil(config.getShortWindowSeconds());
            long ttlLong = config.getLongWindowSeconds().longValue();

            // 构建批量执行 Map
            Map<String, Long> keyTtlMap = new LinkedHashMap<>();
            keyTtlMap.put(keyShort, ttlShort);
            keyTtlMap.put(keyLong, ttlLong);

            // 批量执行（一次 Redis 调用）
            Map<String, Long> results = redisCounterExecutor.batchIncrement(keyTtlMap);

            Long countShort = results.get(keyShort);
            Long countLong = results.get(keyLong);

            log.debug("访问统计记录完成: bizType={}, bizKey={}, countShort={}, countLong={}", 
                    bizType, bizKey, countShort, countLong);

            return StatResult.of(countShort, countLong);
            
        } catch (RedisCounterException e) {
            log.error("访问统计记录失败: bizType={}, bizKey={}, error={}", 
                    bizType, bizKey, e.getMessage(), e);
            
            // 根据降级开关决定是否返回空结果
            if (config.getFallbackEnabled()) {
                log.warn("访问统计降级生效，返回空结果");
                return StatResult.empty();
            } else {
                throw new RuntimeException("访问统计失败且降级未开启", e);
            }
        }
    }

    /**
     * 格式化窗口Key（支持小数）
     * 例如：2.0 -> "2s", 0.5 -> "0.5s"
     */
    private String formatWindowKey(Double seconds) {
        if (seconds == seconds.intValue()) {
            return seconds.intValue() + "s";
        }
        return seconds + "s";
    }

    /**
     * 构建统计Key
     * 格式：{keyPrefix}:{bizType}:{bizKey}:{window}
     * 示例：stat:product:12345:2s
     */
    private String buildStatKey(String bizType, String bizKey, String window) {
        String keyPrefix = schedulerProperties.getStat().getKeyPrefix();
        return String.format("%s:%s:%s:%s", keyPrefix, bizType, bizKey, window);
    }
}
