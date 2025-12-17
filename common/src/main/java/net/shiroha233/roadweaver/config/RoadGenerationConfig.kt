package net.shiroha233.roadweaver.config

/**
 * 道路生成配置快照（不可变）。
 * 封装道路生成过程中需要的所有配置，避免在生成过程中频繁访问全局单例。
 */
data class RoadGenerationConfig(
    val pathfinding: PathfindingConfig,
    val hierarchicalPathfindingEnabled: Boolean,
    val roadWidth: Int,
    val allowArtificial: Boolean,
    val allowNatural: Boolean,
    val averagingRadius: Int,
    val slopeLimitEnabled: Boolean,
    val maxSlopeStepPerTwoSegments: Int,
    val roadsideStructuresEnabled: Boolean,
    val maxStructuresPerRoad: Int,
    val smallStructureOffset: Int,
    val mediumStructureOffset: Int,
    val largeStructureOffset: Int,
    val pathfindingAlgorithm: ModConfig.PathfindingAlgorithm
) {
    fun pathfinding(): PathfindingConfig = pathfinding

    fun hierarchicalPathfindingEnabled(): Boolean = hierarchicalPathfindingEnabled

    fun roadWidth(): Int = roadWidth

    fun allowArtificial(): Boolean = allowArtificial

    fun allowNatural(): Boolean = allowNatural

    fun averagingRadius(): Int = averagingRadius

    fun slopeLimitEnabled(): Boolean = slopeLimitEnabled

    fun maxSlopeStepPerTwoSegments(): Int = maxSlopeStepPerTwoSegments

    fun roadsideStructuresEnabled(): Boolean = roadsideStructuresEnabled

    fun maxStructuresPerRoad(): Int = maxStructuresPerRoad

    fun smallStructureOffset(): Int = smallStructureOffset

    fun mediumStructureOffset(): Int = mediumStructureOffset

    fun largeStructureOffset(): Int = largeStructureOffset

    fun pathfindingAlgorithm(): ModConfig.PathfindingAlgorithm = pathfindingAlgorithm

    companion object {
        @JvmStatic
        fun from(cfg: ModConfig): RoadGenerationConfig {
            return RoadGenerationConfig(
                PathfindingConfig.from(cfg),
                cfg.hierarchicalPathfindingEnabled(),
                cfg.roadWidth(),
                cfg.allowArtificial(),
                cfg.allowNatural(),
                cfg.averagingRadius(),
                cfg.slopeLimitEnabled(),
                cfg.maxSlopeStepPerTwoSegments(),
                cfg.roadsideStructuresEnabled(),
                cfg.maxStructuresPerRoad(),
                cfg.smallStructureOffset(),
                cfg.mediumStructureOffset(),
                cfg.largeStructureOffset(),
                cfg.pathfindingAlgorithm()
            )
        }
    }

    fun effectiveRoadWidth(defaultWidth: Int): Int = if (roadWidth > 0) roadWidth else defaultWidth
}
