package com.example.aliintern.scheduler.statistics;

import com.example.aliintern.scheduler.common.model.StatResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 访问统计模块集成测试
 * 
 * 前提条件：需要本地运行 Redis 服务（localhost:6379）
 * 
 * 测试内容：
 * 1. 单次访问记录
 * 2. 多次访问累加
 * 3. 双窗口统计正确性
 * 4. 参数校验
 * 
 * 注意：
 * - count1s 表示短窗口计数（当前配置为 1 秒）
 * - count60s 表示长窗口计数（当前配置为 60 秒）
 * - 字段名是固定的，实际窗口时长由配置文件决定
 */
@SpringBootTest
class RedisAccessStatisticsServiceTest {

    @Autowired
    private AccessStatisticsService statisticsService;

    @Test
    @DisplayName("测试单次访问记录")
    void testSingleRecord() {
        // 使用唯一key避免测试间干扰
        String bizType = "test";
        String bizKey = "single_" + System.currentTimeMillis();

        StatResult result = statisticsService.record(bizType, bizKey);

        assertNotNull(result, "结果不应为null");
        assertEquals(1L, result.getCount1s(), "短窗口计数应为1");
        assertEquals(1L, result.getCount60s(), "长窗口计数应为1");
    }

    @Test
    @DisplayName("测试多次访问累加")
    void testMultipleRecords() {
        String bizType = "test";
        String bizKey = "multiple_" + System.currentTimeMillis();

        // 连续记录5次
        StatResult result = null;
        for (int i = 0; i < 5; i++) {
            result = statisticsService.record(bizType, bizKey);
        }

        assertNotNull(result);
        assertEquals(5L, result.getCount1s(), "短窗口计数应为5");
        assertEquals(5L, result.getCount60s(), "长窗口计数应为5");
    }

    @Test
    @DisplayName("测试不同业务类型隔离")
    void testBizTypeIsolation() {
        String bizKey = "isolation_" + System.currentTimeMillis();

        // 对不同业务类型记录
        StatResult productResult = statisticsService.record("product", bizKey);
        StatResult orderResult = statisticsService.record("order", bizKey);

        // 应该各自独立计数
        assertEquals(1L, productResult.getCount1s());
        assertEquals(1L, orderResult.getCount1s());
    }

    @Test
    @DisplayName("测试空参数处理")
    void testNullParameters() {
        StatResult result1 = statisticsService.record(null, "key");
        StatResult result2 = statisticsService.record("type", null);
        StatResult result3 = statisticsService.record("", "key");

        // 应返回空结果而不是抛异常
        assertEquals(0L, result1.getCount1s());
        assertEquals(0L, result2.getCount1s());
        assertEquals(0L, result3.getCount1s());
    }

    @Test
    @DisplayName("测试高并发场景模拟")
    void testConcurrentAccess() throws InterruptedException {
        String bizType = "test";
        String bizKey = "concurrent_" + System.currentTimeMillis();
        int threadCount = 10;
        int recordsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    statisticsService.record(bizType, bizKey);
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 获取最终统计（可能部分在60秒窗口内）
        StatResult result = statisticsService.record(bizType, bizKey);
        
        // 60秒窗口应该累加所有记录（+1是最后一次record）
        long expectedTotal = threadCount * recordsPerThread + 1;
        assertEquals(expectedTotal, result.getCount60s(), 
                "60秒窗口应正确累加所有并发请求");
    }

    @Test
    @DisplayName("测试短窗口过期")
    void testWindowExpiration() throws InterruptedException {
        String bizType = "test";
        String bizKey = "expire_" + System.currentTimeMillis();

        // 第一次记录
        StatResult result1 = statisticsService.record(bizType, bizKey);
        assertEquals(1L, result1.getCount1s());

        // 等待超过短窗口时间（1秒 + buffer）
        Thread.sleep(1100);

        // 第二次记录 - 短窗口应重新计数
        StatResult result2 = statisticsService.record(bizType, bizKey);
        assertEquals(1L, result2.getCount1s(), "短窗口过期后应重新从1开始");
        assertEquals(2L, result2.getCount60s(), "长窗口应继续累加");
    }
}
