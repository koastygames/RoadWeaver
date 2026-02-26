package net.shiroha233.roadweaver.features.longdrive.config;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PathfindingConfig;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;

/**
 * 长途旅行生成配置快照（不可变）。
 */
public record LongDriveGenerationConfig(
        PathfindingConfig pathfinding,
        int roadWidth,
        int averagingRadius,
        boolean slopeLimitEnabled,
        int maxSlopeStepPerTwoSegments,
        int aStarStep,
        int segmentLength,
        int leadDistance,
        double directionBias
) {
    public static LongDriveGenerationConfig from(ModConfig cfg) {
        PathfindingConfig base = PathfindingConfig.from(cfg);
        PathfindingConfig ldPath = new PathfindingConfig(
                cfg.longDriveAStarStep(),
                base.orthoStepCost(),
                base.diagStepCost(),
                base.elevationWeight(),
                base.biomeWeight(),
                base.stabilityWeight(),
                base.waterDepthWeight(),
                base.nearWaterCost(),
                base.deviationWeight(),
                base.heuristicWeight(),
                base.threadDutyCycle(),
                base.bridgeMinWaterDepth()
        );
        return new LongDriveGenerationConfig(
                ldPath,
                cfg.longDriveRoadWidth(),
                cfg.averagingRadius(),
                cfg.slopeLimitEnabled(),
                cfg.maxSlopeStepPerTwoSegments(),
                cfg.longDriveAStarStep(),
                cfg.longDriveSegmentLength(),
                cfg.longDriveLeadDistance(),
                cfg.longDriveDirectionBias()
        );
    }

    public RoadGenerationConfig toRoadGenerationConfig() {
        return new RoadGenerationConfig(
                pathfinding,
                false,
                roadWidth,
                true,
                false,
                averagingRadius,
                slopeLimitEnabled,
                maxSlopeStepPerTwoSegments,
                false,
                0, 0, 0, 0,
                ModConfig.PathfindingAlgorithm.ASTAR_BASIC
        );
    }
}
