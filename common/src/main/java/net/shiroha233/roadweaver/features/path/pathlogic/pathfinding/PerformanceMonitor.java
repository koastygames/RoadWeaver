package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器 - 用于验证优化效果
 * 
 * 监控指标：
 * - 地形采样总次数
 * - 地形采样总耗时
 * - 平均采样耗时
 * - 缓存命中率
 */
public final class PerformanceMonitor {
    private static final AtomicLong totalSamples = new AtomicLong(0);
    private static final AtomicLong totalTimeNanos = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    
    private PerformanceMonitor() {}
    
    /**
     * 记录一次采样
     */
    public static void recordSample(long durationNanos) {
        totalSamples.incrementAndGet();
        totalTimeNanos.addAndGet(durationNanos);
    }
    
    /**
     * 记录缓存命中
     */
    public static void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    /**
     * 记录缓存未命中
     */
    public static void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    /**
     * 获取性能报告
     */
    public static String getReport() {
        long samples = totalSamples.get();
        long timeNanos = totalTimeNanos.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        
        if (samples == 0) {
            return "性能监控：无数据";
        }
        
        double avgTimeMs = (timeNanos / (double) samples) / 1_000_000.0;
        double totalTimeMs = timeNanos / 1_000_000.0;
        double hitRate = (hits / (double) (hits + misses)) * 100.0;
        
        return String.format(
            "性能监控：\n" +
            "  总采样次数: %d\n" +
            "  总耗时: %.2f ms\n" +
            "  平均耗时: %.4f ms/次\n" +
            "  缓存命中率: %.2f%% (%d/%d)",
            samples, totalTimeMs, avgTimeMs, hitRate, hits, hits + misses
        );
    }
    
    /**
     * 重置统计数据
     */
    public static void reset() {
        totalSamples.set(0);
        totalTimeNanos.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}
