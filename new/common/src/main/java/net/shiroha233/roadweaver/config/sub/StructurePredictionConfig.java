package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构预测配置
 */
public final class StructurePredictionConfig implements SubConfig {
    private boolean enabled = true;
    private boolean structureAvoidanceEnabled = true;
    private int predictRadiusChunks = RoadConstants.DEFAULT_PREDICT_RADIUS_CHUNKS;
    private boolean biomePrefilter = true;
    private List<String> structureWhitelist = new ArrayList<>(List.of("#minecraft:village"));
    private List<String> structureBlacklist = new ArrayList<>();
    private List<String> dimensionWhitelist = new ArrayList<>(List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
    private int villageRoadOffset = 8;
    private int otherStructureRoadOffset = 6;

    @Override
    public void sanitize() {
        if (structureWhitelist == null) structureWhitelist = new ArrayList<>();
        if (structureBlacklist == null) structureBlacklist = new ArrayList<>();
        if (dimensionWhitelist == null) {
            dimensionWhitelist = new ArrayList<>(List.of(
                    "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        }
        if (predictRadiusChunks <= 0) predictRadiusChunks = RoadConstants.DEFAULT_PREDICT_RADIUS_CHUNKS;
        if (villageRoadOffset < 0) villageRoadOffset = 8;
        if (otherStructureRoadOffset < 0) otherStructureRoadOffset = 6;
    }

    @Override
    public StructurePredictionConfig snapshot() {
        StructurePredictionConfig copy = new StructurePredictionConfig();
        copy.enabled = this.enabled;
        copy.structureAvoidanceEnabled = this.structureAvoidanceEnabled;
        copy.predictRadiusChunks = this.predictRadiusChunks;
        copy.biomePrefilter = this.biomePrefilter;
        copy.structureWhitelist = new ArrayList<>(this.structureWhitelist);
        copy.structureBlacklist = new ArrayList<>(this.structureBlacklist);
        copy.dimensionWhitelist = new ArrayList<>(this.dimensionWhitelist);
        copy.villageRoadOffset = this.villageRoadOffset;
        copy.otherStructureRoadOffset = this.otherStructureRoadOffset;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public boolean structureAvoidanceEnabled() { return structureAvoidanceEnabled; }
    public void setStructureAvoidanceEnabled(boolean v) { this.structureAvoidanceEnabled = v; }
    public int predictRadiusChunks() { return predictRadiusChunks; }
    public void setPredictRadiusChunks(int v) { this.predictRadiusChunks = v; }
    public int radiusChunks() { return predictRadiusChunks; }
    public void setRadiusChunks(int v) { this.predictRadiusChunks = v; }
    public boolean biomePrefilter() { return biomePrefilter; }
    public void setBiomePrefilter(boolean v) { this.biomePrefilter = v; }
    public List<String> structureWhitelist() { return structureWhitelist; }
    public void setStructureWhitelist(List<String> v) { this.structureWhitelist = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> structureBlacklist() { return structureBlacklist; }
    public void setStructureBlacklist(List<String> v) { this.structureBlacklist = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> dimensionWhitelist() { return dimensionWhitelist; }
    public void setDimensionWhitelist(List<String> v) { this.dimensionWhitelist = v == null ? new ArrayList<>() : new ArrayList<>(v); }

    public boolean isEnabledForDimension(String dimensionId) {
        if (!enabled || dimensionId == null || dimensionId.isEmpty()) return false;
        return dimensionWhitelist != null && dimensionWhitelist.contains(dimensionId);
    }

    public int villageRoadOffset() { return villageRoadOffset; }
    public void setVillageRoadOffset(int v) { this.villageRoadOffset = v; }
    public int otherStructureRoadOffset() { return otherStructureRoadOffset; }
    public void setOtherStructureRoadOffset(int v) { this.otherStructureRoadOffset = v; }
}
