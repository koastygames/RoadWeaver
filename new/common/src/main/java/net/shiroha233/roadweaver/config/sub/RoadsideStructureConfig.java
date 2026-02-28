package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 路边结构配置
 */
public final class RoadsideStructureConfig implements SubConfig {
    private boolean enabled = true;
    private int maxStructuresPerRoad = RoadConstants.DEFAULT_MAX_STRUCTURES_PER_ROAD;
    private int smallStructureOffset = RoadConstants.DEFAULT_SMALL_STRUCTURE_OFFSET;
    private int mediumStructureOffset = RoadConstants.DEFAULT_MEDIUM_STRUCTURE_OFFSET;
    private int largeStructureOffset = RoadConstants.DEFAULT_LARGE_STRUCTURE_OFFSET;
    private int villageRoadOffset = RoadConstants.DEFAULT_VILLAGE_ROAD_OFFSET;
    private int otherStructureRoadOffset = RoadConstants.DEFAULT_OTHER_STRUCTURE_ROAD_OFFSET;
    private boolean structureAvoidanceEnabled = true;
    private int structureRoadOffset = RoadConstants.DEFAULT_VILLAGE_ROAD_OFFSET;

    @Override
    public void sanitize() {
        maxStructuresPerRoad = Math.max(0, Math.min(RoadConstants.MAX_STRUCTURES_PER_ROAD_MAX, maxStructuresPerRoad));
        smallStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, smallStructureOffset));
        mediumStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, mediumStructureOffset));
        largeStructureOffset = Math.max(RoadConstants.STRUCTURE_OFFSET_MIN, Math.min(RoadConstants.STRUCTURE_OFFSET_MAX, largeStructureOffset));
        villageRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, villageRoadOffset));
        otherStructureRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, otherStructureRoadOffset));
        structureRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, structureRoadOffset));
    }

    @Override
    public RoadsideStructureConfig snapshot() {
        RoadsideStructureConfig copy = new RoadsideStructureConfig();
        copy.enabled = this.enabled;
        copy.maxStructuresPerRoad = this.maxStructuresPerRoad;
        copy.smallStructureOffset = this.smallStructureOffset;
        copy.mediumStructureOffset = this.mediumStructureOffset;
        copy.largeStructureOffset = this.largeStructureOffset;
        copy.villageRoadOffset = this.villageRoadOffset;
        copy.otherStructureRoadOffset = this.otherStructureRoadOffset;
        copy.structureAvoidanceEnabled = this.structureAvoidanceEnabled;
        copy.structureRoadOffset = this.structureRoadOffset;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int maxStructuresPerRoad() { return maxStructuresPerRoad; }
    public void setMaxStructuresPerRoad(int v) { this.maxStructuresPerRoad = Math.max(0, v); }
    public int smallStructureOffset() { return smallStructureOffset; }
    public void setSmallStructureOffset(int v) { this.smallStructureOffset = Math.max(1, v); }
    public int mediumStructureOffset() { return mediumStructureOffset; }
    public void setMediumStructureOffset(int v) { this.mediumStructureOffset = Math.max(1, v); }
    public int largeStructureOffset() { return largeStructureOffset; }
    public void setLargeStructureOffset(int v) { this.largeStructureOffset = Math.max(1, v); }
    public int villageRoadOffset() { return villageRoadOffset; }
    public void setVillageRoadOffset(int v) { this.villageRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, v)); }
    public int otherStructureRoadOffset() { return otherStructureRoadOffset; }
    public void setOtherStructureRoadOffset(int v) { this.otherStructureRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, v)); }
    public boolean structureAvoidanceEnabled() { return structureAvoidanceEnabled; }
    public void setStructureAvoidanceEnabled(boolean v) { this.structureAvoidanceEnabled = v; }
    public int structureRoadOffset() { return villageRoadOffset; }
    public void setStructureRoadOffset(int v) { this.villageRoadOffset = Math.max(0, Math.min(RoadConstants.ROAD_OFFSET_MAX, v)); }
}
