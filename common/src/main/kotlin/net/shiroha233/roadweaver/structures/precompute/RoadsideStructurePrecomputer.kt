package net.shiroha233.roadweaver.structures.precompute

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Rotation
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.structures.data.BiomeCategory
import net.shiroha233.roadweaver.structures.data.StructureScale
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry.RoadsideStructureEntry
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 路边结构预计算器
 *
 * 在道路寻路完成后，预计算结构放置位置并存储到 PendingStructureStorage。
 * 这样在区块 STRUCTURE_STARTS 阶段可以注入结构，让 Beardifier 自动处理地形适应。
 */
object RoadsideStructurePrecomputer {
    private val LOGGER = LoggerFactory.getLogger("RoadWeaver/StructurePrecomputer")

    /**
     * 预计算道路沿线的结构放置位置
     *
     * @param level    服务端世界
     * @param segments 道路路径段
     * @param spans    道路跨度（桥梁等）
     * @param width    道路宽度
     * @param cache    地形采样缓存
     * @param random   随机源
     * @return 预计算的结构数量
     */
    @JvmStatic
    fun precomputeStructures(
        level: ServerLevel,
        segments: List<Records.RoadSegmentPlacement>?,
        spans: List<Records.RoadSpan>?,
        width: Int,
        cache: TerrainSamplingCache,
        random: RandomSource
    ): Int {
        if (segments == null || segments.size < 10) return 0

        val cfg: ModConfig = ConfigService.get()

        // 检查是否启用路边结构
        if (!cfg.roadsideStructuresEnabled()) return 0

        val maxStructures = cfg.maxStructuresPerRoad()
        if (maxStructures <= 0) return 0

        // 获取可用的路边结构
        val allStructures: List<RoadsideStructureEntry> = RoadsideStructureRegistry.getAll(level)
        if (allStructures.isEmpty()) return 0

        // 标记桥梁段
        val bridgeIndices = HashSet<Int>()
        if (spans != null) {
            for (span in spans) {
                if (span.type == Records.SpanType.BRIDGE) {
                    for (i in segments.indices) {
                        val pos = segments[i].middlePos
                        if (isInSpan(pos, span)) {
                            bridgeIndices.add(i)
                        }
                    }
                }
            }
        }

        // 计算检查间隔
        val roadLength = segments.size
        val checkInterval = max(1, roadLength / (maxStructures + 1))

        // 记录已放置的位置（避免重叠）
        val placedChunks = HashSet<Long>()
        var placedCount = 0

        var i = checkInterval
        while (i < roadLength - checkInterval && placedCount < maxStructures) {
            if (bridgeIndices.contains(i)) {
                i += checkInterval
                continue
            }

            val middle = segments[i].middlePos

            // 使用较大窗口（±10 segments，约10格）计算稳定的切线方向
            val windowSize = 10
            val prev = segments[max(0, i - windowSize)].middlePos
            val next = segments[min(roadLength - 1, i + windowSize)].middlePos

            // 计算道路方向（切线）
            var dirX = (next.x - prev.x).toDouble()
            var dirZ = (next.z - prev.z).toDouble()
            val len = sqrt(dirX * dirX + dirZ * dirZ)
            if (len < 0.01) {
                i += checkInterval
                continue
            }
            dirX /= len
            dirZ /= len

            // 获取群系（使用噪声采样，不触发区块加载）
            val biomeHolder: Holder<Biome> = cache.getBiome(level, middle.x, middle.z)
            val category = BiomeCategory.fromBiome(biomeHolder)

            // 选择合适的结构
            val entry = selectStructure(allStructures, category, roadLength, random)
            if (entry == null) {
                i += checkInterval
                continue
            }

            val structure: RoadsideStructure = entry.structure
            val sizeHint: Vec3i = structure.sizeHint()

            // 计算放置位置（道路两侧）
            val leftSide = random.nextBoolean()
            val offset = getOffsetForScale(structure.scale(), cfg)

            // 先计算旋转（需要知道旋转才能正确补偿锚点偏移）
            val rotation = calculateRotation(dirX, dirZ, leftSide, structure.faceRoad())

            // 使用道路方向的法线（perpendicular）进行侧向偏移
            val perpX = if (leftSide) -dirZ else dirZ
            val perpZ = if (leftSide) dirX else -dirX

            val sizeX = sizeHint.x
            val sizeZ = sizeHint.z

            // 计算旋转后结构在法线方向上的半尺寸
            val halfExtentInPerpDir = when (rotation) {
                Rotation.NONE, Rotation.CLOCKWISE_180 -> (abs(perpX) * sizeX + abs(perpZ) * sizeZ) / 2.0
                Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90 -> (abs(perpX) * sizeZ + abs(perpZ) * sizeX) / 2.0
                else -> max(sizeX, sizeZ) / 2.0
            }

            val centerOffset = offset + halfExtentInPerpDir

            val placeX = middle.x + round(perpX * centerOffset).toInt()
            val placeZ = middle.z + round(perpZ * centerOffset).toInt()
            val placeY = cache.height(level, placeX, placeZ)

            // 锚点在结构角落，需要从中心点反推锚点位置
            var anchorX = placeX
            var anchorZ = placeZ
            when (rotation) {
                Rotation.NONE -> {
                    anchorX -= sizeX / 2
                    anchorZ -= sizeZ / 2
                }
                Rotation.CLOCKWISE_90 -> {
                    anchorX += sizeZ / 2
                    anchorZ -= sizeX / 2
                }
                Rotation.CLOCKWISE_180 -> {
                    anchorX += sizeX / 2
                    anchorZ += sizeZ / 2
                }
                Rotation.COUNTERCLOCKWISE_90 -> {
                    anchorX -= sizeZ / 2
                    anchorZ += sizeX / 2
                }
            }

            val placePos = BlockPos(anchorX, placeY, anchorZ)

            // 检查区块是否已有结构
            val chunkPos = ChunkPos(placePos)
            val chunkKey = chunkPos.toLong()
            if (placedChunks.contains(chunkKey)) {
                i += checkInterval
                continue
            }

            // 检查地形条件
            if (!checkTerrainConditions(cache, level, placePos, sizeHint)) {
                i += checkInterval
                continue
            }

            // 添加到待放置存储（rotation 已在前面计算）
            PendingStructureStorage.addPendingStructure(
                level,
                entry.id,
                placePos,
                rotation,
                sizeHint.x,
                sizeHint.y,
                sizeHint.z
            )

            placedChunks.add(chunkKey)
            placedCount++

            LOGGER.debug("Precomputed structure {} at {} for chunk [{}, {}]", entry.id, placePos, chunkPos.x, chunkPos.z)

            i += checkInterval
        }

        return placedCount
    }

    /**
     * 检查位置是否在跨度范围内
     */
    private fun isInSpan(pos: BlockPos, span: Records.RoadSpan): Boolean {
        val minX = min(span.start.x, span.end.x)
        val maxX = max(span.start.x, span.end.x)
        val minZ = min(span.start.z, span.end.z)
        val maxZ = max(span.start.z, span.end.z)
        return pos.x in minX..maxX && pos.z in minZ..maxZ
    }

    /**
     * 选择合适的结构
     */
    private fun selectStructure(
        structures: List<RoadsideStructureEntry>,
        category: BiomeCategory,
        roadLength: Int,
        random: RandomSource
    ): RoadsideStructureEntry? {
        val candidates = ArrayList<RoadsideStructureEntry>()
        var totalWeight = 0

        for (entry in structures) {
            val structure = entry.structure

            // 检查群系匹配
            if (!structure.placementRule().isBiomeAllowed(category)) continue

            // 检查道路长度
            if (roadLength < structure.placementRule().minRoadLength) continue

            candidates.add(entry)
            totalWeight += structure.weight()
        }

        if (candidates.isEmpty() || totalWeight <= 0) return null

        val roll = random.nextInt(totalWeight)
        var cumulative = 0
        for (entry in candidates) {
            cumulative += entry.structure.weight()
            if (roll < cumulative) return entry
        }

        return candidates[candidates.size - 1]
    }

    /**
     * 检查地形条件
     * 注意：使用 cache 的噪声采样方法，避免触发区块加载
     */
    private fun checkTerrainConditions(
        cache: TerrainSamplingCache,
        level: ServerLevel,
        pos: BlockPos,
        size: Vec3i
    ): Boolean {
        // 使用 cache 检查是否在水中，避免触发区块加载
        if (cache.isColumnWater(level, pos.x, pos.z)) return false

        val centerY = cache.height(level, pos.x, pos.z)
        val halfX = size.x / 2
        val halfZ = size.z / 2

        val maxSlope = 3
        val y1 = cache.height(level, pos.x - halfX, pos.z)
        val y2 = cache.height(level, pos.x + halfX, pos.z)
        val y3 = cache.height(level, pos.x, pos.z - halfZ)
        val y4 = cache.height(level, pos.x, pos.z + halfZ)

        return abs(y1 - centerY) <= maxSlope &&
            abs(y2 - centerY) <= maxSlope &&
            abs(y3 - centerY) <= maxSlope &&
            abs(y4 - centerY) <= maxSlope
    }

    /**
     * 根据结构规模获取偏移距离
     */
    private fun getOffsetForScale(scale: StructureScale, cfg: ModConfig): Int {
        return when (scale) {
            StructureScale.SMALL -> cfg.smallStructureOffset()
            StructureScale.MEDIUM -> cfg.mediumStructureOffset()
            StructureScale.LARGE -> cfg.largeStructureOffset()
        }
    }

    /**
     * 计算结构旋转
     */
    private fun calculateRotation(dirX: Double, dirZ: Double, leftSide: Boolean, faceRoad: Boolean): Rotation {
        if (!faceRoad) return Rotation.NONE

        val absX = abs(dirX)
        val absZ = abs(dirZ)

        return if (absX > absZ) {
            // 道路主要沿 X 轴
            if (leftSide) {
                if (dirX > 0) Rotation.CLOCKWISE_180 else Rotation.NONE
            } else {
                if (dirX > 0) Rotation.NONE else Rotation.CLOCKWISE_180
            }
        } else {
            // 道路主要沿 Z 轴
            if (leftSide) {
                if (dirZ > 0) Rotation.COUNTERCLOCKWISE_90 else Rotation.CLOCKWISE_90
            } else {
                if (dirZ > 0) Rotation.CLOCKWISE_90 else Rotation.COUNTERCLOCKWISE_90
            }
        }
    }
}
