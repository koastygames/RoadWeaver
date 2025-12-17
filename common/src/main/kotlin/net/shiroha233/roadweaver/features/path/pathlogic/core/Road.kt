package net.shiroha233.roadweaver.features.path.pathlogic.core

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.config.PresetService
import net.shiroha233.roadweaver.config.RoadGenerationConfig
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage
import net.shiroha233.roadweaver.structures.precompute.RoadsideStructurePrecomputer

/**
 * 道路生成器
 *
 * 职责：根据结构连接生成一条道路，包括寻路、路面生成、路边结构预计算
 *
 * 设计原则：
 * - 接受配置快照，不依赖全局单例
 * - 所有配置在入口层读取，通过参数传递
 */
class Road(
    private val level: ServerLevel,
    private val connection: Records.StructureConnection,
    private val featureConfig: PathFeatureConfig,
    private val genConfig: RoadGenerationConfig
) {

    /**
     * 兼容旧 API：从全局配置创建
     * @deprecated 使用带 RoadGenerationConfig 参数的构造函数
     */
    @Deprecated("Use ctor with RoadGenerationConfig")
    constructor(level: ServerLevel, connection: Records.StructureConnection, config: PathFeatureConfig) :
        this(level, connection, config, RoadGenerationConfig.from(ConfigService.get()))

    /**
     * 生成道路
     *
     * @param maxSteps 最大寻路步数
     */
    fun generateRoad(maxSteps: Int) {
        val random = RandomSource.create()
        val width = genConfig.effectiveRoadWidth(getRandomWidth(random, featureConfig))

        val allowA = genConfig.allowArtificial()
        val allowN = genConfig.allowNatural()
        if (!allowA && !allowN) return

        val type = if (allowA && allowN) {
            if (random.nextBoolean()) 0 else 1
        } else {
            if (allowA) 0 else 1
        }

        val materials: List<BlockState>
        var slabMaterials: List<BlockState> = listOf()

        if (type == 0) {
            // 人工道路始终从 JSON 预设系统中选择一套材质
            val modCfg: ModConfig = ConfigService.get() // 预设服务仍需要访问全局配置
            val preset = PresetService.choosePresetForArtificial(random, modCfg)
            materials = PresetService.toBlockStatesFromIds(preset.materials())
            slabMaterials = PresetService.toBlockStatesFromIds(preset.slabMaterials())
        } else {
            materials = listOf(Blocks.DIRT_PATH.defaultBlockState(), Blocks.GRAVEL.defaultBlockState())
        }

        val rawStart = connection.from
        val rawEnd = connection.to

        // 直接用原始端点做 A* 寻路，不预设偏移方向
        val cache = TerrainSamplingCache()
        try {
            val rawSegments = RoadPathCalculator.calculateAStarRoadPath(rawStart, rawEnd, width, level, maxSteps, cache, genConfig)
            if (rawSegments == null || rawSegments.size < 5) return

            // 寻路完成后，根据实际路径方向裁剪掉进入结构保护区的路段
            // 这样即使路径从意外方向绕过来，也不会穿过结构
            val segments = StructureRoadOffsetService.trimPathNearStructure(level, rawSegments, rawStart, rawEnd)
            if (segments == null || segments.size < 5) return

            val spans = RoadPathCalculator.extractSpans(segments, level, cache, genConfig.pathfinding())
            val targetY = computeTargetY(level, segments, spans, cache, genConfig)

            val rd = Records.RoadData(width, type, materials, slabMaterials, segments, spans, targetY)
            RoadShardStorage.addRoad(level, rd)

            // 寻路完成后，预计算路边结构位置
            // 如果区块还没生成，结构会在 STRUCTURE_STARTS 阶段注入，Beardifier 会自动处理地形
            // 如果区块已经生成，则在 Feature 阶段通过 RoadsideStructurePlacer 放置（无地形适应）
            RoadsideStructurePrecomputer.precomputeStructures(level, segments, spans, width, cache, random)
        } finally {
            // 单条道路生成结束后清空噪声采样缓存，避免长时间占用内存
            cache.clear()
        }
    }

    companion object {
        private fun getRandomWidth(rnd: RandomSource, cfg: PathFeatureConfig): Int {
            return 3
        }

        private fun computeTargetY(
            level: ServerLevel,
            segments: List<Records.RoadSegmentPlacement>,
            spans: List<Records.RoadSpan>?,
            cache: TerrainSamplingCache,
            cfg: RoadGenerationConfig
        ): List<Int> {
            val n = segments.size
            val centers = ArrayList<BlockPos>(n)
            for (s in segments) centers.add(s.middlePos)

            // 将 spans 映射到索引范围，用于 BRIDGE
            val isBridge = BooleanArray(n)
            if (!spans.isNullOrEmpty()) {
                val indexMap = HashMap<Long, Int>()
                for (i in centers.indices) indexMap[centers[i].asLong()] = i

                for (sp in spans) {
                    if (sp.type != Records.SpanType.BRIDGE) continue
                    val si = indexMap[sp.start.asLong()]
                    val ei = indexMap[sp.end.asLong()]
                    if (si == null || ei == null) continue

                    val a = maxOf(0, minOf(si, ei))
                    val b = minOf(n - 1, maxOf(si, ei))
                    for (k in a..b) isBridge[k] = true
                }
            }

            val avg = maxOf(0, cfg.averagingRadius())
            val base = IntArray(n)
            for (i in 0 until n) {
                var sum = 0
                var cnt = 0
                val lo = maxOf(0, i - avg)
                val hi = minOf(n - 1, i + avg)
                for (j in lo..hi) {
                    val sp = centers[j]
                    val yTop = cache.height(level, sp.x, sp.z)
                    sum += yTop
                    cnt++
                }
                base[i] = if (cnt > 0) kotlin.math.round(sum.toDouble() / cnt.toDouble()).toInt() else centers[i].y
            }

            // 如果关闭限坡平滑，则直接使用基础平均高度，不再进行每两段步进限制
            if (!cfg.slopeLimitEnabled()) {
                return base.toList()
            }

            val smoothed = base.clone()
            // 对每个连续非桥梁段进行平滑，以避免奇偶振荡
            var i = 0
            while (i < n) {
                while (i < n && isBridge[i]) i++
                val s = i
                while (i < n && !isBridge[i]) i++
                val e = i - 1

                if (s <= e) {
                    val step2 = maxOf(0, minOf(8, cfg.maxSlopeStepPerTwoSegments()))
                    val halfLow = maxOf(0, step2 / 2)
                    val halfHigh = maxOf(0, (step2 + 1) / 2)

                    for (ii in (s + 1)..e) {
                        var y = smoothed[ii]
                        if (ii == s + 1) {
                            val py = smoothed[ii - 1]
                            if (y > py + halfLow) y = py + halfLow
                            if (y < py - halfLow) y = py - halfLow
                        } else {
                            val py = smoothed[ii - 1]
                            if (y > py + halfHigh) y = py + halfHigh
                            if (y < py - halfHigh) y = py - halfHigh

                            val p2 = smoothed[ii - 2]
                            val hi = p2 + step2
                            val lo = p2 - step2
                            if (y > hi) y = hi
                            if (y < lo) y = lo
                        }
                        smoothed[ii] = y
                    }

                    for (ii in (e - 1) downTo s) {
                        var y = smoothed[ii]
                        if (ii == e - 1) {
                            val ny = smoothed[ii + 1]
                            if (y > ny + halfLow) y = ny + halfLow
                            if (y < ny - halfLow) y = ny - halfLow
                        } else {
                            val ny = smoothed[ii + 1]
                            if (y > ny + halfHigh) y = ny + halfHigh
                            if (y < ny - halfHigh) y = ny - halfHigh

                            val n2 = smoothed[ii + 2]
                            val hi = n2 + step2
                            val lo = n2 - step2
                            if (y > hi) y = hi
                            if (y < lo) y = lo
                        }
                        smoothed[ii] = y
                    }
                }
            }

            return smoothed.toList()
        }
    }
}
