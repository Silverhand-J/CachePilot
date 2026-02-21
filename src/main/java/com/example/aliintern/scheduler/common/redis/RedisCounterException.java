package com.example.aliintern.scheduler.common.redis;

/**
 * Redis 计数器执行异常
 * 
 * 当 Redis 不可用或执行失败时抛出此异常。
 * 上层服务应捕获此异常并根据业务需求决定降级策略。
 */
public class RedisCounterException extends RuntimeException {

    public RedisCounterException(String message) {
        super(message);
    }

    public RedisCounterException(String message, Throwable cause) {
        super(message, cause);
    }
}
