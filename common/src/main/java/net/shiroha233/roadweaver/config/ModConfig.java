/* 文件职责：聚合并校验全局模组配置。 */
package net.shiroha233.roadweaver.config;

import net.shiroha233.roadweaver.config.sub.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组配置聚合类，通过组合持有所有子配置实例
 */
public final class ModConfig {

    private StructurePredictionConfig structurePrediction = new StructurePredictionConfig();
    private PlanningConfig planning = new PlanningConfig();
    private PathfindingCostConfig pathfindingCost = new PathfindingCostConfig();
    private RoadAppearanceConfig roadAppearance = new RoadAppearanceConfig();
    private BridgeConfig bridge = new BridgeConfig();
    private HighwayConfig highway = new HighwayConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    private RoadsideStructureConfig roadsideStructure = new RoadsideStructureConfig();
    private ClientConfig client = new ClientConfig();
    private Map<String, DimensionRoadSettings> dimensionRoadSettings = new ConcurrentHashMap<>();

    public void sanitize() {
        if (structurePrediction == null) structurePrediction = new StructurePredictionConfig();
        if (planning == null) planning = new PlanningConfig();
        if (pathfindingCost == null) pathfindingCost = new PathfindingCostConfig();
        if (roadAppearance == null) roadAppearance = new RoadAppearanceConfig();
        if (bridge == null) bridge = new BridgeConfig();
        if (highway == null) highway = new HighwayConfig();
        if (performance == null) performance = new PerformanceConfig();
        if (roadsideStructure == null) roadsideStructure = new RoadsideStructureConfig();
        if (client == null) client = new ClientConfig();

        structurePrediction.sanitize();
        planning.sanitize();
        pathfindingCost.sanitize();
        roadAppearance.sanitize();
        bridge.sanitize();
        highway.sanitize();
        performance.sanitize();
        roadsideStructure.sanitize();
        client.sanitize();

        if (dimensionRoadSettings == null) {
            dimensionRoadSettings = new ConcurrentHashMap<>();
        } else {
            try {
                dimensionRoadSettings.values().removeIf(v -> v == null || v.isAllInherit());
            } catch (Throwable ignored) {}
        }
    }


    public StructurePredictionConfig structurePrediction() { return structurePrediction; }
    public PlanningConfig planning() { return planning; }
    public PathfindingCostConfig pathfindingCost() { return pathfindingCost; }
    public RoadAppearanceConfig roadAppearance() { return roadAppearance; }
    public BridgeConfig bridge() { return bridge; }
    public HighwayConfig highway() { return highway; }
    public PerformanceConfig performance() { return performance; }
    public RoadsideStructureConfig roadsideStructure() { return roadsideStructure; }
    public ClientConfig client() { return client; }

    public boolean loadingTipsEnabled() {
        return client.loadingTipsEnabled();
    }

    public boolean loadingProgressEnabled() {
        return client.loadingProgressEnabled();
    }

    public Map<String, DimensionRoadSettings> dimensionRoadSettings() {
        return dimensionRoadSettings;
    }

    public void setDimensionRoadSettings(Map<String, DimensionRoadSettings> v) {
        this.dimensionRoadSettings = new ConcurrentHashMap<>(v == null ? Map.of() : v);
        try {
            this.dimensionRoadSettings.values().removeIf(s -> s == null || s.isAllInherit());
        } catch (Throwable ignored) {}
    }

    public DimensionRoadSettings getOrCreateDimensionSettings(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) return null;
        return dimensionRoadSettings.computeIfAbsent(dimensionId, k -> new DimensionRoadSettings());
    }

    public void removeDimensionSettingsIfAllInherit(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) return;
        DimensionRoadSettings s = dimensionRoadSettings.get(dimensionId);
        if (s != null && s.isAllInherit()) dimensionRoadSettings.remove(dimensionId);
    }

    private DimensionRoadSettings getDimSettings(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty() || dimensionRoadSettings == null) return null;
        return dimensionRoadSettings.get(dimensionId);
    }

    private static boolean chooseBool(Boolean override, boolean globalValue) {
        return override != null ? override : globalValue;
    }

    public boolean roadsEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.roadsEnabled(), roadAppearance.roadsEnabled());
    }

    public boolean bridgeEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.bridgeEnabled(), bridge.enabled());
    }

    public PathfindingCostConfig.PathfindingAlgorithm pathfindingAlgorithmForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        PathfindingCostConfig.PathfindingAlgorithm v = (s == null) ? null : s.pathfindingAlgorithm();
        return v != null ? v : pathfindingCost.pathfindingAlgorithm();
    }

    public boolean tunnelEnabled() {
        return roadAppearance.tunnelEnabled();
    }

    public int roadClearHeight() {
        return roadAppearance.roadClearHeight();
    }

    public int tunnelClearHeight() {
        return roadAppearance.tunnelClearHeight();
    }

    public boolean slopeLimitEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.slopeLimitEnabled(), roadAppearance.slopeLimitEnabled());
    }

    public boolean highwayEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.highwayEnabled(), highway.enabled());
    }

    public boolean roadsideStructuresEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.roadsideStructuresEnabled(), roadsideStructure.enabled());
    }

    public boolean roadSignsEnabledForDimension(String dimId) {
        DimensionRoadSettings s = getDimSettings(dimId);
        return chooseBool(s == null ? null : s.roadSignsEnabled(), roadAppearance.roadSignsEnabled());
    }

    public int highwayRoadWidth() {
        return highway.roadWidth();
    }

    public int averagingRadius() {
        return roadAppearance.averagingRadius();
    }

    public boolean highwaySlopeLimitEnabled() {
        return highway.slopeLimitEnabled();
    }

    public int highwaySlopeRunBlocks() {
        return highway.slopeRunBlocks();
    }

    public int highwaySlopeRiseBlocks() {
        return highway.slopeRiseBlocks();
    }

    public int highwayAStarStep() {
        return highway.aStarStep();
    }

    public double highwayFloatingWeight() {
        return highway.floatingWeight();
    }

    public double highwayPenetrationWeight() {
        return highway.penetrationWeight();
    }

    public boolean highwayEnabled() {
        return highway.enabled();
    }

    public boolean highwayAutoPlanEnabled() {
        return highway.autoPlanEnabled();
    }

    public int highwayGridBlocks() {
        return highway.gridBlocks();
    }

    public boolean highwayDynamicPlanEnabled() {
        return highway.dynamicPlanEnabled();
    }

    public List<String> structurePredictionDimensionWhitelist() {
        return structurePrediction.dimensionWhitelist();
    }

    public void setStructurePredictionDimensionWhitelist(List<String> v) {
        structurePrediction.setDimensionWhitelist(v);
    }

    public boolean structurePredictionEnabled() {
        return structurePrediction.enabled();
    }

    public boolean structureAvoidanceEnabled() {
        return structurePrediction.structureAvoidanceEnabled();
    }

    public boolean bridgeEnabled() {
        return bridge.enabled();
    }

    public int bridgeMinLength() {
        return bridge.minLength();
    }

    public int bridgeMergeGap() {
        return bridge.mergeGap();
    }

    public int bridgeMaxLengthBlocks() {
        return bridge.maxLengthBlocks();
    }

    public boolean bridgeUseBuoysInstead() {
        return bridge.useBuoysInstead();
    }

    public boolean bridgeUseBuoysWhenSkipped() {
        return bridge.useBuoysWhenSkipped();
    }

    public int buoyIntervalBlocks() {
        return bridge.buoyIntervalBlocks();
    }

    public int bridgeDeckClearance() {
        return bridge.deckClearance();
    }

    public boolean bridgeKeepLamps() {
        return bridge.keepLamps();
    }

    public int bridgePierInterval() {
        return bridge.pierInterval();
    }

    public int bridgePierWidth() {
        return bridge.pierWidth();
    }

    public int bridgePierMaxHeight() {
        return bridge.pierMaxHeight();
    }

    public int bridgeRampSegments() {
        return bridge.rampSegments();
    }

    public int bridgeMinWaterDepth() {
        return bridge.minWaterDepth();
    }

    public int maxSlopeStepPerTwoSegments() {
        return roadAppearance.maxSlopeStepPerTwoSegments();
    }

    public boolean slopeLimitEnabled() {
        return roadAppearance.slopeLimitEnabled();
    }

    public int lampInterval() {
        return roadAppearance.lampInterval();
    }

    public boolean isStructurePredictionEnabledForDimension(String dimensionId) {
        return structurePrediction.isEnabledForDimension(dimensionId);
    }

    public int predictRadiusChunks() {
        return structurePrediction.predictRadiusChunks();
    }

    public boolean biomePrefilter() {
        return structurePrediction.biomePrefilter();
    }

    public List<String> structureWhitelist() {
        return structurePrediction.structureWhitelist();
    }

    public void setStructureWhitelist(List<String> v) {
        structurePrediction.setStructureWhitelist(v);
    }

    public List<String> structureBlacklist() {
        return structurePrediction.structureBlacklist();
    }

    public void setStructureBlacklist(List<String> v) {
        structurePrediction.setStructureBlacklist(v);
    }

    public int villageRoadOffset() {
        return structurePrediction.villageRoadOffset();
    }

    public int otherStructureRoadOffset() {
        return structurePrediction.otherStructureRoadOffset();
    }

    public int aStarStep() {
        return pathfindingCost.aStarStep();
    }
}
