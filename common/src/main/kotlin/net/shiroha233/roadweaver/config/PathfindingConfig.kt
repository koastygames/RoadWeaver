package net.shiroha233.roadweaver.config

/**
 * 寻路配置快照（不可变）。
 * 在入口层从 ModConfig 创建，传递给寻路器，避免热路径中频繁访问全局单例。
 */
data class PathfindingConfig(
    val aStarStep: Int,
    val orthoStepCost: Double,
    val diagStepCost: Double,
    val elevationWeight: Double,
    val biomeWeight: Double,
    val stabilityWeight: Double,
    val waterDepthWeight: Double,
    val nearWaterCost: Double,
    val deviationWeight: Double,
    val heuristicWeight: Double,
    val threadDutyCycle: Int,
    val bridgeMinWaterDepth: Int
) {
    fun aStarStep(): Int = aStarStep

    fun orthoStepCost(): Double = orthoStepCost

    fun diagStepCost(): Double = diagStepCost

    fun elevationWeight(): Double = elevationWeight

    fun biomeWeight(): Double = biomeWeight

    fun stabilityWeight(): Double = stabilityWeight

    fun waterDepthWeight(): Double = waterDepthWeight

    fun nearWaterCost(): Double = nearWaterCost

    fun deviationWeight(): Double = deviationWeight

    fun heuristicWeight(): Double = heuristicWeight

    fun threadDutyCycle(): Int = threadDutyCycle

    fun bridgeMinWaterDepth(): Int = bridgeMinWaterDepth

    companion object {
        @JvmStatic
        fun from(cfg: ModConfig): PathfindingConfig {
            return PathfindingConfig(
                cfg.aStarStep(),
                cfg.orthoStepCost(),
                cfg.diagStepCost(),
                cfg.elevationWeight().toDouble(),
                cfg.biomeWeight().toDouble(),
                cfg.stabilityWeight().toDouble(),
                cfg.waterDepthWeight().toDouble(),
                cfg.nearWaterCost().toDouble(),
                cfg.deviationWeight(),
                cfg.heuristicWeight(),
                cfg.threadDutyCycle(),
                cfg.bridgeMinWaterDepth()
            )
        }
    }

    fun effectiveAStarStep(): Int {
        if (aStarStep < 4) return 16
        if (aStarStep > 128) return 128
        return aStarStep
    }

    fun shouldThrottle(): Boolean = threadDutyCycle < 100
}
