package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 地形缓存预热器 - 优化版
 * 
 * 策略：预热搜索区域的矩形范围，而不是沿路径线性预热
 * 
 * 原理：
 * - A* 寻路的搜索空间通常是起点到终点的矩形区域
 * - 预热整个矩形区域可以大幅提高缓存命中率
 * - 使用 FastHeightSampler 的批量预热功能，进一步提速
 */
public final class TerrainCachePrewarmer {
    private TerrainCachePrewarmer() {}

    /**
     * 预热起点到终点之间的矩形区域
     * 
     * @param startGround 起点
     * @param endGround   终点
     * @param level       服务端世界
     * @param maxSteps    最大步数（用于估算搜索范围）
     * @param cache       地形采样缓存
     */
    public static void prewarmAlongRoute(BlockPos startGround,
                                        BlockPos endGround,
                                        ServerLevel level,
                                        int maxSteps,
                                        TerrainSamplingCache cache) {
        if (cache == null) return;
        if (startGround == null || endGround == null) return;
        if (maxSteps <= 0) return;

        // 计算搜索区域的边界
        int minX = Math.min(startGround.getX(), endGround.getX());
        int maxX = Math.max(startGround.getX(), endGround.getX());
        int minZ = Math.min(startGround.getZ(), endGround.getZ());
        int maxZ = Math.max(startGround.getZ(), endGround.getZ());
        
        // 扩展边界：A* 可能会偏离直线路径
        // 扩展量基于距离的 20%（经验值）
        int distance = Math.max(maxX - minX, maxZ - minZ);
        int expansion = Math.max(64, distance / 5);
        
        minX -= expansion;
        maxX += expansion;
        minZ -= expansion;
        maxZ += expansion;
        
        // 使用粗网格预热（64格步长）
        // 原因：FastHeightSampler 内部已经有缓存，粗网格足够
        int step = 64;
        
        // 批量预热
        cache.prewarmRegion(level, minX, minZ, maxX, maxZ, step);
    }
}
