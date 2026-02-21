package com.example.aliintern.scheduler.controller;

import com.example.aliintern.scheduler.config.SchedulerProperties;
import com.example.aliintern.scheduler.ratelimit.RateLimitExceededException;
import com.example.aliintern.scheduler.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流模块测试 Controller
 * 
 * 提供限流模块的独立测试接口，用于验证：
 * - 三个维度的限流（全局 / bizType / bizKey）
 * - 限流阈值配置生效
 * - Redis 容错降级
 * - 并发压力测试
 */
@Slf4j
@RestController
@RequestMapping("/test/ratelimit")
@RequiredArgsConstructor
public class RateLimitTestController {

    private final RateLimiterService rateLimiterService;
    private final SchedulerProperties schedulerProperties;

    /**
     * 单次限流测试
     * 测试单个请求是否被限流
     * 
     * @param bizType 业务类型（可选）
     * @param bizKey  业务键（可选）
     * @return 限流结果
     */
    @GetMapping("/acquire")
    public ResponseEntity<Map<String, Object>> testAcquire(
            @RequestParam(required = false, defaultValue = "order") String bizType,
            @RequestParam(required = false, defaultValue = "sku_001") String bizKey) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("bizType", bizType);
        result.put("bizKey", bizKey);
        
        try {
            long start = System.currentTimeMillis();
            rateLimiterService.acquire(bizType, bizKey);
            long cost = System.currentTimeMillis() - start;
            
            result.put("allowed", true);
            result.put("costMs", cost);
            result.put("message", "Request allowed");
            
            return ResponseEntity.ok(result);
            
        } catch (RateLimitExceededException e) {
            result.put("allowed", false);
            result.put("dimension", e.getDimension());
            result.put("currentCount", e.getCurrentCount());
            result.put("limit", e.getLimit());
            result.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }
    }

    /**
     * 并发压力测试
     * 模拟并发请求，统计通过和拒绝的数量
     * 
     * @param bizType     业务类型
     * @param bizKey      业务键
     * @param concurrency 并发数
     * @param requests    每个并发的请求数
     * @return 测试统计结果
     */
    @GetMapping("/pressure")
    public ResponseEntity<Map<String, Object>> pressureTest(
            @RequestParam(required = false, defaultValue = "order") String bizType,
            @RequestParam(required = false, defaultValue = "sku_001") String bizKey,
            @RequestParam(required = false, defaultValue = "10") int concurrency,
            @RequestParam(required = false, defaultValue = "100") int requests) {
        
        Map<String, Object> result = new HashMap<>();
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        int totalRequests = concurrency * requests;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    rateLimiterService.acquire(bizType, bizKey);
                    allowedCount.incrementAndGet();
                } catch (RateLimitExceededException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Unexpected error: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        executor.shutdown();
        
        result.put("bizType", bizType);
        result.put("bizKey", bizKey);
        result.put("totalRequests", totalRequests);
        result.put("concurrency", concurrency);
        result.put("allowedCount", allowedCount.get());
        result.put("rejectedCount", rejectedCount.get());
        result.put("errorCount", errorCount.get());
        result.put("durationMs", endTime - startTime);
        result.put("qps", totalRequests * 1000.0 / (endTime - startTime));
        
        // 计算拒绝率
        double rejectRate = totalRequests > 0 ? 
                (double) rejectedCount.get() / totalRequests * 100 : 0;
        result.put("rejectRate", String.format("%.2f%%", rejectRate));
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前限流配置
     * 
     * @return 限流配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();
        
        SchedulerProperties.RateLimitConfig config = schedulerProperties.getRateLimit();
        
        result.put("enableGlobalLimit", config.getEnableGlobalLimit());
        result.put("enableBizTypeLimit", config.getEnableBizTypeLimit());
        result.put("enableBizKeyLimit", config.getEnableBizKeyLimit());
        result.put("globalQpsLimit", config.getGlobalQpsLimit());
        result.put("bizTypeQpsLimit", config.getBizTypeQpsLimit());
        result.put("bizKeyQpsLimit", config.getBizKeyQpsLimit());
        result.put("windowSeconds", config.getWindowSeconds());
        result.put("keyPrefix", config.getKeyPrefix());
        result.put("failOpen", config.getFailOpen());
        result.put("redisTimeout", config.getRedisTimeout());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 测试不同维度的限流
     * 分别测试全局、bizType、bizKey三个维度
     * 
     * @return 各维度测试结果
     */
    @GetMapping("/dimensions")
    public ResponseEntity<Map<String, Object>> testDimensions() {
        Map<String, Object> result = new HashMap<>();
        
        // 测试全局限流（使用不同的 bizType 和 bizKey）
        Map<String, Object> globalTest = new HashMap<>();
        int globalAllowed = 0;
        int globalRejected = 0;
        for (int i = 0; i < 50; i++) {
            try {
                rateLimiterService.acquire("type_" + i, "key_" + i);
                globalAllowed++;
            } catch (RateLimitExceededException e) {
                if ("GLOBAL".equals(e.getDimension())) {
                    globalRejected++;
                }
            }
        }
        globalTest.put("allowed", globalAllowed);
        globalTest.put("rejected", globalRejected);
        result.put("globalDimension", globalTest);
        
        // 测试 bizType 限流（固定 bizType，不同 bizKey）
        Map<String, Object> bizTypeTest = new HashMap<>();
        int bizTypeAllowed = 0;
        int bizTypeRejected = 0;
        String testBizType = "test_biz_type_" + System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            try {
                rateLimiterService.acquire(testBizType, "key_" + i);
                bizTypeAllowed++;
            } catch (RateLimitExceededException e) {
                if ("BIZ_TYPE".equals(e.getDimension())) {
                    bizTypeRejected++;
                }
            }
        }
        bizTypeTest.put("bizType", testBizType);
        bizTypeTest.put("allowed", bizTypeAllowed);
        bizTypeTest.put("rejected", bizTypeRejected);
        result.put("bizTypeDimension", bizTypeTest);
        
        // 测试 bizKey 限流（固定 bizType 和 bizKey）
        Map<String, Object> bizKeyTest = new HashMap<>();
        int bizKeyAllowed = 0;
        int bizKeyRejected = 0;
        String testBizKey = "test_biz_key_" + System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            try {
                rateLimiterService.acquire("order", testBizKey);
                bizKeyAllowed++;
            } catch (RateLimitExceededException e) {
                if ("BIZ_KEY".equals(e.getDimension())) {
                    bizKeyRejected++;
                }
            }
        }
        bizKeyTest.put("bizKey", testBizKey);
        bizKeyTest.put("allowed", bizKeyAllowed);
        bizKeyTest.put("rejected", bizKeyRejected);
        result.put("bizKeyDimension", bizKeyTest);
        
        return ResponseEntity.ok(result);
    }
}
