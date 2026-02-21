package com.example.aliintern.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 调度层统一配置类
 * 
 * 集中管理所有调度层相关配置，禁止创建独立的模块配置类
 * 配置前缀：scheduler
 * 
 * 包含模块：
 * - 限流模块（rateLimit）
 * - 访问统计模块（stat）
 * - 热点识别模块（hotspot）
 * - 策略决策引擎（decision）
 */
@Data
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    /**
     * 限流模块配置
     */
    private final RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 访问统计模块配置
     */
    private final StatConfig stat = new StatConfig();

    /**
     * 热点识别模块配置
     */
    private final HotspotConfig hotspot = new HotspotConfig();

    /**
     * 策略决策引擎配置
     */
    private final StrategyConfig strategy = new StrategyConfig();

    /**
     * 缓存访问代理配置（TTL 映射）
     */
    private final CacheConfig cache = new CacheConfig();

    // ==================== 限流模块配置 ====================
    
    /**
     * 限流模块配置
     * 配置前缀：scheduler.rate-limit
     * 
     * 本模块是调度系统的"第一道防线"，用于保护系统免受突发流量冲击。
     * 位于访问统计模块之前，不参与热点识别或缓存调度决策。
     */
    @Data
    public static class RateLimitConfig {
        
        // ========== 开关配置 ==========
        
        /**
         * 是否启用全局限流
         * 保护整体系统，默认开启
         */
        private Boolean enableGlobalLimit = true;
        
        /**
         * 是否启用 bizType 限流
         * 不同业务隔离，默认开启
         */
        private Boolean enableBizTypeLimit = true;
        
        /**
         * 是否启用 bizKey 限流
         * 防止单 Key 被打爆，默认开启
         */
        private Boolean enableBizKeyLimit = true;
        
        // ========== 阈值配置 ==========
        
        /**
         * 全局 QPS 限流阈值
         * 默认 10000 QPS
         */
        private Integer globalQpsLimit = 10000;
        
        /**
         * 单个 bizType QPS 限流阈值
         * 默认 5000 QPS
         */
        private Integer bizTypeQpsLimit = 5000;
        
        /**
         * 单个 bizKey QPS 限流阈值
         * 默认 1000 QPS（防止热 Key 打爆）
         */
        private Integer bizKeyQpsLimit = 1000;
        
        // ========== 窗口配置 ==========
        
        /**
         * 限流时间窗口（秒）
         * 默认 1 秒（即 QPS 限流）
         */
        private Long windowSeconds = 1L;
        
        // ========== Redis Key 配置 ==========
        
        /**
         * 限流 Key 前缀
         * Key 格式：{keyPrefix}:{dimension}:{bizType}:{bizKey}:{window}
         */
        private String keyPrefix = "ratelimit";
        
        // ========== 容错配置 ==========
        
        /**
         * Redis 不可用时的降级策略
         * true = fail-open（默认放行）
         * false = fail-closed（拒绝）
         * 默认 true，优先保证系统可用性
         */
        private Boolean failOpen = true;
        
        /**
         * Redis 操作超时时间（毫秒）
         * 防止 Redis 慢查询阻塞线程
         */
        private Integer redisTimeout = 100;
    }

    // ==================== 访问统计模块配置 ====================
    
    /**
     * 访问统计模块配置
     * 配置前缀：scheduler.stat
     */
    @Data
    public static class StatConfig {
        
        // ========== 时间窗口配置 ==========
        
        /**
         * 短窗口时长（秒）
         * 用于瞬时热点检测，默认 2 秒
         * 支持小数配置（如：0.5 表示 500ms，适用于秒杀场景）
         */
        private Double shortWindowSeconds = 2.0;
        
        /**
         * 长窗口时长（秒）
         * 用于稳定热度判断，默认 120 秒
         */
        private Integer longWindowSeconds = 120;
        
        // ========== Redis 容错配置 ==========
        
        /**
         * Redis 操作超时时间（毫秒）
         * 防止 Redis 慢查询阻塞线程，默认 3000ms
         */
        private Integer redisTimeout = 3000;
        
        /**
         * Redis 操作最大重试次数
         * 默认 2 次
         */
        private Integer maxRetries = 2;
        
        /**
         * 异常降级开关
         * 当 Redis 不可用时是否返回空结果而非抛异常
         * 默认 true（开启降级）
         */
        private Boolean fallbackEnabled = true;
        
        // ========== Key 配置 ==========
        
        /**
         * 统计 Key 前缀
         * 默认 "stat"，可按环境区分（如：stat_prod, stat_test）
         * Key格式：{keyPrefix}:{bizType}:{bizKey}:{window}
         */
        private String keyPrefix = "stat";
    }

    // ==================== 热点识别模块配置 ====================
    
    /**
     * 热点识别模块配置
     * 配置前缀：scheduler.hotspot
     */
    @Data
    public static class HotspotConfig {
        
        // ========== EXTREMELY_HOT 阈值 ==========
        
        /**
         * EXTREMELY_HOT 级别 - 短窗口阈值
         * 当 countShort >= 此值时判定为 EXTREMELY_HOT（突发流量）
         */
        private Long extremelyHotShortThreshold = 100L;
        
        /**
         * EXTREMELY_HOT 级别 - 长窗口阈值
         * 当 countLong >= 此值时判定为 EXTREMELY_HOT
         */
        private Long extremelyHotLongThreshold = 1000L;
        
        // ========== HOT 阈值 ==========
        
        /**
         * HOT 级别 - 短窗口阈值
         * 当 countShort >= 此值时判定为 HOT
         */
        private Long hotShortThreshold = 20L;
        
        /**
         * HOT 级别 - 长窗口阈值
         * 当 countLong >= 此值时判定为 HOT
         */
        private Long hotLongThreshold = 300L;
        
        // ========== WARM 阈值 ==========
        
        /**
         * WARM 级别 - 短窗口阈值
         * 当 countShort >= 此值时判定为 WARM
         */
        private Long warmShortThreshold = 5L;
        
        /**
         * WARM 级别 - 长窗口阈值
         * 当 countLong >= 此值时判定为 WARM
         */
        private Long warmLongThreshold = 60L;
    }

    // ==================== 策略决策引擎配置 ====================
    
    /**
     * 策略决策引擎配置
     * 配置前缀：scheduler.strategy
     */
    @Data
    public static class StrategyConfig {
        
        // ========== COLD 级别策略 ==========
        
        /**
         * COLD 级别 - 缓存模式
         * 默认 NONE（不缓存）
         */
        private String coldCacheMode = "NONE";
        
        /**
         * COLD 级别 - TTL 等级
         * 默认 SHORT
         */
        private String coldTtlLevel = "SHORT";
        
        // ========== WARM 级别策略 ==========
        
        /**
         * WARM 级别 - 缓存模式
         * 默认 REMOTE_ONLY（仅 Redis）
         */
        private String warmCacheMode = "REMOTE_ONLY";
        
        /**
         * WARM 级别 - TTL 等级
         * 默认 SHORT
         */
        private String warmTtlLevel = "SHORT";
        
        // ========== HOT 级别策略 ==========
        
        /**
         * HOT 级别 - 缓存模式
         * 默认 LOCAL_AND_REMOTE（本地 + Redis）
         */
        private String hotCacheMode = "LOCAL_AND_REMOTE";
        
        /**
         * HOT 级别 - TTL 等级
         * 默认 NORMAL
         */
        private String hotTtlLevel = "NORMAL";
        
        // ========== EXTREMELY_HOT 级别策略 ==========
        
        /**
         * EXTREMELY_HOT 级别 - 缓存模式
         * 默认 LOCAL_AND_REMOTE（本地 + Redis）
         */
        private String extremelyHotCacheMode = "LOCAL_AND_REMOTE";
        
        /**
         * EXTREMELY_HOT 级别 - TTL 等级
         * 默认 LONG
         */
        private String extremelyHotTtlLevel = "LONG";
    }

    // ==================== 缓存访问代理配置 ====================
    
    /**
     * 缓存访问代理配置
     * 配置前缀：scheduler.cache
     */
    @Data
    public static class CacheConfig {
        
        /**
         * TTL 配置
         */
        private final TtlConfig ttl = new TtlConfig();
        
        /**
         * TTL 配置类
         */
        @Data
        public static class TtlConfig {
            
            /**
             * 本地缓存 TTL 配置（秒）
             */
            private TtlLevelConfig local = new TtlLevelConfig(30, 60, 300);
            
            /**
             * Redis TTL 配置（秒）
             */
            private TtlLevelConfig remote = new TtlLevelConfig(60, 120, 600);
            
            /**
             * TTL 级别配置
             */
            @Data
            public static class TtlLevelConfig {
                private long shortTtl;   // SHORT 级别 TTL
                private long normalTtl;  // NORMAL 级别 TTL
                private long longTtl;    // LONG 级别 TTL
                
                public TtlLevelConfig() {}
                
                public TtlLevelConfig(long shortTtl, long normalTtl, long longTtl) {
                    this.shortTtl = shortTtl;
                    this.normalTtl = normalTtl;
                    this.longTtl = longTtl;
                }
            }
        }
    }
}
