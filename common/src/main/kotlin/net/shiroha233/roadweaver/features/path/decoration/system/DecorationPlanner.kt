package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration
import net.shiroha233.roadweaver.features.path.decoration.types.DistanceSignDecoration
import net.shiroha233.roadweaver.features.path.decoration.types.LamppostDecoration
import net.shiroha233.roadweaver.features.path.decoration.types.LanternPostDecoration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

object DecorationPlanner {
    enum class Mode { ARTIFICIAL, NATURAL }

    private const val SIDE_OFFSET = 2

    @JvmStatic
    fun placeOnSurface(
        world: WorldGenLevel,
        placePos: BlockPos,
        material: List<BlockState>,
        random: RandomSource,
        cfg: ModConfig,
        mode: Mode
    ) {
        val roadType = if (mode == Mode.ARTIFICIAL) 0 else 1
        SurfacePlacementUtil.placeOnSurface(world, placePos, material, roadType, random, cfg)
    }

    @JvmStatic
    fun addDecoration(
        world: WorldGenLevel,
        out: MutableSet<Decoration>,
        placePos: BlockPos,
        segmentIndex: Int,
        nextPos: BlockPos,
        prevPos: BlockPos,
        middlePositions: List<BlockPos>,
        roadWidth: Int,
        random: RandomSource,
        cfg: ModConfig,
        mode: Mode
    ) {
        val dx = nextPos.x - prevPos.x
        val dz = nextPos.z - prevPos.z
        val len = sqrt(dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble())
        val nx = if (len != 0.0) round(dx / len).toInt() else 0
        val nz = if (len != 0.0) round(dz / len).toInt() else 0
        val dir = Vec3i(nx, 0, nz)
        val ortho = Vec3i(-dir.z, 0, dir.x)
        val halfWidth = max(1, roadWidth / 2)
        val sideOffset = max(SIDE_OFFSET, halfWidth + 1)

        // 只在离路口最近的可处理路段放置路牌
        // 起点牌：segmentIndex == 8（第一个被处理的路段）
        // 终点牌：segmentIndex == middlePositions.size() - 10（倒数第 8 段附近）
        val isStartSign = segmentIndex == 8
        val isEndSign = segmentIndex == middlePositions.size - 10

        if (cfg.roadSignsEnabled() && (isStartSign || isEndSign)) {
            val isStart = isStartSign
            val shifted = if (isStart) {
                placePos.offset(ortho.x * sideOffset, 0, ortho.z * sideOffset)
            } else {
                placePos.offset(-ortho.x * sideOffset, 0, -ortho.z * sideOffset)
            }
            val dist = computeApproxDistanceMeters(world, shifted, isStart, middlePositions)
            out.add(DistanceSignDecoration(shifted, ortho, world, isStart, dist.toString()))
            return
        }

        val interval = max(1, cfg.lampInterval())
        if (segmentIndex % interval == 0) {
            val left = random.nextBoolean()
            var shifted = if (left) {
                placePos.offset(ortho.x * sideOffset, 0, ortho.z * sideOffset)
            } else {
                placePos.offset(-ortho.x * sideOffset, 0, -ortho.z * sideOffset)
            }
            shifted = BlockPos(shifted.x, world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, shifted.x, shifted.z), shifted.z)
            if (abs(shifted.y - placePos.y) > 1) return
            if (mode == Mode.ARTIFICIAL) {
                out.add(LamppostDecoration(shifted, ortho, world))
            } else {
                out.add(LanternPostDecoration(shifted, ortho, world))
            }
        }
    }

    private fun computeApproxDistanceMeters(world: WorldGenLevel, fromPos: BlockPos, isStart: Boolean, middlePositions: List<BlockPos>): Int {
        val target = if (isStart) middlePositions[middlePositions.size - 1] else middlePositions[0]
        val dx = (target.x.toLong() - fromPos.x.toLong())
        val dz = (target.z.toLong() - fromPos.z.toLong())
        val d = sqrt(dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble())
        return round(d).toInt()
    }
}
