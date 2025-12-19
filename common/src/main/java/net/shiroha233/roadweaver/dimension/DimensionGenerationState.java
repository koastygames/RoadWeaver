package net.shiroha233.roadweaver.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 维度道路生成状态记录
 * 用于跟踪每个维度的初始道路生成进度
 */
public record DimensionGenerationState(
    ResourceKey<Level> dimension,
    boolean active,
    int total,
    int done,
    int generating,
    int failed,
    long startTime
) {
    /**
     * 创建初始状态
     */
    public static DimensionGenerationState initial(ResourceKey<Level> dimension) {
        return new DimensionGenerationState(dimension, false, 0, 0, 0, 0, 0);
    }
    
    /**
     * 创建活跃状态
     */
    public static DimensionGenerationState active(ResourceKey<Level> dimension, int total) {
        return new DimensionGenerationState(dimension, true, total, 0, 0, 0, System.currentTimeMillis());
    }
    
    /**
     * 更新进度
     */
    public DimensionGenerationState withProgress(int done, int generating, int failed) {
        return new DimensionGenerationState(dimension, active, total, done, generating, failed, startTime);
    }
    
    /**
     * 标记为完成
     */
    public DimensionGenerationState completed() {
        return new DimensionGenerationState(dimension, false, total, done, 0, failed, startTime);
    }
    
    /**
     * 获取进度百分比
     */
    public int progressPercent() {
        if (total <= 0) return 100;
        return Math.min(100, (done + failed) * 100 / total);
    }
    
    /**
     * 检查是否已完成
     */
    public boolean isComplete() {
        return !active && (total == 0 || (done + failed) >= total);
    }
}
