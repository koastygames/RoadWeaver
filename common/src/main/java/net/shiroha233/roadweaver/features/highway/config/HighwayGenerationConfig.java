package net.shiroha233.roadweaver.features.highway.config;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;

/**
 * Immutable highway-generation snapshot used by worker threads.
 */
public record HighwayGenerationConfig(
        PathfindingCostConfig pathfindingCost,
        int roadWidth,
        int averagingRadius,
        boolean slopeLimitEnabled,
        int slopeRunBlocks,
        int slopeRiseBlocks,
        PathfindingCostConfig.PathfindingAlgorithm pathfindingAlgorithm,
        double floatingWeight,
        double penetrationWeight,
        double routeCurvature,
        double turnPenalty,
        double gradeChangePenalty,
        int threadDutyCycle,
        int bridgeMinWaterDepth
) {
    public static HighwayGenerationConfig from(ModConfig cfg) {
        PathfindingCostConfig cost = cfg.pathfindingCost().snapshot();

        return new HighwayGenerationConfig(
                cost,
                cfg.highwayRoadWidth(),
                cfg.averagingRadius(),
                cfg.highwaySlopeLimitEnabled(),
                cfg.highwaySlopeRunBlocks(),
                cfg.highwaySlopeRiseBlocks(),
                PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL,
                cfg.highwayFloatingWeight(),
                cfg.highwayPenetrationWeight(),
                cfg.highway().routeCurvature(),
                cfg.highway().turnPenalty(),
                cfg.highway().gradeChangePenalty(),
                cfg.performance().threadDutyCycle(),
                cfg.bridge().minWaterDepth()
        );
    }

    /** Converts this highway snapshot into the shared road-generation snapshot. */
    public RoadGenerationConfig toRoadGenerationConfig() {
        return RoadGenerationConfig.fromHighwayConfig(this);
    }
}
