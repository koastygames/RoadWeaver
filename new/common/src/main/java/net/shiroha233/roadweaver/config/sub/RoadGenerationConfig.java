package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;

/**
 * 道路生成配置，聚合道路生成所需的各项配置参数
 */
public final class RoadGenerationConfig implements SubConfig {

    private int effectiveRoadWidth = RoadConstants.DEFAULT_ROAD_WIDTH;
    private boolean allowArtificial = true;
    private boolean allowNatural = true;
    private int averagingRadius = RoadConstants.DEFAULT_AVERAGING_RADIUS;
    private boolean slopeLimitEnabled = true;
    private int maxSlopeStepPerTwoSegments = RoadConstants.DEFAULT_MAX_SLOPE_STEP;
    private PathfindingCostConfig pathfinding = new PathfindingCostConfig();

    /**
     * 从 HighwayGenerationConfig 创建 RoadGenerationConfig
     */
    public static RoadGenerationConfig fromHighwayConfig(HighwayGenerationConfig highwayConfig) {
        RoadGenerationConfig cfg = new RoadGenerationConfig();
        if (highwayConfig == null) {
            cfg.sanitize();
            return cfg;
        }

        cfg.effectiveRoadWidth = Math.max(1, highwayConfig.roadWidth());
        cfg.averagingRadius = Math.max(0, highwayConfig.averagingRadius());
        cfg.slopeLimitEnabled = highwayConfig.slopeLimitEnabled();
        cfg.maxSlopeStepPerTwoSegments = Math.max(0, highwayConfig.slopeRiseBlocks());

        if (highwayConfig.pathfindingCost() != null) {
            cfg.pathfinding = highwayConfig.pathfindingCost().snapshot();
        }

        cfg.sanitize();
        return cfg;
    }

    public static RoadGenerationConfig from(ModConfig config) {
        RoadGenerationConfig cfg = new RoadGenerationConfig();
        if (config == null) {
            cfg.sanitize();
            return cfg;
        }

        cfg.effectiveRoadWidth = config.roadsEnabledForDimension(null) ? config.averagingRadius() : 0;
        cfg.allowArtificial = config.roadAppearance().allowArtificial();
        cfg.allowNatural = config.roadAppearance().allowNatural();
        cfg.averagingRadius = config.averagingRadius();
        cfg.slopeLimitEnabled = config.slopeLimitEnabledForDimension(null);
        cfg.maxSlopeStepPerTwoSegments = config.roadAppearance().maxSlopeStepPerTwoSegments();

        PathfindingCostConfig pf = config.pathfindingCost();
        if (pf != null) {
            cfg.pathfinding = pf.snapshot();
        }

        cfg.sanitize();
        return cfg;
    }

    @Override
    public void sanitize() {
        effectiveRoadWidth = Math.max(1, Math.min(RoadConstants.ROAD_WIDTH_MAX, effectiveRoadWidth));
        averagingRadius = Math.max(0, averagingRadius);
        maxSlopeStepPerTwoSegments = Math.max(0, Math.min(RoadConstants.MAX_SLOPE_STEP_MAX, maxSlopeStepPerTwoSegments));
        if (pathfinding == null) {
            pathfinding = new PathfindingCostConfig();
        }
        pathfinding.sanitize();
    }

    @Override
    public RoadGenerationConfig snapshot() {
        RoadGenerationConfig copy = new RoadGenerationConfig();
        copy.effectiveRoadWidth = this.effectiveRoadWidth;
        copy.allowArtificial = this.allowArtificial;
        copy.allowNatural = this.allowNatural;
        copy.averagingRadius = this.averagingRadius;
        copy.slopeLimitEnabled = this.slopeLimitEnabled;
        copy.maxSlopeStepPerTwoSegments = this.maxSlopeStepPerTwoSegments;
        copy.pathfinding = (PathfindingCostConfig) this.pathfinding.snapshot();
        return copy;
    }

    public int effectiveRoadWidth(int baseWidth) {
        return Math.max(1, Math.min(RoadConstants.ROAD_WIDTH_MAX, baseWidth > 0 ? baseWidth : effectiveRoadWidth));
    }

    public boolean allowArtificial() {
        return allowArtificial;
    }

    public boolean allowNatural() {
        return allowNatural;
    }

    public int averagingRadius() {
        return averagingRadius;
    }

    public boolean slopeLimitEnabled() {
        return slopeLimitEnabled;
    }

    public int maxSlopeStepPerTwoSegments() {
        return maxSlopeStepPerTwoSegments;
    }

    public PathfindingCostConfig pathfinding() {
        return pathfinding;
    }
}
