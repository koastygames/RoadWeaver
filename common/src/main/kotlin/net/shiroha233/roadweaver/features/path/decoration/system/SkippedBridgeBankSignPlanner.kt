package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.WorldGenLevel
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration
import net.shiroha233.roadweaver.features.path.decoration.types.SeaQuestionSignDecoration
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 当桥梁因为“超长”被跳过时，在水域跨度两端（岸边）放置提示路牌。
 * 这里只负责“是否需要放 + 计算侧向偏移 + 投放 Decoration”，不直接写方块。
 */
object SkippedBridgeBankSignPlanner {
    private const val SIDE_OFFSET = 2

    @JvmStatic
    fun addIfSkippedBridgeBank(
        world: WorldGenLevel,
        out: MutableSet<Decoration>,
        placePos: BlockPos,
        nextPos: BlockPos,
        prevPos: BlockPos,
        roadWidth: Int,
        skipSegments: BooleanArray?,
        i: Int
    ) {
        if (skipSegments == null || i !in skipSegments.indices) return
        if (skipSegments[i]) return

        val beforeSkip = (i + 1 < skipSegments.size) && skipSegments[i + 1]
        val afterSkip = (i - 1 >= 0) && skipSegments[i - 1]
        if (!beforeSkip && !afterSkip) return

        val dx = nextPos.x - prevPos.x
        val dz = nextPos.z - prevPos.z
        val len = sqrt(dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble())
        val nx = if (len != 0.0) round(dx / len).toInt() else 0
        val nz = if (len != 0.0) round(dz / len).toInt() else 0
        val dir = Vec3i(nx, 0, nz)
        val ortho = Vec3i(-dir.z, 0, dir.x)

        val halfWidth = max(1, roadWidth / 2)
        val sideOffset = max(SIDE_OFFSET, halfWidth + 1)

        // 复用原有 start/end 的逻辑：
        // - afterSkip 代表“跨海后重新开始铺路”的一侧，当作 start
        // - beforeSkip 代表“跨海前道路结束”的一侧，当作 end
        val isStart = afterSkip
        val shifted = if (isStart) {
            placePos.offset(ortho.x * sideOffset, 0, ortho.z * sideOffset)
        } else {
            placePos.offset(-ortho.x * sideOffset, 0, -ortho.z * sideOffset)
        }

        if (StructureAvoidanceService.shouldAvoid(world, shifted)) return
        out.add(SeaQuestionSignDecoration(shifted, ortho, world, isStart))
    }
}
