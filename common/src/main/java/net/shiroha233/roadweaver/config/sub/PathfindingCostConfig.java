package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 负责维护寻路算法与采样精度相关的代价配置。
 */
public final class PathfindingCostConfig implements SubConfig {

    /** 寻路算法枚举。 */
    public enum PathfindingAlgorithm { ASTAR_BASIC, ASTAR_BIDIRECTIONAL, GRADIENT_DESCENT, POTENTIAL_FIELD }

    /** 采样精度模式枚举。 */
    public enum SamplingPrecision { FAST, PRECISE }

    private double orthoStepCost = RoadConstants.DEFAULT_ORTHO_STEP_COST;
    private double diagStepCost = RoadConstants.DEFAULT_DIAG_STEP_COST;
    private int elevationWeight = RoadConstants.DEFAULT_ELEVATION_WEIGHT;
    private int biomeWeight = RoadConstants.DEFAULT_BIOME_WEIGHT;
    private int stabilityWeight = RoadConstants.DEFAULT_STABILITY_WEIGHT;
    private int waterDepthWeight = RoadConstants.DEFAULT_WATER_DEPTH_WEIGHT;
    private int nearWaterCost = RoadConstants.DEFAULT_NEAR_WATER_COST;
    private int waterProximityCost = RoadConstants.DEFAULT_WATER_PROXIMITY_COST;
    private double heuristicWeight = RoadConstants.DEFAULT_HEURISTIC_WEIGHT;
    private double deviationWeight = RoadConstants.DEFAULT_DEVIATION_WEIGHT;
    private int aStarStep = RoadConstants.DEFAULT_ASTAR_STEP;
    private int aStarMaxSteps = RoadConstants.DEFAULT_ASTAR_MAX_STEPS;
    private PathfindingAlgorithm pathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT;
    private int quantizedSamplingChunkRadius = RoadConstants.DEFAULT_QUANTIZED_SAMPLING_CHUNK_RADIUS;
    private SamplingPrecision samplingPrecision = SamplingPrecision.PRECISE;

    @Override
    public void sanitize() {
        orthoStepCost = Math.max(0, orthoStepCost);
        diagStepCost = Math.max(0, diagStepCost);
        elevationWeight = Math.max(0, elevationWeight);
        biomeWeight = Math.max(0, biomeWeight);
        stabilityWeight = Math.max(0, stabilityWeight);
        waterDepthWeight = Math.max(0, waterDepthWeight);
        nearWaterCost = Math.max(0, nearWaterCost);
        waterProximityCost = Math.max(0, waterProximityCost);
        heuristicWeight = Math.max(0, heuristicWeight);
        deviationWeight = Math.max(0, deviationWeight);
        aStarStep = Math.min(RoadConstants.ASTAR_STEP_MAX, aStarStep);
        aStarMaxSteps = Math.max(
                RoadConstants.ASTAR_MAX_STEPS_MIN,
                Math.min(RoadConstants.ASTAR_MAX_STEPS_MAX, aStarMaxSteps));
        if (pathfindingAlgorithm == null) {
            pathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT;
        }
        if (samplingPrecision == null) {
            samplingPrecision = SamplingPrecision.PRECISE;
        }
        quantizedSamplingChunkRadius = Math.max(
                0,
                Math.min(RoadConstants.QUANTIZED_SAMPLING_CHUNK_RADIUS_MAX, quantizedSamplingChunkRadius));
    }

    @Override
    public PathfindingCostConfig snapshot() {
        PathfindingCostConfig copy = new PathfindingCostConfig();
        copy.orthoStepCost = this.orthoStepCost;
        copy.diagStepCost = this.diagStepCost;
        copy.elevationWeight = this.elevationWeight;
        copy.biomeWeight = this.biomeWeight;
        copy.stabilityWeight = this.stabilityWeight;
        copy.waterDepthWeight = this.waterDepthWeight;
        copy.nearWaterCost = this.nearWaterCost;
        copy.waterProximityCost = this.waterProximityCost;
        copy.heuristicWeight = this.heuristicWeight;
        copy.deviationWeight = this.deviationWeight;
        copy.aStarStep = this.aStarStep;
        copy.aStarMaxSteps = this.aStarMaxSteps;
        copy.pathfindingAlgorithm = this.pathfindingAlgorithm;
        copy.quantizedSamplingChunkRadius = this.quantizedSamplingChunkRadius;
        copy.samplingPrecision = this.samplingPrecision;
        return copy;
    }

    public double orthoStepCost() { return orthoStepCost; }
    public void setOrthoStepCost(double value) { this.orthoStepCost = value; }
    public double diagStepCost() { return diagStepCost; }
    public void setDiagStepCost(double value) { this.diagStepCost = value; }
    public int elevationWeight() { return elevationWeight; }
    public void setElevationWeight(int value) { this.elevationWeight = value; }
    public int biomeWeight() { return biomeWeight; }
    public void setBiomeWeight(int value) { this.biomeWeight = value; }
    public int stabilityWeight() { return stabilityWeight; }
    public void setStabilityWeight(int value) { this.stabilityWeight = value; }
    public int waterDepthWeight() { return waterDepthWeight; }
    public void setWaterDepthWeight(int value) { this.waterDepthWeight = value; }
    public int nearWaterCost() { return nearWaterCost; }
    public void setNearWaterCost(int value) { this.nearWaterCost = value; }
    public int waterProximityCost() { return waterProximityCost; }
    public void setWaterProximityCost(int value) { this.waterProximityCost = value; }
    public double heuristicWeight() { return heuristicWeight; }
    public void setHeuristicWeight(double value) { this.heuristicWeight = value; }
    public double deviationWeight() { return deviationWeight; }
    public void setDeviationWeight(double value) { this.deviationWeight = value; }
    public int aStarStep() { return aStarStep; }
    public void setAStarStep(int value) { this.aStarStep = value; }
    public int aStarMaxSteps() { return aStarMaxSteps; }
    public void setAStarMaxSteps(int value) { this.aStarMaxSteps = value; }
    public PathfindingAlgorithm pathfindingAlgorithm() { return pathfindingAlgorithm; }
    public void setPathfindingAlgorithm(PathfindingAlgorithm value) { this.pathfindingAlgorithm = value; }
    public PathfindingAlgorithm algorithm() { return pathfindingAlgorithm; }
    public void setAlgorithm(PathfindingAlgorithm value) { this.pathfindingAlgorithm = value; }
    public int quantizedSamplingChunkRadius() { return quantizedSamplingChunkRadius; }
    public void setQuantizedSamplingChunkRadius(int value) { this.quantizedSamplingChunkRadius = value; }
    public SamplingPrecision samplingPrecision() { return samplingPrecision; }
    public void setSamplingPrecision(SamplingPrecision value) { this.samplingPrecision = value; }

    /** 寻路阶段是否使用精确采样。 */
    public boolean isAccurateSampling() {
        return samplingPrecision != SamplingPrecision.FAST;
    }

    /** 普通采样使用后处理精化，高精度采样直接使用精确结果。 */
    public boolean needsRefinement() {
        return samplingPrecision != SamplingPrecision.PRECISE;
    }

    public int effectiveAStarStep() {
        if (aStarStep < RoadConstants.ASTAR_STEP_MIN) {
            return RoadConstants.DEFAULT_ASTAR_STEP;
        }
        if (aStarStep > RoadConstants.ASTAR_STEP_MAX) {
            return RoadConstants.ASTAR_STEP_MAX;
        }
        return aStarStep;
    }
}
