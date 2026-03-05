package net.shiroha233.roadweaver.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 维度道路生成状态记录
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
    public static DimensionGenerationState initial(ResourceKey<Level> dimension) {
        return new DimensionGenerationState(dimension, false, 0, 0, 0, 0, 0);
    }
    
    public static DimensionGenerationState active(ResourceKey<Level> dimension, int total) {
        return new DimensionGenerationState(dimension, true, total, 0, 0, 0, System.currentTimeMillis());
    }
    
    public DimensionGenerationState withProgress(int done, int generating, int failed) {
        return new DimensionGenerationState(dimension, active, total, done, generating, failed, startTime);
    }
    
    public DimensionGenerationState completed() {
        return new DimensionGenerationState(dimension, false, total, done, 0, failed, startTime);
    }
    
    public int progressPercent() {
        if (total <= 0) return 100;
        return Math.min(100, (done + failed) * 100 / total);
    }
    
    public boolean isComplete() {
        return !active && (total == 0 || (done + failed) >= total);
    }
}
