package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 高速公路配置
 */
public final class HighwayConfig implements SubConfig {
    private boolean enabled = false;
    private boolean autoPlanEnabled = true;
    private int gridBlocks = RoadConstants.DEFAULT_HIGHWAY_GRID_BLOCKS;
    private Boolean dynamicPlanEnabled = true;
    private int roadWidth = RoadConstants.DEFAULT_HIGHWAY_ROAD_WIDTH;
    private Boolean slopeLimitEnabled = true;
    private int slopeRunBlocks = RoadConstants.DEFAULT_HIGHWAY_SLOPE_RUN_BLOCKS;
    private int slopeRiseBlocks = RoadConstants.DEFAULT_HIGHWAY_SLOPE_RISE_BLOCKS;
    private int aStarStep = RoadConstants.DEFAULT_HIGHWAY_ASTAR_STEP;
    private int aStarMaxSteps = RoadConstants.DEFAULT_HIGHWAY_ASTAR_MAX_STEPS;
    private double floatingWeight = RoadConstants.DEFAULT_HIGHWAY_FLOATING_WEIGHT;
    private double penetrationWeight = RoadConstants.DEFAULT_HIGHWAY_PENETRATION_WEIGHT;
    private Boolean terrainAwarePlanning = true;
    private int intersectionWindowSize = RoadConstants.DEFAULT_INTERSECTION_WINDOW_SIZE;
    private double intersectionEdgeMargin = RoadConstants.DEFAULT_INTERSECTION_EDGE_MARGIN;

    @Override
    public void sanitize() {
        if (dynamicPlanEnabled == null) dynamicPlanEnabled = true;
        if (slopeLimitEnabled == null) slopeLimitEnabled = true;
        if (terrainAwarePlanning == null) terrainAwarePlanning = true;
        gridBlocks = Math.max(RoadConstants.HIGHWAY_GRID_BLOCKS_MIN, Math.min(RoadConstants.HIGHWAY_GRID_BLOCKS_MAX, gridBlocks));
        roadWidth = Math.max(RoadConstants.HIGHWAY_ROAD_WIDTH_MIN, Math.min(RoadConstants.HIGHWAY_ROAD_WIDTH_MAX, roadWidth));
        if (slopeRunBlocks < RoadConstants.HIGHWAY_SLOPE_RUN_MIN || slopeRunBlocks > RoadConstants.HIGHWAY_SLOPE_RUN_MAX) {
            slopeRunBlocks = RoadConstants.DEFAULT_HIGHWAY_SLOPE_RUN_BLOCKS;
        }
        slopeRiseBlocks = Math.max(0, Math.min(RoadConstants.HIGHWAY_SLOPE_RISE_MAX, slopeRiseBlocks));
        aStarStep = Math.max(RoadConstants.ASTAR_STEP_MIN, Math.min(RoadConstants.ASTAR_STEP_MAX, aStarStep));
        aStarMaxSteps = Math.max(RoadConstants.HIGHWAY_ASTAR_MAX_STEPS_MIN, Math.min(RoadConstants.HIGHWAY_ASTAR_MAX_STEPS_MAX, aStarMaxSteps));
        floatingWeight = Math.max(0, floatingWeight);
        penetrationWeight = Math.max(0, penetrationWeight);
        intersectionWindowSize = Math.max(RoadConstants.INTERSECTION_WINDOW_SIZE_MIN, Math.min(RoadConstants.INTERSECTION_WINDOW_SIZE_MAX, intersectionWindowSize));
        intersectionEdgeMargin = Math.max(0, Math.min(RoadConstants.INTERSECTION_EDGE_MARGIN_MAX, intersectionEdgeMargin));
    }

    @Override
    public HighwayConfig snapshot() {
        HighwayConfig copy = new HighwayConfig();
        copy.enabled = this.enabled;
        copy.autoPlanEnabled = this.autoPlanEnabled;
        copy.gridBlocks = this.gridBlocks;
        copy.dynamicPlanEnabled = this.dynamicPlanEnabled;
        copy.roadWidth = this.roadWidth;
        copy.slopeLimitEnabled = this.slopeLimitEnabled;
        copy.slopeRunBlocks = this.slopeRunBlocks;
        copy.slopeRiseBlocks = this.slopeRiseBlocks;
        copy.aStarStep = this.aStarStep;
        copy.aStarMaxSteps = this.aStarMaxSteps;
        copy.floatingWeight = this.floatingWeight;
        copy.penetrationWeight = this.penetrationWeight;
        copy.terrainAwarePlanning = this.terrainAwarePlanning;
        copy.intersectionWindowSize = this.intersectionWindowSize;
        copy.intersectionEdgeMargin = this.intersectionEdgeMargin;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public boolean autoPlanEnabled() { return autoPlanEnabled; }
    public void setAutoPlanEnabled(boolean v) { this.autoPlanEnabled = v; }
    public int gridBlocks() { return gridBlocks; }
    public void setGridBlocks(int v) { this.gridBlocks = v; }
    public boolean dynamicPlanEnabled() { return dynamicPlanEnabled == null || dynamicPlanEnabled; }
    public void setDynamicPlanEnabled(boolean v) { this.dynamicPlanEnabled = v; }
    public int planningRadiusBlocks() {
        int grid = Math.max(1, gridBlocks);
        return dynamicPlanEnabled() ? (grid * 2) : grid;
    }
    public int roadWidth() { return roadWidth; }
    public void setRoadWidth(int v) { this.roadWidth = v; }
    public boolean slopeLimitEnabled() { return slopeLimitEnabled == null || slopeLimitEnabled; }
    public void setSlopeLimitEnabled(boolean v) { this.slopeLimitEnabled = v; }
    public int slopeRunBlocks() { return slopeRunBlocks; }
    public void setSlopeRunBlocks(int v) { this.slopeRunBlocks = v; }
    public int slopeRiseBlocks() { return slopeRiseBlocks; }
    public void setSlopeRiseBlocks(int v) { this.slopeRiseBlocks = v; }
    public int aStarStep() { return aStarStep; }
    public void setAStarStep(int v) { this.aStarStep = v; }
    public int aStarMaxSteps() { return aStarMaxSteps; }
    public void setAStarMaxSteps(int v) { this.aStarMaxSteps = v; }
    public double floatingWeight() { return floatingWeight; }
    public void setFloatingWeight(double v) { this.floatingWeight = v; }
    public double penetrationWeight() { return penetrationWeight; }
    public void setPenetrationWeight(double v) { this.penetrationWeight = v; }
    public boolean terrainAwarePlanning() { return terrainAwarePlanning == null || terrainAwarePlanning; }
    public void setTerrainAwarePlanning(boolean v) { this.terrainAwarePlanning = v; }
    public int intersectionWindowSize() { return intersectionWindowSize; }
    public void setIntersectionWindowSize(int v) { this.intersectionWindowSize = v; }
    public double intersectionEdgeMargin() { return intersectionEdgeMargin; }
    public void setIntersectionEdgeMargin(double v) { this.intersectionEdgeMargin = v; }
}
