package com.example.aliintern.scheduler.common.redis;

import java.util.Map;

/**
 * Redis 计数器统一执行层
 * 
 * 本层是限流层和访问统计层共享的底层能力，用于：
 * - 统一 Redis 计数操作（INCR + EXPIRE）
 * - 消除重复 Lua 执行逻辑
 * - 控制 Redis QPS
 * - 提供批量执行能力
 * 
 * 职责边界：
 * - 只负责原子性计数
 * - 不感知业务语义（限流 / 统计）
 * - 不决定是否拒绝请求
 * - 不参与热点识别
 * 
 * 架构分层：
 * <pre>
 * RateLimiterService          AccessStatisticsService
 *         ↓                              ↓
 *         └──────────────┬───────────────┘
 *                        ↓
 *              RedisCounterExecutor（本层）
 *                        ↓
 *                   Redis + Lua
 * </pre>
 */
public interface RedisCounterExecutor {

    /**
     * 单 Key 计数（原子性 INCR + EXPIRE）
     * 
     * 使用 Lua 脚本保证原子性：
     * 1. INCR key
     * 2. 如果首次创建（返回值为 1），设置过期时间
     * 3. 返回当前计数
     * 
     * @param key        Redis key
     * @param ttlSeconds 过期时间（秒）
     * @return 当前计数值
     * @throws RedisCounterException 当 Redis 不可用时抛出
     */
    long increment(String key, long ttlSeconds) throws RedisCounterException;

    /**
     * 批量 Key 计数（一次 Lua 调用完成多个计数）
     * 
     * 适用于需要同时统计多个维度的场景，例如：
     * - 限流的 global + bizType + bizKey
     * - 统计的 shortWindow + longWindow
     * 
     * 优势：
     * - 减少 Redis 网络往返次数
     * - 降低 Redis QPS
     * - 保证原子性
     * 
     * @param keyTtlMap Key -> TTL 映射（单位：秒）
     * @return Key -> Count 映射（当前计数值）
     * @throws RedisCounterException 当 Redis 不可用时抛出
     */
    Map<String, Long> batchIncrement(Map<String, Long> keyTtlMap) throws RedisCounterException;

    /**
     * 检查 Redis 是否可用
     * 
     * @return true = 可用，false = 不可用
     */
    boolean isAvailable();
}
