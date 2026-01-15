package net.shiroha233.roadweaver.features.highway.config;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PathfindingConfig;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;

/**
 * Highway 生成配置快照（不可变）。
 *
 * 说明：目前为了尽快打通闭环，Highway 暂时复用 path 的寻路实现（通过 RoadGenerationConfig 适配）。
 * 后续替换为 Highway 专用双向 A* 时，可保持该快照不变，仅替换内部实现。
 */
public record HighwayGenerationConfig(
        PathfindingConfig pathfinding,
        boolean hierarchicalPathfindingEnabled,
        int roadWidth,
        int averagingRadius,
        boolean slopeLimitEnabled,
        int slopeRunBlocks,
        int slopeRiseBlocks,
        ModConfig.PathfindingAlgorithm pathfindingAlgorithm,
        double floatingWeight,
        double penetrationWeight
) {
    public static HighwayGenerationConfig from(ModConfig cfg) {
        PathfindingConfig base = PathfindingConfig.from(cfg);
        PathfindingConfig highwayPath = new PathfindingConfig(
                cfg.highwayAStarStep(),
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

        return new HighwayGenerationConfig(
                highwayPath,
                cfg.hierarchicalPathfindingEnabled(),
                cfg.highwayRoadWidth(),
                cfg.averagingRadius(),
                cfg.highwaySlopeLimitEnabled(),
                cfg.highwaySlopeRunBlocks(),
                cfg.highwaySlopeRiseBlocks(),
                ModConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL,
                cfg.highwayFloatingWeight(),
                cfg.highwayPenetrationWeight()
        );
    }

    /**
     * 适配现有 path 寻路/后处理所需的配置类型。
     */
    public RoadGenerationConfig toRoadGenerationConfig() {
        // 兼容层：RoadGenerationConfig 仍然使用“每两段最大坡度”的表示。
        // Highway 的真实高度平滑由 slopeRunBlocks/slopeRiseBlocks 决定。
        int step2 = 0;
        if (slopeRunBlocks > 0 && slopeRiseBlocks > 0) {
            step2 = (int) Math.floor((2.0 * slopeRiseBlocks) / (double) slopeRunBlocks);
        }
        step2 = Math.max(0, Math.min(8, step2));
        return new RoadGenerationConfig(
                pathfinding,
                hierarchicalPathfindingEnabled,
                roadWidth,
                true,
                false,
                averagingRadius,
                slopeLimitEnabled,
                step2,
                false,
                0,
                0,
                0,
                0,
                pathfindingAlgorithm
        );
    }
}
