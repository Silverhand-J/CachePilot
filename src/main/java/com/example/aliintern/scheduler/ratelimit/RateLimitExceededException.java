package com.example.aliintern.scheduler.ratelimit;

/**
 * 限流超限异常
 * 
 * 当请求超过限流阈值时抛出此异常。
 * 调用方应捕获此异常并返回适当的错误响应（如 HTTP 429 Too Many Requests）。
 * 
 * 包含限流维度信息，便于问题定位和监控。
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * 限流维度（GLOBAL / BIZ_TYPE / BIZ_KEY）
     */
    private final String dimension;

    /**
     * 业务类型（可为空）
     */
    private final String bizType;

    /**
     * 业务键（可为空）
     */
    private final String bizKey;

    /**
     * 当前计数
     */
    private final long currentCount;

    /**
     * 限流阈值
     */
    private final long limit;

    /**
     * 构造函数
     *
     * @param dimension    限流维度
     * @param bizType      业务类型
     * @param bizKey       业务键
     * @param currentCount 当前计数
     * @param limit        限流阈值
     */
    public RateLimitExceededException(String dimension, String bizType, String bizKey,
                                       long currentCount, long limit) {
        super(String.format("Rate limit exceeded: dimension=%s, bizType=%s, bizKey=%s, current=%d, limit=%d",
                dimension, bizType, bizKey, currentCount, limit));
        this.dimension = dimension;
        this.bizType = bizType;
        this.bizKey = bizKey;
        this.currentCount = currentCount;
        this.limit = limit;
    }

    /**
     * 简化构造函数（仅维度）
     *
     * @param dimension    限流维度
     * @param currentCount 当前计数
     * @param limit        限流阈值
     */
    public RateLimitExceededException(String dimension, long currentCount, long limit) {
        this(dimension, null, null, currentCount, limit);
    }

    public String getDimension() {
        return dimension;
    }

    public String getBizType() {
        return bizType;
    }

    public String getBizKey() {
        return bizKey;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public long getLimit() {
        return limit;
    }
}
