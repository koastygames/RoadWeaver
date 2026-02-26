package net.shiroha233.roadweaver.config;

/**
 * 寻路配置快照（不可变）。从 ModConfig 创建，传递给寻路器，避免热路径中频繁访问全局单例。
 */
public record PathfindingConfig(
    int aStarStep,
    double orthoStepCost,
    double diagStepCost,
    double elevationWeight,
    double biomeWeight,
    double stabilityWeight,
    double waterDepthWeight,
    double nearWaterCost,
    double deviationWeight,
    double heuristicWeight,
    int threadDutyCycle,
    int bridgeMinWaterDepth
) {
    public static PathfindingConfig from(ModConfig cfg) {
        return new PathfindingConfig(
            cfg.aStarStep(),
            cfg.orthoStepCost(),
            cfg.diagStepCost(),
            cfg.elevationWeight(),
            cfg.biomeWeight(),
            cfg.stabilityWeight(),
            cfg.waterDepthWeight(),
            cfg.nearWaterCost(),
            cfg.deviationWeight(),
            cfg.heuristicWeight(),
            cfg.threadDutyCycle(),
            cfg.bridgeMinWaterDepth()
        );
    }
    
    /**
     * 获取有效的 A* 步长（带边界检查）
     */
    public int effectiveAStarStep() {
        if (aStarStep < 4) return 16;
        if (aStarStep > 128) return 128;
        return aStarStep;
    }
    
    /**
     * 是否需要节流（占空比 < 100）
     */
    public boolean shouldThrottle() {
        return threadDutyCycle < 100;
    }
}
