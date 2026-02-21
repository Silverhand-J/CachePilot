package com.example.aliintern.scheduler.common.redis.impl;

import com.example.aliintern.scheduler.common.redis.RedisCounterException;
import com.example.aliintern.scheduler.common.redis.RedisCounterExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 计数器统一执行层实现
 * 
 * 使用 Lua 脚本保证原子性，消除限流层和统计层的重复逻辑。
 * 
 * 性能优势：
 * - 单次执行：1 次 Redis 网络往返
 * - 批量执行：多个 Key 仅 1 次 Redis 网络往返
 * 
 * Lua 脚本逻辑：
 * 1. INCR key
 * 2. 如果返回值为 1（首次创建），设置过期时间
 * 3. 返回当前计数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRedisCounterExecutor implements RedisCounterExecutor {

    private final StringRedisTemplate redisTemplate;

    /**
     * 单 Key 计数 Lua 脚本
     * 
     * KEYS[1]: Redis key
     * ARGV[1]: TTL（秒）
     * 返回值：当前计数
     */
    private static final String SINGLE_INCREMENT_SCRIPT = 
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

    /**
     * 批量 Key 计数 Lua 脚本
     * 
     * KEYS: 多个 Redis key
     * ARGV: 对应的 TTL（秒）
     * 返回值：多个计数值（数组）
     */
    private static final String BATCH_INCREMENT_SCRIPT = 
            "local results = {} " +
            "for i = 1, #KEYS do " +
            "    local current = redis.call('INCR', KEYS[i]) " +
            "    if current == 1 then " +
            "        redis.call('EXPIRE', KEYS[i], ARGV[i]) " +
            "    end " +
            "    results[i] = current " +
            "end " +
            "return results";

    private DefaultRedisScript<Long> singleIncrementScript;
    private DefaultRedisScript<List> batchIncrementScript;

    @PostConstruct
    public void init() {
        // 初始化单 Key 脚本
        singleIncrementScript = new DefaultRedisScript<>();
        singleIncrementScript.setScriptText(SINGLE_INCREMENT_SCRIPT);
        singleIncrementScript.setResultType(Long.class);

        // 初始化批量脚本
        batchIncrementScript = new DefaultRedisScript<>();
        batchIncrementScript.setScriptText(BATCH_INCREMENT_SCRIPT);
        batchIncrementScript.setResultType(List.class);

        log.info("RedisCounterExecutor initialized with Lua scripts");
    }

    @Override
    public long increment(String key, long ttlSeconds) throws RedisCounterException {
        try {
            List<String> keys = Collections.singletonList(key);
            Long result = redisTemplate.execute(singleIncrementScript, keys, String.valueOf(ttlSeconds));

            if (result == null) {
                throw new RedisCounterException("Redis returned null for key: " + key);
            }

            log.debug("Redis counter increment: key={}, count={}, ttl={}", key, result, ttlSeconds);
            return result;

        } catch (Exception e) {
            log.error("Redis counter increment failed: key={}, error={}", key, e.getMessage());
            throw new RedisCounterException("Failed to increment counter for key: " + key, e);
        }
    }

    @Override
    public Map<String, Long> batchIncrement(Map<String, Long> keyTtlMap) throws RedisCounterException {
        if (keyTtlMap == null || keyTtlMap.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 准备 KEYS 和 ARGV
            List<String> keys = new ArrayList<>(keyTtlMap.keySet());
            String[] ttls = keyTtlMap.values().stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);

            // 执行批量 Lua 脚本
            List<Long> results = redisTemplate.execute(batchIncrementScript, keys, (Object[]) ttls);

            if (results == null || results.size() != keys.size()) {
                throw new RedisCounterException("Redis batch increment returned invalid results");
            }

            // 构建返回 Map
            Map<String, Long> resultMap = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                resultMap.put(keys.get(i), results.get(i));
            }

            log.debug("Redis batch counter increment: keys={}, results={}", keys, results);
            return resultMap;

        } catch (Exception e) {
            log.error("Redis batch counter increment failed: keys={}, error={}", 
                    keyTtlMap.keySet(), e.getMessage());
            throw new RedisCounterException("Failed to batch increment counters", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.opsForValue().get("health_check");
            return true;
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}
