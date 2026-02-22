package com.example.aliintern.scheduler.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问统计结果
 * 用于返回双时间窗口的访问计数
 * 
 * 注意：
 * - count1s 和 count60s 是固定字段名，实际代表短窗口和长窗口
 * - 实际窗口时长由 application.properties 中的配置决定
 * - 当前配置：短窗口 1秒，长窗口 60秒
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatResult {

    /**
     * 短窗口计数
     * 用于判断瞬时高频访问
     * 实际时长由 scheduler.stat.short-window-seconds 配置（当前 1秒）
     */
    private Long count1s;

    /**
     * 长窗口计数
     * 用于判断稳定热度
     * 实际时长由 scheduler.stat.long-window-seconds 配置（当前 60秒）
     */
    private Long count60s;

    /**
     * 创建一个空的统计结果（计数均为0）
     */
    public static StatResult empty() {
        return StatResult.builder()
                .count1s(0L)
                .count60s(0L)
                .build();
    }

    /**
     * 创建统计结果
     *
     * @param count1s  短窗口计数
     * @param count60s 长窗口计数
     * @return StatResult实例
     */
    public static StatResult of(Long count1s, Long count60s) {
        return StatResult.builder()
                .count1s(count1s)
                .count60s(count60s)
                .build();
    }
}
