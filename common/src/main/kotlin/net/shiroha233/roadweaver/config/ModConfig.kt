package net.shiroha233.roadweaver.config

import java.util.ArrayList

class ModConfig {
    enum class PlanningAlgorithm {
        KNN,
        DELAUNAY,
        RNG,
        MST
    }

    enum class PathfindingAlgorithm {
        ASTAR_BASIC,
        ASTAR_BIDIRECTIONAL,
        GRADIENT_DESCENT
    }

    private var villagePredictionEnabled: Boolean = true
    private var predictRadiusChunks: Int = 1024
    private var biomePrefilter: Boolean = true
    private var structureWhitelist: MutableList<String> = ArrayList()
    private var structureBlacklist: MutableList<String> = ArrayList()

    // 路网规划配置
    private var initialPlanRadiusChunks: Int = 64
    private var dynamicPlanEnabled: Boolean = true
    private var dynamicPlanRadiusChunks: Int = 256
    private var dynamicPlanStrideChunks: Int = Math.max(8, Math.min(64, dynamicPlanRadiusChunks / 2))
    private var planningAlgorithm: PlanningAlgorithm = PlanningAlgorithm.RNG

    // 道路生成配置
    private var allowArtificial: Boolean = true
    private var allowNatural: Boolean = true
    private var placeWaypoints: Boolean = false
    private var spawnCabinEnabled: Boolean = true
    private var averagingRadius: Int = 8
    private var hierarchicalPathfindingEnabled: Boolean = false
    private var generationThreads: Int = Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors()))
    private var computeThreads: Int = 0
    private var initialGenerationThreads: Int = 6
    private var maxConcurrentGenerations: Int = Math.max(1, Math.min(3, generationThreads))
    private var threadDutyCycle: Int = 50
    private var aStarStep: Int = 16
    private var aStarMaxSteps: Int = 10000
    private var causewayMaxDepth: Int = 1
    private var maxSlopeStepPerTwoSegments: Int = 1
    private var slopeLimitEnabled: Boolean = true
    private var pathfindingAlgorithm: PathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT

    private var roadWidth: Int = 3
    private var roadSignsEnabled: Boolean = false
    private var lampInterval: Int = 32
    private var roadClearHeight: Int = 4
    private var tunnelEnabled: Boolean = false
    private var tunnelClearHeight: Int = 5
    private var preventTreesOnRoad: Boolean = true

    // 桥梁配置
    private var bridgeEnabled: Boolean = true
    private var bridgeDeckClearance: Int = 2
    private var bridgeMaxLengthBlocks: Int = 100
    private var bridgeUseBuoysInstead: Boolean = false
    private var bridgeUseBuoysWhenSkipped: Boolean = false
    private var buoyIntervalBlocks: Int = 32
    private var bridgePierInterval: Int = 6
    private var bridgePierWidth: Int = 1
    private var bridgePierMaxHeight: Int = 20
    private var bridgeKeepLamps: Boolean = true
    private var bridgeRampSegments: Int = 4
    private var bridgeMinWaterDepth: Int = 2
    private var bridgeMinLength: Int = 5
    private var bridgeMergeGap: Int = 8

    // 路边结构配置
    private var roadsideStructuresEnabled: Boolean = true
    private var maxStructuresPerRoad: Int = 3
    private var smallStructureOffset: Int = 8
    private var mediumStructureOffset: Int = 12
    private var largeStructureOffset: Int = 16

    // 结构距离控制
    private var villageRoadOffset: Int = 60
    private var otherStructureRoadOffset: Int = 25
    private var structureAvoidanceEnabled: Boolean = true
    private var structureRoadOffset: Int = 60

    // A* 寻路成本权重
    private var orthoStepCost: Double = 1.0
    private var diagStepCost: Double = 1.0
    private var elevationWeight: Int = 80
    private var biomeWeight: Int = 2
    private var stabilityWeight: Int = 15
    private var waterDepthWeight: Int = 80
    private var nearWaterCost: Int = 80
    private var waterProximityCost: Int = 20
    private var heuristicWeight: Double = 15.0
    private var deviationWeight: Double = 0.5

    init {
        structureWhitelist.add("#minecraft:village")
    }

    fun villagePredictionEnabled(): Boolean = villagePredictionEnabled
    fun setVillagePredictionEnabled(v: Boolean) {
        villagePredictionEnabled = v
    }

    fun predictRadiusChunks(): Int = predictRadiusChunks
    fun setPredictRadiusChunks(v: Int) {
        predictRadiusChunks = v
    }

    fun biomePrefilter(): Boolean = biomePrefilter
    fun setBiomePrefilter(v: Boolean) {
        biomePrefilter = v
    }

    fun structureWhitelist(): List<String> = structureWhitelist
    fun setStructureWhitelist(v: List<String>?) {
        structureWhitelist = if (v == null) ArrayList() else ArrayList(v)
    }

    fun structureBlacklist(): List<String> = structureBlacklist
    fun setStructureBlacklist(v: List<String>?) {
        structureBlacklist = if (v == null) ArrayList() else ArrayList(v)
    }

    fun sanitize() {
        if (structureWhitelist == null) structureWhitelist = ArrayList()
        if (structureBlacklist == null) structureBlacklist = ArrayList()

        if (predictRadiusChunks <= 0) predictRadiusChunks = 1024
        if (initialPlanRadiusChunks <= 0) initialPlanRadiusChunks = 64
        if (dynamicPlanRadiusChunks <= 0) dynamicPlanRadiusChunks = 256
        if (dynamicPlanStrideChunks <= 0) dynamicPlanStrideChunks = Math.max(8, Math.min(64, Math.max(1, dynamicPlanRadiusChunks) / 2))
        if (dynamicPlanStrideChunks > dynamicPlanRadiusChunks) dynamicPlanStrideChunks = dynamicPlanRadiusChunks
        if (dynamicPlanStrideChunks > 256) dynamicPlanStrideChunks = 256
        if (planningAlgorithm == null) planningAlgorithm = PlanningAlgorithm.RNG
        if (aStarStep > 128) aStarStep = 128
        if (aStarMaxSteps < 3000) aStarMaxSteps = 3000
        if (aStarMaxSteps > 100000) aStarMaxSteps = 100000
        if (causewayMaxDepth < 0) causewayMaxDepth = 0
        if (causewayMaxDepth > 12) causewayMaxDepth = 12
        if (maxSlopeStepPerTwoSegments < 0) maxSlopeStepPerTwoSegments = 0
        if (maxSlopeStepPerTwoSegments > 8) maxSlopeStepPerTwoSegments = 8

        if (computeThreads < 0) computeThreads = 0
        if (computeThreads > 128) computeThreads = 128

        if (threadDutyCycle < 1 || threadDutyCycle > 100) threadDutyCycle = 50

        if (pathfindingAlgorithm == null) {
            pathfindingAlgorithm = PathfindingAlgorithm.ASTAR_BASIC
        }

        if (roadWidth < 0) roadWidth = 0
        if (roadWidth > 15) roadWidth = 15
        if (lampInterval < 1) lampInterval = 59
        if (lampInterval > 2048) lampInterval = 2048
        if (roadClearHeight < 1) roadClearHeight = 4
        if (roadClearHeight > 16) roadClearHeight = 16
        if (tunnelClearHeight < 2) tunnelClearHeight = 2
        if (tunnelClearHeight > 16) tunnelClearHeight = 16

        if (bridgeDeckClearance < 1) bridgeDeckClearance = 1
        if (bridgeDeckClearance > 8) bridgeDeckClearance = 8
        if (bridgeMaxLengthBlocks < 0) bridgeMaxLengthBlocks = 0
        if (bridgeMaxLengthBlocks > 10000) bridgeMaxLengthBlocks = 10000
        if (buoyIntervalBlocks < 4) buoyIntervalBlocks = 4
        if (buoyIntervalBlocks > 256) buoyIntervalBlocks = 256
        if (bridgePierInterval < 3) bridgePierInterval = 3
        if (bridgePierInterval > 32) bridgePierInterval = 32
        if (bridgePierWidth < 1) bridgePierWidth = 1
        if (bridgePierWidth > 3) bridgePierWidth = 3
        if (bridgePierMaxHeight < 6) bridgePierMaxHeight = 6
        if (bridgePierMaxHeight > 64) bridgePierMaxHeight = 64
        if (bridgeRampSegments < 0) bridgeRampSegments = 0
        if (bridgeRampSegments > 12) bridgeRampSegments = 12

        if (orthoStepCost < 0) orthoStepCost = 0.0
        if (diagStepCost < 0) diagStepCost = 0.0
        if (elevationWeight < 0) elevationWeight = 0
        if (biomeWeight < 0) biomeWeight = 0
        if (stabilityWeight < 0) stabilityWeight = 0
        if (waterDepthWeight < 0) waterDepthWeight = 0
        if (nearWaterCost < 0) nearWaterCost = 0
        if (waterProximityCost < 0) waterProximityCost = 0
        if (heuristicWeight < 0) heuristicWeight = 0.0
        if (deviationWeight < 0) deviationWeight = 0.0

        if (maxStructuresPerRoad < 0) maxStructuresPerRoad = 0
        if (maxStructuresPerRoad > 20) maxStructuresPerRoad = 20
        if (smallStructureOffset < 1) smallStructureOffset = 1
        if (smallStructureOffset > 64) smallStructureOffset = 64
        if (mediumStructureOffset < 1) mediumStructureOffset = 1
        if (mediumStructureOffset > 64) mediumStructureOffset = 64
        if (largeStructureOffset < 1) largeStructureOffset = 1
        if (largeStructureOffset > 64) largeStructureOffset = 64

        if (villageRoadOffset < 0) villageRoadOffset = 0
        if (villageRoadOffset > 256) villageRoadOffset = 256
        if (otherStructureRoadOffset < 0) otherStructureRoadOffset = 0
        if (otherStructureRoadOffset > 256) otherStructureRoadOffset = 256
        if (structureRoadOffset < 0) structureRoadOffset = 0
        if (structureRoadOffset > 256) structureRoadOffset = 256
    }

    fun initialPlanRadiusChunks(): Int = initialPlanRadiusChunks
    fun setInitialPlanRadiusChunks(v: Int) {
        initialPlanRadiusChunks = v
    }

    fun dynamicPlanEnabled(): Boolean = dynamicPlanEnabled
    fun setDynamicPlanEnabled(v: Boolean) {
        dynamicPlanEnabled = v
    }

    fun dynamicPlanRadiusChunks(): Int = dynamicPlanRadiusChunks
    fun setDynamicPlanRadiusChunks(v: Int) {
        dynamicPlanRadiusChunks = v
    }

    fun dynamicPlanStrideChunks(): Int = dynamicPlanStrideChunks
    fun setDynamicPlanStrideChunks(v: Int) {
        dynamicPlanStrideChunks = v
    }

    fun allowArtificial(): Boolean = allowArtificial
    fun setAllowArtificial(v: Boolean) {
        allowArtificial = v
    }

    fun allowNatural(): Boolean = allowNatural
    fun setAllowNatural(v: Boolean) {
        allowNatural = v
    }

    fun placeWaypoints(): Boolean = placeWaypoints
    fun setPlaceWaypoints(v: Boolean) {
        placeWaypoints = v
    }

    fun spawnCabinEnabled(): Boolean = spawnCabinEnabled
    fun setSpawnCabinEnabled(v: Boolean) {
        spawnCabinEnabled = v
    }

    fun averagingRadius(): Int = averagingRadius
    fun setAveragingRadius(v: Int) {
        averagingRadius = v
    }

    fun hierarchicalPathfindingEnabled(): Boolean = hierarchicalPathfindingEnabled
    fun setHierarchicalPathfindingEnabled(v: Boolean) {
        hierarchicalPathfindingEnabled = v
    }

    fun generationThreads(): Int = generationThreads
    fun setGenerationThreads(v: Int) {
        generationThreads = v
    }

    fun computeThreads(): Int = computeThreads
    fun setComputeThreads(v: Int) {
        computeThreads = v
    }

    fun initialGenerationThreads(): Int = initialGenerationThreads
    fun setInitialGenerationThreads(v: Int) {
        initialGenerationThreads = v
    }

    fun maxConcurrentGenerations(): Int = maxConcurrentGenerations
    fun setMaxConcurrentGenerations(v: Int) {
        maxConcurrentGenerations = v
    }

    fun threadDutyCycle(): Int = threadDutyCycle
    fun setThreadDutyCycle(v: Int) {
        threadDutyCycle = Math.max(1, Math.min(100, v))
    }

    fun aStarStep(): Int = aStarStep
    fun setAStarStep(v: Int) {
        aStarStep = v
    }

    fun aStarMaxSteps(): Int = aStarMaxSteps
    fun setAStarMaxSteps(v: Int) {
        aStarMaxSteps = v
    }

    fun causewayMaxDepth(): Int = causewayMaxDepth
    fun setCausewayMaxDepth(v: Int) {
        causewayMaxDepth = v
    }

    fun maxSlopeStepPerTwoSegments(): Int = maxSlopeStepPerTwoSegments
    fun setMaxSlopeStepPerTwoSegments(v: Int) {
        maxSlopeStepPerTwoSegments = v
    }

    fun slopeLimitEnabled(): Boolean = slopeLimitEnabled
    fun setSlopeLimitEnabled(v: Boolean) {
        slopeLimitEnabled = v
    }

    fun pathfindingAlgorithm(): PathfindingAlgorithm = pathfindingAlgorithm
    fun setPathfindingAlgorithm(v: PathfindingAlgorithm?) {
        if (v != null) {
            pathfindingAlgorithm = v
        }
    }

    fun roadWidth(): Int = roadWidth
    fun setRoadWidth(v: Int) {
        roadWidth = v
    }

    fun roadSignsEnabled(): Boolean = roadSignsEnabled
    fun setRoadSignsEnabled(v: Boolean) {
        roadSignsEnabled = v
    }

    fun lampInterval(): Int = lampInterval
    fun setLampInterval(v: Int) {
        lampInterval = v
    }

    fun roadClearHeight(): Int = roadClearHeight
    fun setRoadClearHeight(v: Int) {
        roadClearHeight = v
    }

    fun planningAlgorithm(): PlanningAlgorithm = planningAlgorithm
    fun setPlanningAlgorithm(v: PlanningAlgorithm?) {
        if (v != null) {
            planningAlgorithm = v
        }
    }

    fun tunnelEnabled(): Boolean = tunnelEnabled
    fun setTunnelEnabled(v: Boolean) {
        tunnelEnabled = v
    }

    fun tunnelClearHeight(): Int = tunnelClearHeight
    fun setTunnelClearHeight(v: Int) {
        tunnelClearHeight = v
    }

    fun preventTreesOnRoad(): Boolean = preventTreesOnRoad
    fun setPreventTreesOnRoad(v: Boolean) {
        preventTreesOnRoad = v
    }

    fun bridgeEnabled(): Boolean = bridgeEnabled
    fun setBridgeEnabled(v: Boolean) {
        bridgeEnabled = v
    }

    fun bridgeDeckClearance(): Int = bridgeDeckClearance
    fun setBridgeDeckClearance(v: Int) {
        bridgeDeckClearance = v
    }

    fun bridgeMaxLengthBlocks(): Int = bridgeMaxLengthBlocks
    fun setBridgeMaxLengthBlocks(v: Int) {
        bridgeMaxLengthBlocks = v
    }

    fun bridgeUseBuoysInstead(): Boolean = bridgeUseBuoysInstead
    fun setBridgeUseBuoysInstead(v: Boolean) {
        bridgeUseBuoysInstead = v
    }

    fun bridgeUseBuoysWhenSkipped(): Boolean = bridgeUseBuoysWhenSkipped
    fun setBridgeUseBuoysWhenSkipped(v: Boolean) {
        bridgeUseBuoysWhenSkipped = v
    }

    fun buoyIntervalBlocks(): Int = buoyIntervalBlocks
    fun setBuoyIntervalBlocks(v: Int) {
        buoyIntervalBlocks = v
    }

    fun bridgePierInterval(): Int = bridgePierInterval
    fun setBridgePierInterval(v: Int) {
        bridgePierInterval = v
    }

    fun bridgePierWidth(): Int = bridgePierWidth
    fun setBridgePierWidth(v: Int) {
        bridgePierWidth = v
    }

    fun bridgePierMaxHeight(): Int = bridgePierMaxHeight
    fun setBridgePierMaxHeight(v: Int) {
        bridgePierMaxHeight = v
    }

    fun bridgeKeepLamps(): Boolean = bridgeKeepLamps
    fun setBridgeKeepLamps(v: Boolean) {
        bridgeKeepLamps = v
    }

    fun bridgeRampSegments(): Int = bridgeRampSegments
    fun setBridgeRampSegments(v: Int) {
        bridgeRampSegments = v
    }

    fun bridgeMinWaterDepth(): Int = bridgeMinWaterDepth
    fun setBridgeMinWaterDepth(v: Int) {
        bridgeMinWaterDepth = v
    }

    fun bridgeMinLength(): Int = bridgeMinLength
    fun setBridgeMinLength(v: Int) {
        bridgeMinLength = v
    }

    fun bridgeMergeGap(): Int = bridgeMergeGap
    fun setBridgeMergeGap(v: Int) {
        bridgeMergeGap = v
    }

    fun orthoStepCost(): Double = orthoStepCost
    fun setOrthoStepCost(v: Double) {
        orthoStepCost = v
    }

    fun diagStepCost(): Double = diagStepCost
    fun setDiagStepCost(v: Double) {
        diagStepCost = v
    }

    fun elevationWeight(): Int = elevationWeight
    fun setElevationWeight(v: Int) {
        elevationWeight = v
    }

    fun biomeWeight(): Int = biomeWeight
    fun setBiomeWeight(v: Int) {
        biomeWeight = v
    }

    fun stabilityWeight(): Int = stabilityWeight
    fun setStabilityWeight(v: Int) {
        stabilityWeight = v
    }

    fun waterDepthWeight(): Int = waterDepthWeight
    fun setWaterDepthWeight(v: Int) {
        waterDepthWeight = v
    }

    fun nearWaterCost(): Int = nearWaterCost
    fun setNearWaterCost(v: Int) {
        nearWaterCost = v
    }

    fun waterProximityCost(): Int = waterProximityCost
    fun setWaterProximityCost(v: Int) {
        waterProximityCost = v
    }

    fun heuristicWeight(): Double = heuristicWeight
    fun setHeuristicWeight(v: Double) {
        heuristicWeight = v
    }

    fun deviationWeight(): Double = deviationWeight
    fun setDeviationWeight(v: Double) {
        deviationWeight = v
    }

    fun roadsideStructuresEnabled(): Boolean = roadsideStructuresEnabled
    fun setRoadsideStructuresEnabled(v: Boolean) {
        roadsideStructuresEnabled = v
    }

    fun maxStructuresPerRoad(): Int = maxStructuresPerRoad
    fun setMaxStructuresPerRoad(v: Int) {
        maxStructuresPerRoad = Math.max(0, v)
    }

    fun smallStructureOffset(): Int = smallStructureOffset
    fun setSmallStructureOffset(v: Int) {
        smallStructureOffset = Math.max(1, v)
    }

    fun mediumStructureOffset(): Int = mediumStructureOffset
    fun setMediumStructureOffset(v: Int) {
        mediumStructureOffset = Math.max(1, v)
    }

    fun largeStructureOffset(): Int = largeStructureOffset
    fun setLargeStructureOffset(v: Int) {
        largeStructureOffset = Math.max(1, v)
    }

    fun villageRoadOffset(): Int = villageRoadOffset
    fun setVillageRoadOffset(v: Int) {
        villageRoadOffset = Math.max(0, Math.min(256, v))
    }

    fun otherStructureRoadOffset(): Int = otherStructureRoadOffset
    fun setOtherStructureRoadOffset(v: Int) {
        otherStructureRoadOffset = Math.max(0, Math.min(256, v))
    }

    fun structureAvoidanceEnabled(): Boolean = structureAvoidanceEnabled
    fun setStructureAvoidanceEnabled(v: Boolean) {
        structureAvoidanceEnabled = v
    }

    @Deprecated("兼容旧配置：structureRoadOffset 实际映射到 villageRoadOffset")
    fun structureRoadOffset(): Int = villageRoadOffset

    @Deprecated("兼容旧配置：structureRoadOffset 实际映射到 villageRoadOffset")
    fun setStructureRoadOffset(v: Int) {
        villageRoadOffset = Math.max(0, Math.min(256, v))
    }
}
