package net.shiroha233.roadweaver.config;

/**
 * 寻路配置快照（不可变）。
 * 在入口层从 ModConfig 创建，传递给寻路器，避免热路径中频繁访问全局单例。
 * 
 * 设计原则：
 * 1. 不可变 - 一旦创建就不会改变，线程安全
 * 2. 无依赖 - 不依赖任何全局状态
 * 3. 可测试 - 方便单元测试时注入不同配置
 */
public record PathfindingConfig(
    // A* 步长
    int aStarStep,
    // 正交步成本
    double orthoStepCost,
    // 对角步成本
    double diagStepCost,
    // 高度差权重
    double elevationWeight,
    // 生物群系权重
    double biomeWeight,
    // 地形稳定性权重
    double stabilityWeight,
    // 水深权重
    double waterDepthWeight,
    // 近水惩罚
    double nearWaterCost,
    // 偏离权重
    double deviationWeight,
    // 启发式权重
    double heuristicWeight,
    // 线程占空比（用于节流）
    int threadDutyCycle,
    // 桥梁最小水深
    int bridgeMinWaterDepth
) {
    /**
     * 从 ModConfig 创建快照
     */
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
