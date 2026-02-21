package com.example.aliintern.scheduler.ratelimit;

/**
 * 限流服务接口
 * 
 * 本模块是调度系统的"第一道防线"，用于保护系统免受突发流量冲击。
 * 
 * ⚠️ 职责边界（必须遵守）：
 * - 本模块只负责判断请求是否允许进入系统
 * - 不读取访问统计数据
 * - 不判断热点
 * - 不修改缓存策略
 * - 不决定 TTL
 * - 不与 DispatchDecision 耦合
 * 
 * 执行顺序：
 * RateLimiter -> AccessStatistics -> HotspotDetector -> DecisionStrategyEngine
 */
public interface RateLimiterService {

    /**
     * 尝试获取访问许可
     * 
     * 按顺序检查以下限流维度（若启用）：
     * 1. 全局 QPS 限流（保护整体系统）
     * 2. bizType 限流（不同业务隔离）
     * 3. bizKey 限流（防止单 Key 被打爆）
     * 
     * 如果任一维度超限，立即抛出 RateLimitExceededException。
     * 
     * @param bizType 业务类型（如：order, inventory, user）
     * @param bizKey  业务键（如：商品ID、用户ID等）
     * @throws RateLimitExceededException 当超过任一维度的限流阈值时抛出
     */
    void acquire(String bizType, String bizKey) throws RateLimitExceededException;
}
