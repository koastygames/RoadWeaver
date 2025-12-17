package net.shiroha233.roadweaver.features.path.pathlogic.core

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.search.StructurePredictor
import kotlin.math.max
import kotlin.math.min

/**
 * 专门负责根据结构类型决定道路端点需要从结构点向外缩进多少格。
 */
object StructureRoadOffsetService {
    private enum class StructureCategory {
        VILLAGE,
        OTHER,
        UNKNOWN
    }

    // 当坐标与预测结构点不完全重合时，允许的匹配容差（半径，单位：方块）
    private const val MATCH_TOLERANCE_BLOCKS = 16

    /**
     * @deprecated 使用 trimPathNearStructure 代替，在寻路完成后裁剪路径
     */
    @Deprecated("Use trimPathNearStructure")
    @JvmStatic
    fun adjustEndpoint(level: ServerLevel, endpoint: BlockPos, otherEnd: BlockPos): BlockPos {
        return endpoint
    }

    /**
     * 在 A* 寻路完成后，裁剪掉进入结构保护区的路段。
     */
    @JvmStatic
    fun trimPathNearStructure(
        level: ServerLevel,
        segments: List<Records.RoadSegmentPlacement>?,
        rawStart: BlockPos,
        rawEnd: BlockPos
    ): List<Records.RoadSegmentPlacement>? {
        if (segments == null || segments.size < 3) return segments
        if (Level.OVERWORLD != level.dimension()) return segments

        val startOffset = getOffsetBlocksForEndpoint(level, rawStart)
        val endOffset = getOffsetBlocksForEndpoint(level, rawEnd)

        if (startOffset <= 0 && endOffset <= 0) return segments

        val n = segments.size
        var trimStart = 0
        var trimEnd = n

        if (startOffset > 0) {
            val offsetSq = startOffset.toLong() * startOffset.toLong()
            for (i in 0 until n) {
                val pos = segments[i].middlePos
                val dx = pos.x.toLong() - rawStart.x.toLong()
                val dz = pos.z.toLong() - rawStart.z.toLong()
                val distSq = dx * dx + dz * dz
                if (distSq >= offsetSq) {
                    trimStart = i
                    break
                }
            }
        }

        if (endOffset > 0) {
            val offsetSq = endOffset.toLong() * endOffset.toLong()
            for (i in (n - 1) downTo 0) {
                val pos = segments[i].middlePos
                val dx = pos.x.toLong() - rawEnd.x.toLong()
                val dz = pos.z.toLong() - rawEnd.z.toLong()
                val distSq = dx * dx + dz * dz
                if (distSq >= offsetSq) {
                    trimEnd = i + 1
                    break
                }
            }
        }

        if (trimStart >= trimEnd || trimEnd - trimStart < 3) {
            val mid = n / 2
            trimStart = max(0, mid - 2)
            trimEnd = min(n, mid + 3)
        }

        return ArrayList(segments.subList(trimStart, trimEnd))
    }

    /**
     * 获取指定端点的保护区半径（方块数）
     */
    @JvmStatic
    fun getOffsetBlocksForEndpoint(level: ServerLevel, endpoint: BlockPos): Int {
        val cat = detectCategory(level, endpoint)
        val cfg = ConfigService.get()
        return when (cat) {
            StructureCategory.VILLAGE -> cfg.villageRoadOffset()
            StructureCategory.OTHER -> cfg.otherStructureRoadOffset()
            else -> 0
        }
    }

    private fun detectCategory(level: ServerLevel, endpoint: BlockPos): StructureCategory {
        if (Level.OVERWORLD != level.dimension()) return StructureCategory.UNKNOWN

        val cx = endpoint.x shr 4
        val cz = endpoint.z shr 4
        val searchRadius = 1

        val tolerance = max(MATCH_TOLERANCE_BLOCKS, ConfigService.get().aStarStep() + 8)
        val tol2 = tolerance.toLong() * tolerance.toLong()

        val villageInfos = StructurePredictor.predictOverworldStructuresInRect(
            level,
            cx - searchRadius,
            cz - searchRadius,
            cx + searchRadius,
            cz + searchRadius,
            true,
            listOf("#minecraft:village"),
            listOf()
        )
        if (!villageInfos.isNullOrEmpty()) {
            for (info in villageInfos) {
                val p = info.pos
                val dx = p.x.toLong() - endpoint.x.toLong()
                val dz = p.z.toLong() - endpoint.z.toLong()
                val d2 = dx * dx + dz * dz
                if (d2 <= tol2) {
                    return StructureCategory.VILLAGE
                }
            }
        }

        val cfg = ConfigService.get()
        val whitelist = cfg.structureWhitelist()
        if (!whitelist.isNullOrEmpty()) {
            val otherInfos = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cx - searchRadius,
                cz - searchRadius,
                cx + searchRadius,
                cz + searchRadius,
                true,
                whitelist,
                cfg.structureBlacklist()
            )
            if (!otherInfos.isNullOrEmpty()) {
                for (info in otherInfos) {
                    val p = info.pos
                    val dx = p.x.toLong() - endpoint.x.toLong()
                    val dz = p.z.toLong() - endpoint.z.toLong()
                    val d2 = dx * dx + dz * dz
                    if (d2 <= tol2) {
                        return StructureCategory.OTHER
                    }
                }
            }
        }

        return StructureCategory.UNKNOWN
    }
}
