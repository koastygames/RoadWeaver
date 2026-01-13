package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速高度采样器 - 绕过 getBaseHeight() 的 NoiseChunk 创建开销
 * 
 * 原理：原版 getBaseHeight() 每次调用都会创建新的 NoiseChunk，
 * 而 NoiseChunk 初始化涉及大量噪声预计算和内存分配。
 * 
 * 本类直接使用 NoiseRouter.initialDensityWithoutJaggedness() 进行采样，
 * 这是原版 preliminarySurfaceLevel 使用的同一个密度函数，
 * 精度足够用于道路寻路，但速度快 10-50 倍。
 * 
 * 坐标对齐：重要！
 * - 原版 preliminarySurfaceLevel 使用 QuartPos.toBlock(QuartPos.fromBlock()) 进行4格对齐
 * - 本类必须使用相同的坐标对齐方式，否则会产生1-3格的精度偏差
 * - 对齐方式：(x >> 2) << 2，将坐标对齐到4格边界
 * 
 * 限制：
 * - 不考虑水体、洞穴等细节（对道路寻路影响不大）
 * - 精度为 cellHeight（通常8格），而非逐格
 * - 坐标按4格对齐，与原版行为一致
 */
public final class FastHeightSampler {
    
    // 密度阈值：与原版 computePreliminarySurfaceLevel 一致
    private static final double DENSITY_THRESHOLD = 0.390625D;
    
    private final DensityFunction initialDensity;
    private final int minY;
    private final int maxY;
    private final int cellHeight;
    
    // 高度缓存：线程安全
    private final ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();
    
    private FastHeightSampler(DensityFunction initialDensity, NoiseSettings settings) {
        this.initialDensity = initialDensity;
        this.minY = settings.minY();
        this.maxY = minY + settings.height();
        this.cellHeight = settings.getCellHeight();
    }
    
    /**
     * 从 ServerLevel 创建采样器
     */
    public static FastHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.getGeneratorState().randomState();
        NoiseRouter router = randomState.router();
        
        // 获取 NoiseSettings
        NoiseSettings settings = getNoiseSettings(level);
        
        return new FastHeightSampler(router.initialDensityWithoutJaggedness(), settings);
    }
    
    /**
     * 快速采样高度
     * 
     * @return 估算的地表高度，精度为 cellHeight
     */
    public int sampleHeight(int x, int z) {
        // 对齐坐标到4格边界，匹配原版 preliminarySurfaceLevel 的行为
        int alignedX = (x >> 2) << 2;
        int alignedZ = (z >> 2) << 2;
        
        long key = packXZ(alignedX, alignedZ);
        Integer cached = heightCache.get(key);
        if (cached != null) {
            PerformanceMonitor.recordCacheHit();
            return cached;
        }
        
        PerformanceMonitor.recordCacheMiss();
        long startTime = System.nanoTime();
        
        int height = computeHeight(alignedX, alignedZ);
        heightCache.put(key, height);
        
        long duration = System.nanoTime() - startTime;
        PerformanceMonitor.recordSample(duration);
        
        return height;
    }
    
    /**
     * 批量预热指定区域
     */
    public void prewarmRegion(int minX, int minZ, int maxX, int maxZ, int step) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                // 使用对齐坐标进行预热
                int alignedX = (x >> 2) << 2;
                int alignedZ = (z >> 2) << 2;
                sampleHeight(alignedX, alignedZ);
            }
        }
    }
    
    /**
     * 计算高度 - 模仿原版 computePreliminarySurfaceLevel
     */
    private int computeHeight(int x, int z) {
        // 从上往下找第一个密度 > 阈值的位置
        for (int y = maxY; y >= minY; y -= cellHeight) {
            double density = initialDensity.compute(
                new DensityFunction.SinglePointContext(x, y, z)
            );
            if (density > DENSITY_THRESHOLD) {
                return y;
            }
        }
        return minY;
    }
    
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * 获取 NoiseSettings
     * 注意：需要根据实际的 ChunkGenerator 类型处理
     */
    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        
        // NoiseBasedChunkGenerator 的情况
        if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        
        // 默认主世界设置
        return NoiseSettings.create(-64, 384, 1, 2);
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        heightCache.clear();
    }
    
    /**
     * 获取缓存大小（用于监控）
     */
    public int getCacheSize() {
        return heightCache.size();
    }
}
