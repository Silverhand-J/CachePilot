package com.example.aliintern.scheduler.cache.client;

import com.example.aliintern.scheduler.config.SchedulerProperties;
import com.example.aliintern.scheduler.config.SchedulerProperties.CacheConfig.TtlConfig.TtlLevelConfig;
import com.example.aliintern.scheduler.common.enums.CacheTtlLevel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存客户端
 * 基于 Caffeine 实现的本地缓存访问封装
 * 
 * 职责：
 * - 提供本地缓存读写接口
 * - 根据 TTL 等级使用不同的缓存实例（SHORT/NORMAL/LONG）
 * - 异常容错，不影响主流程
 * 
 * 设计说明：
 * - 使用三个独立的 Caffeine 实例，分别对应 SHORT/NORMAL/LONG 三种 TTL
 * - 每个实例的过期时间由配置文件决定
 * - 查询时会遍历所有缓存实例
 */
@Slf4j
@Component
public class LocalCacheClient {

    /**
     * 三个 TTL 级别的缓存实例
     */
    private final EnumMap<CacheTtlLevel, Cache<String, Object>> caches;
    private final SchedulerProperties schedulerProperties;

    public LocalCacheClient(SchedulerProperties schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
        this.caches = new EnumMap<>(CacheTtlLevel.class);
        
        TtlLevelConfig localTtlConfig = schedulerProperties.getCache().getTtl().getLocal();
        
        // 初始化 SHORT TTL 缓存
        caches.put(CacheTtlLevel.SHORT, Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(localTtlConfig.getShortTtl(), TimeUnit.SECONDS)
                .recordStats()
                .build());
        
        // 初始化 NORMAL TTL 缓存
        caches.put(CacheTtlLevel.NORMAL, Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(localTtlConfig.getNormalTtl(), TimeUnit.SECONDS)
                .recordStats()
                .build());
        
        // 初始化 LONG TTL 缓存
        caches.put(CacheTtlLevel.LONG, Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(localTtlConfig.getLongTtl(), TimeUnit.SECONDS)
                .recordStats()
                .build());
        
        log.info("本地缓存初始化完成: SHORT={}s, NORMAL={}s, LONG={}s",
                localTtlConfig.getShortTtl(), localTtlConfig.getNormalTtl(), localTtlConfig.getLongTtl());
    }

    /**
     * 从本地缓存获取数据
     * 会遍历所有 TTL 级别的缓存查找
     * 
     * @param key 缓存键
     * @return 缓存值，未命中返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (key == null) {
            return null;
        }
        
        try {
            // 遍历所有缓存实例查找
            for (CacheTtlLevel level : CacheTtlLevel.values()) {
                Object value = caches.get(level).getIfPresent(key);
                if (value != null) {
                    log.debug("本地缓存命中: key={}, ttlLevel={}", key, level);
                    return (T) value;
                }
            }
            log.debug("本地缓存未命中: key={}", key);
            return null;
        } catch (Exception e) {
            log.warn("本地缓存读取异常: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写入本地缓存
     * 根据 ttlLevel 写入对应的缓存实例
     * 
     * @param key      缓存键
     * @param value    缓存值
     * @param ttlLevel TTL 等级（SHORT/NORMAL/LONG）
     */
    public void put(String key, Object value, CacheTtlLevel ttlLevel) {
        if (key == null || value == null) {
            return;
        }
        
        CacheTtlLevel level = (ttlLevel != null) ? ttlLevel : CacheTtlLevel.NORMAL;
        
        try {
            // 先从其他级别删除，避免重复缓存
            for (CacheTtlLevel l : CacheTtlLevel.values()) {
                if (l != level) {
                    caches.get(l).invalidate(key);
                }
            }
            // 写入对应级别的缓存
            caches.get(level).put(key, value);
            log.debug("本地缓存写入成功: key={}, ttlLevel={}, ttl={}s", 
                    key, level, getTtlSeconds(level));
        } catch (Exception e) {
            log.warn("本地缓存写入失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 删除本地缓存
     * 从所有 TTL 级别中删除
     * 
     * @param key 缓存键
     */
    public void invalidate(String key) {
        if (key == null) {
            return;
        }
        
        try {
            for (CacheTtlLevel level : CacheTtlLevel.values()) {
                caches.get(level).invalidate(key);
            }
            log.debug("本地缓存删除: key={}", key);
        } catch (Exception e) {
            log.warn("本地缓存删除失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取指定级别的 TTL 秒数
     */
    private long getTtlSeconds(CacheTtlLevel level) {
        TtlLevelConfig config = schedulerProperties.getCache().getTtl().getLocal();
        return switch (level) {
            case SHORT -> config.getShortTtl();
            case NORMAL -> config.getNormalTtl();
            case LONG -> config.getLongTtl();
        };
    }

    /**
     * 获取缓存统计信息
     */
    public String getStats() {
        StringBuilder sb = new StringBuilder();
        for (CacheTtlLevel level : CacheTtlLevel.values()) {
            sb.append(level.name()).append(": ").append(caches.get(level).stats()).append("\n");
        }
        return sb.toString();
    }
}

