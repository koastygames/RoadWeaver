package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * 全局地形采样统计：线程安全地汇总所有 TerrainSamplingCache 实例的缓存命中率和噪声采样数。
 * 用于在初始道路生成界面显示实时性能数据。
 */
object TerrainSamplingStats {
    // 缓存请求总数（包括命中和未命中）
    private val totalRequests = AtomicLong(0)
    // 缓存命中次数
    private val cacheHits = AtomicLong(0)
    // 噪声采样次数（实际调用生成器的次数，即缓存未命中时）
    private val noiseSamples = AtomicLong(0)

    // 用于计算每秒采样数的时间戳和快照
    @Volatile
    private var lastSnapshotTimeMs: Long = 0
    @Volatile
    private var lastSnapshotNoiseSamples: Long = 0
    @Volatile
    private var samplesPerSecond: Double = 0.0

    /**
     * 记录一次缓存请求（命中）
     */
    @JvmStatic
    fun recordCacheHit() {
        totalRequests.incrementAndGet()
        cacheHits.incrementAndGet()
    }

    /**
     * 记录一次缓存请求（未命中，需要噪声采样）
     */
    @JvmStatic
    fun recordCacheMiss() {
        totalRequests.incrementAndGet()
        noiseSamples.incrementAndGet()
    }

    /**
     * 获取缓存命中率（0.0 ~ 1.0）
     */
    @JvmStatic
    fun getCacheHitRate(): Double {
        val total = totalRequests.get()
        if (total == 0L) return 0.0
        return cacheHits.get().toDouble() / total
    }

    /**
     * 获取缓存命中率百分比（0 ~ 100）
     */
    @JvmStatic
    fun getCacheHitRatePercent(): Int {
        return (getCacheHitRate() * 100.0).roundToInt()
    }

    /**
     * 获取总噪声采样数
     */
    @JvmStatic
    fun getTotalNoiseSamples(): Long {
        return noiseSamples.get()
    }

    /**
     * 更新并获取每秒噪声采样数（需要定期调用以刷新）
     */
    @JvmStatic
    fun updateAndGetSamplesPerSecond(): Double {
        val now = System.currentTimeMillis()
        val currentSamples = noiseSamples.get()

        if (lastSnapshotTimeMs == 0L) {
            // 首次调用，初始化快照
            lastSnapshotTimeMs = now
            lastSnapshotNoiseSamples = currentSamples
            return 0.0
        }

        val elapsedMs = now - lastSnapshotTimeMs
        if (elapsedMs >= 500) { // 每 500ms 更新一次
            val samplesDelta = currentSamples - lastSnapshotNoiseSamples
            samplesPerSecond = (samplesDelta * 1000.0) / elapsedMs
            lastSnapshotTimeMs = now
            lastSnapshotNoiseSamples = currentSamples
        }

        return samplesPerSecond
    }

    /**
     * 获取每秒噪声采样数（不更新，仅读取上次计算结果）
     */
    @JvmStatic
    fun getSamplesPerSecond(): Double {
        return samplesPerSecond
    }

    /**
     * 重置所有统计数据（在初始生成开始时调用）
     */
    @JvmStatic
    fun reset() {
        totalRequests.set(0)
        cacheHits.set(0)
        noiseSamples.set(0)
        lastSnapshotTimeMs = 0
        lastSnapshotNoiseSamples = 0
        samplesPerSecond = 0.0
    }
}
