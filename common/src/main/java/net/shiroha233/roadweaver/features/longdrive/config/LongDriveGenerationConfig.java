package net.shiroha233.roadweaver.features.longdrive.config;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.BridgeConfig;
import net.shiroha233.roadweaver.config.sub.LongDriveConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.PerformanceConfig;
import net.shiroha233.roadweaver.config.sub.RoadAppearanceConfig;

/**
 * 长途驾驶生成配置快照（不可变）
 */
public record LongDriveGenerationConfig(
        PathfindingCostConfig pathfindingCost,
        int roadWidth,
        int averagingRadius,
        boolean slopeLimitEnabled,
        int maxSlopeStepPerTwoSegments,
        int aStarStep,
        int segmentLength,
        int leadDistance,
        double directionBias,
        int threadDutyCycle,
        int bridgeMinWaterDepth
) {
    public static LongDriveGenerationConfig from(ModConfig cfg) {
        LongDriveConfig ld = cfg.longDrive();
        RoadAppearanceConfig appearance = cfg.roadAppearance();
        PerformanceConfig perf = cfg.performance();
        BridgeConfig bridge = cfg.bridge();
        PathfindingCostConfig cost = cfg.pathfindingCost().snapshot();

        return new LongDriveGenerationConfig(
                cost,
                ld.roadWidth(),
                appearance.averagingRadius(),
                appearance.slopeLimitEnabled(),
                appearance.maxSlopeStepPerTwoSegments(),
                ld.aStarStep(),
                ld.segmentLength(),
                ld.leadDistance(),
                ld.directionBias(),
                perf.threadDutyCycle(),
                bridge.minWaterDepth()
        );
    }
}
