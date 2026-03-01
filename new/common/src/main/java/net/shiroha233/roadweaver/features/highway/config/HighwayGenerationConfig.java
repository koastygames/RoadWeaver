package net.shiroha233.roadweaver.features.highway.config;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;

/**
 * Highway 生成配置快照
 */
public record HighwayGenerationConfig(
        PathfindingCostConfig pathfindingCost,
        boolean hierarchicalPathfindingEnabled,
        int roadWidth,
        int averagingRadius,
        boolean slopeLimitEnabled,
        int slopeRunBlocks,
        int slopeRiseBlocks,
        PathfindingCostConfig.PathfindingAlgorithm pathfindingAlgorithm,
        double floatingWeight,
        double penetrationWeight,
        int threadDutyCycle,
        int bridgeMinWaterDepth
) {
    public static HighwayGenerationConfig from(ModConfig cfg) {
        PathfindingCostConfig cost = cfg.pathfindingCost().snapshot();

        return new HighwayGenerationConfig(
                cost,
                cfg.hierarchicalPathfindingEnabled(),
                cfg.highwayRoadWidth(),
                cfg.averagingRadius(),
                cfg.highwaySlopeLimitEnabled(),
                cfg.highwaySlopeRunBlocks(),
                cfg.highwaySlopeRiseBlocks(),
                PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL,
                cfg.highwayFloatingWeight(),
                cfg.highwayPenetrationWeight(),
                cfg.performance().threadDutyCycle(),
                cfg.bridge().minWaterDepth()
        );
    }

    /**
     * 转换为 RoadGenerationConfig
     */
    public RoadGenerationConfig toRoadGenerationConfig() {
        return RoadGenerationConfig.fromHighwayConfig(this);
    }
}
