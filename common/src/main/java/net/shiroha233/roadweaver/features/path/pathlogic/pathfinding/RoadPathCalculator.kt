package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.config.PathfindingConfig
import net.shiroha233.roadweaver.config.RoadGenerationConfig
import net.shiroha233.roadweaver.features.path.pathlogic.core.RoadDirection
import net.shiroha233.roadweaver.helpers.Records
import kotlin.math.abs
import kotlin.math.max

object RoadPathCalculator {
    /**
     * 计算 A* 道路路径（带配置参数）
     *
     * @param startIn   起点
     * @param endIn     终点
     * @param width     道路宽度
     * @param level     服务端世界
     * @param maxSteps  最大步数
     * @param cache     地形采样缓存
     * @param cfg       道路生成配置快照
     */
    @JvmStatic
    fun calculateAStarRoadPath(
        startIn: BlockPos,
        endIn: BlockPos,
        width: Int,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache,
        cfg: RoadGenerationConfig
    ): List<Records.RoadSegmentPlacement>? {
        val pathCfg = cfg.pathfinding()
        val dGrid = pathCfg.effectiveAStarStep()
        val sx = snapToGrid(startIn.x, dGrid)
        val sz = snapToGrid(startIn.z, dGrid)
        val ex = snapToGrid(endIn.x, dGrid)
        val ez = snapToGrid(endIn.z, dGrid)

        val start = BlockPos(sx, startIn.y, sz)
        val end = BlockPos(ex, endIn.y, ez)

        val startGround = BlockPos(start.x, heightSampler(cache, start.x, start.z, level), start.z)
        val endGround = BlockPos(end.x, heightSampler(cache, end.x, end.z, level), end.z)

        if (cfg.hierarchicalPathfindingEnabled()) {
            // 注意：粗预热仅用于填充 TerrainSamplingCache，不参与最终道路路径。
            TerrainCachePrewarmer.prewarmAlongRoute(
                startGround,
                endGround,
                level,
                max(500, maxSteps / 4),
                cache
            )
        }

        return calculateDirect(startGround, endGround, width, level, maxSteps, cache, cfg, pathCfg)
    }

    private fun calculateDirect(
        startGround: BlockPos,
        endGround: BlockPos,
        width: Int,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache,
        cfg: RoadGenerationConfig,
        pathCfg: PathfindingConfig
    ): List<Records.RoadSegmentPlacement>? {
        val algo = cfg.pathfindingAlgorithm()

        return when (algo) {
            ModConfig.PathfindingAlgorithm.GRADIENT_DESCENT ->
                GradientDescentPathfinder.calculatePath(startGround, endGround, width, level, maxSteps, cache, pathCfg)
            ModConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL ->
                BidirectionalAStarPathfinder.calculateLandPath(startGround, endGround, width, level, maxSteps, cache, pathCfg)
            else ->
                BasicAStarPathfinder.calculateLandPath(startGround, endGround, width, level, maxSteps, cache, pathCfg)
        }
    }

    /**
     * 计算地形稳定性：检查四个方向的高度差。
     * 使用 A* 步长采样，确保采样点与邻居网格对齐，提高缓存命中率。
     */
    @JvmStatic
    fun calculateTerrainStability(cache: TerrainSamplingCache, pos: BlockPos, y: Int, level: ServerLevel, step: Int): Int {
        var cost = 0
        if (abs(heightSampler(cache, pos.x + step, pos.z, level) - y) > 0) cost++
        if (abs(heightSampler(cache, pos.x - step, pos.z, level) - y) > 0) cost++
        if (abs(heightSampler(cache, pos.x, pos.z + step, level) - y) > 0) cost++
        if (abs(heightSampler(cache, pos.x, pos.z - step, level) - y) > 0) cost++
        return cost
    }

    @JvmStatic
    fun heightSampler(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Int {
        return cache.height(level, x, z)
    }

    @JvmStatic
    fun isWaterLike(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Boolean {
        return cache.isWaterLike(level, x, z)
    }

    @JvmStatic
    fun oceanFloorSampler(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Int {
        return cache.oceanFloor(level, x, z)
    }

    @JvmStatic
    fun isNearWaterLike(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Boolean {
        return cache.isNearWaterLike(level, x, z, 16) // 默认步长
    }

    @JvmStatic
    fun isColumnWater(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Boolean {
        return cache.isColumnWater(level, x, z)
    }

    @JvmStatic
    fun snapToGrid(v: Int, gridSize: Int): Int {
        return Math.floorDiv(v, gridSize) * gridSize
    }

    @JvmStatic
    fun generateWidth(center: BlockPos, radius: Int, cache: MutableSet<BlockPos>, dir: RoadDirection): Set<BlockPos> {
        val set = HashSet<BlockPos>()
        val cx = center.x
        val cz = center.z
        val y = 0
        when (dir) {
            RoadDirection.X_AXIS -> {
                for (dz in -radius..radius) {
                    val p = BlockPos(cx, y, cz + dz)
                    if (cache.add(p)) set.add(p)
                }
            }
            RoadDirection.Z_AXIS -> {
                for (dx in -radius..radius) {
                    val p = BlockPos(cx + dx, y, cz)
                    if (cache.add(p)) set.add(p)
                }
            }
            else -> {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (dir == RoadDirection.DIAGONAL_2) {
                            if ((dx == -radius && dz == -radius) || (dx == radius && dz == radius)) continue
                        }
                        if (dir == RoadDirection.DIAGONAL_1) {
                            if ((dx == -radius && dz == radius) || (dx == radius && dz == -radius)) continue
                        }
                        val p = BlockPos(cx + dx, y, cz + dz)
                        if (cache.add(p)) set.add(p)
                    }
                }
            }
        }
        return set
    }

    /**
     * 提取道路跨度（桥梁、隧道等）
     *
     * @param segments 道路段落
     * @param level    服务端世界
     * @param cache    地形采样缓存
     * @param cfg      寻路配置快照
     */
    @JvmStatic
    fun extractSpans(
        segments: List<Records.RoadSegmentPlacement>?,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        cfg: PathfindingConfig
    ): List<Records.RoadSpan> {
        val spans = ArrayList<Records.RoadSpan>()
        if (segments.isNullOrEmpty()) return spans

        val centers = segments.map { it.middlePos() }

        // 从配置快照读取最小水深阈值
        val minWaterDepth = cfg.bridgeMinWaterDepth()
        val sea = level.seaLevel

        var inWater = false
        var waterStart = -1
        for (i in centers.indices) {
            val p = centers[i]
            // 检测是否是水体且水深达到阈值
            val isWater = isColumnWater(cache, p.x, p.z, level)
            var waterDepth = 0
            if (isWater) {
                val oceanFloor = oceanFloorSampler(cache, p.x, p.z, level)
                waterDepth = max(0, sea - oceanFloor)
            }
            val water = isWater && waterDepth >= minWaterDepth

            if (water && !inWater) {
                inWater = true
                waterStart = i
            } else if (!water && inWater) {
                // 离开水域，创建桥梁跨度
                val startIdx = max(0, waterStart - 1)
                val endIdx = i
                val start = centers[startIdx]
                val end = centers[minOf(endIdx, centers.size - 1)]
                spans.add(Records.RoadSpan(start, end, Records.SpanType.BRIDGE))
                inWater = false
                waterStart = -1
            }
        }
        // 修复：如果道路在水中结束（最后一段仍在水上），需要补上这个 span
        if (inWater && waterStart >= 0) {
            val startIdx = max(0, waterStart - 1)
            val start = centers[startIdx]
            val end = centers[centers.size - 1]
            spans.add(Records.RoadSpan(start, end, Records.SpanType.BRIDGE))
        }

        val SLOPE_ABS_THRESHOLD = 4
        val RUN_MIN_LENGTH = 3
        var runStart = -1
        for (i in 1 until centers.size) {
            val a = centers[i - 1]
            val b = centers[i]
            val ya = heightSampler(cache, a.x, a.z, level)
            val yb = heightSampler(cache, b.x, b.z, level)
            val dy = abs(yb - ya)
            val steep = dy >= SLOPE_ABS_THRESHOLD
            if (steep) {
                if (runStart < 0) runStart = i - 1
            } else if (runStart >= 0) {
                val len = i - runStart
                if (len >= RUN_MIN_LENGTH) {
                    val s = centers[runStart]
                    val e = centers[i]
                    spans.add(Records.RoadSpan(s, e, Records.SpanType.TUNNEL))
                }
                runStart = -1
            }
        }
        if (runStart >= 0) {
            val len = centers.size - runStart
            if (len >= RUN_MIN_LENGTH) {
                val s = centers[runStart]
                val e = centers[centers.size - 1]
                spans.add(Records.RoadSpan(s, e, Records.SpanType.TUNNEL))
            }
        }

        return spans
    }
}
