package com.example.aliintern.scheduler.common.model;

import com.example.aliintern.scheduler.common.enums.HotspotLevel;
import lombok.Builder;
import lombok.Data;

/**
 * 请求上下文
 * 封装请求在调度层各模块间传递的信息
 */
@Data
@Builder
public class RequestContext {

    /**
     * 请求唯一标识
     */
    private String requestId;

    /**
     * 业务类型（如 order, inventory, user）
     * 用于限流和统计的业务隔离
     */
    private String bizType;

    /**
     * 缓存键（如 skuId）
     */
    private String cacheKey;

    /**
     * 热点等级
     */
    private HotspotLevel hotspotLevel;

    /**
     * 缓存TTL（秒）
     */
    private Long cacheTtl;

    /**
     * 是否允许缓存
     */
    private Boolean cacheAllowed;

    /**
     * 请求时间戳
     */
    private Long timestamp;

    /**
     * 用户ID（可选，用于个性化请求）
     */
    private String userId;

    /**
     * 请求来源（如 Web、App、API）
     */
    private String source;
}
