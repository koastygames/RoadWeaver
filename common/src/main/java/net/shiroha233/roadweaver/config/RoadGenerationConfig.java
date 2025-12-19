package net.shiroha233.roadweaver.config;

/**
 * 道路生成配置快照（不可变）。
 * 封装道路生成过程中需要的所有配置，避免在生成过程中频繁访问全局单例。
 * 
 * 设计原则：
 * 1. 不可变 - 一旦创建就不会改变，线程安全
 * 2. 包含寻路配置 - 组合而非继承
 * 3. 在入口层创建，传递到所有需要配置的地方
 */
public record RoadGenerationConfig(
    // 寻路配置（组合）
    PathfindingConfig pathfinding,
    // 是否启用分层寻路（粗步长引导 + 细步长精化）
    boolean hierarchicalPathfindingEnabled,
    // 道路宽度（0 表示随机）
    int roadWidth,
    // 是否允许人工道路
    boolean allowArtificial,
    // 是否允许自然道路
    boolean allowNatural,
    // 平均半径（用于高度平滑）
    int averagingRadius,
    // 是否启用限坡平滑
    boolean slopeLimitEnabled,
    // 每两段最大坡度
    int maxSlopeStepPerTwoSegments,
    // 是否启用路边结构
    boolean roadsideStructuresEnabled,
    // 每条路最大结构数
    int maxStructuresPerRoad,
    // 小型结构偏移
    int smallStructureOffset,
    // 中型结构偏移
    int mediumStructureOffset,
    // 大型结构偏移
    int largeStructureOffset,
    // 寻路算法
    ModConfig.PathfindingAlgorithm pathfindingAlgorithm
) {
    /**
     * 从 ModConfig 创建快照
     */
    public static RoadGenerationConfig from(ModConfig cfg) {
        return new RoadGenerationConfig(
            PathfindingConfig.from(cfg),
            cfg.hierarchicalPathfindingEnabled(),
            cfg.roadWidth(),
            cfg.allowArtificial(),
            cfg.allowNatural(),
            cfg.averagingRadius(),
            cfg.slopeLimitEnabled(),
            cfg.maxSlopeStepPerTwoSegments(),
            cfg.roadsideStructuresEnabled(),
            cfg.maxStructuresPerRoad(),
            cfg.smallStructureOffset(),
            cfg.mediumStructureOffset(),
            cfg.largeStructureOffset(),
            cfg.pathfindingAlgorithm()
        );
    }
    
    /**
     * 获取有效的道路宽度
     */
    public int effectiveRoadWidth(int defaultWidth) {
        return roadWidth > 0 ? roadWidth : defaultWidth;
    }
}
