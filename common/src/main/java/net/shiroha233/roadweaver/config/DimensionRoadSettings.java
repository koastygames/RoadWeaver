package net.shiroha233.roadweaver.config;

import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;

/**
 * 按维度覆盖的道路功能开关配置，null 字段表示继承全局配置
 */
public final class DimensionRoadSettings {

    private Boolean roadsEnabled;
    private Boolean bridgeEnabled;
    private PathfindingCostConfig.PathfindingAlgorithm pathfindingAlgorithm;
    private Boolean slopeLimitEnabled;
    private Boolean roadsideStructuresEnabled;
    private Boolean roadSignsEnabled;

    public DimensionRoadSettings() {}

    public Boolean roadsEnabled() { return roadsEnabled; }
    public void setRoadsEnabled(Boolean v) { this.roadsEnabled = v; }

    public Boolean bridgeEnabled() { return bridgeEnabled; }
    public void setBridgeEnabled(Boolean v) { this.bridgeEnabled = v; }

    public PathfindingCostConfig.PathfindingAlgorithm pathfindingAlgorithm() { return pathfindingAlgorithm; }
    public void setPathfindingAlgorithm(PathfindingCostConfig.PathfindingAlgorithm v) { this.pathfindingAlgorithm = v; }

    public Boolean slopeLimitEnabled() { return slopeLimitEnabled; }
    public void setSlopeLimitEnabled(Boolean v) { this.slopeLimitEnabled = v; }

    public Boolean roadsideStructuresEnabled() { return roadsideStructuresEnabled; }
    public void setRoadsideStructuresEnabled(Boolean v) { this.roadsideStructuresEnabled = v; }

    public Boolean roadSignsEnabled() { return roadSignsEnabled; }
    public void setRoadSignsEnabled(Boolean v) { this.roadSignsEnabled = v; }

    public DimensionRoadSettings copy() {
        DimensionRoadSettings out = new DimensionRoadSettings();
        out.roadsEnabled = this.roadsEnabled;
        out.bridgeEnabled = this.bridgeEnabled;
        out.pathfindingAlgorithm = this.pathfindingAlgorithm;
        out.slopeLimitEnabled = this.slopeLimitEnabled;
        out.roadsideStructuresEnabled = this.roadsideStructuresEnabled;
        out.roadSignsEnabled = this.roadSignsEnabled;
        return out;
    }

    public boolean isAllInherit() {
        return roadsEnabled == null
                && bridgeEnabled == null
                && pathfindingAlgorithm == null
                && slopeLimitEnabled == null
                && roadsideStructuresEnabled == null
                && roadSignsEnabled == null
;
    }
}
