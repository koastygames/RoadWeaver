package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 长途驾驶配置
 */
public final class LongDriveConfig implements SubConfig {
    private boolean enabled = false;
    private int roadWidth = RoadConstants.DEFAULT_LONG_DRIVE_ROAD_WIDTH;
    private int aStarStep = RoadConstants.DEFAULT_LONG_DRIVE_ASTAR_STEP;
    private int segmentLength = RoadConstants.DEFAULT_LONG_DRIVE_SEGMENT_LENGTH;
    private int leadDistance = RoadConstants.DEFAULT_LONG_DRIVE_LEAD_DISTANCE;
    private double directionBias = RoadConstants.DEFAULT_LONG_DRIVE_DIRECTION_BIAS;

    @Override
    public void sanitize() {
        roadWidth = Math.max(1, Math.min(RoadConstants.ROAD_WIDTH_MAX, roadWidth));
        aStarStep = Math.max(RoadConstants.ASTAR_STEP_MIN, Math.min(RoadConstants.ASTAR_STEP_MAX, aStarStep));
        segmentLength = Math.max(RoadConstants.LONG_DRIVE_SEGMENT_LENGTH_MIN, Math.min(RoadConstants.LONG_DRIVE_SEGMENT_LENGTH_MAX, segmentLength));
        leadDistance = Math.max(RoadConstants.LONG_DRIVE_LEAD_DISTANCE_MIN, Math.min(RoadConstants.LONG_DRIVE_LEAD_DISTANCE_MAX, leadDistance));
        directionBias = Math.max(0, Math.min(RoadConstants.LONG_DRIVE_DIRECTION_BIAS_MAX, directionBias));
    }

    @Override
    public LongDriveConfig snapshot() {
        LongDriveConfig copy = new LongDriveConfig();
        copy.enabled = this.enabled;
        copy.roadWidth = this.roadWidth;
        copy.aStarStep = this.aStarStep;
        copy.segmentLength = this.segmentLength;
        copy.leadDistance = this.leadDistance;
        copy.directionBias = this.directionBias;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int roadWidth() { return roadWidth; }
    public void setRoadWidth(int v) { this.roadWidth = v; }
    public int aStarStep() { return aStarStep; }
    public void setAStarStep(int v) { this.aStarStep = v; }
    public int segmentLength() { return segmentLength; }
    public void setSegmentLength(int v) { this.segmentLength = v; }
    public int leadDistance() { return leadDistance; }
    public void setLeadDistance(int v) { this.leadDistance = v; }
    public double directionBias() { return directionBias; }
    public void setDirectionBias(double v) { this.directionBias = v; }
}
