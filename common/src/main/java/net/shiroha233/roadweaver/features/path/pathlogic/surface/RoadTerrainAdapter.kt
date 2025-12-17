package net.shiroha233.roadweaver.features.path.pathlogic.surface

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.config.ModConfig
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object RoadTerrainAdapter {
    /**
     * @deprecated 使用 adaptWithInterpolation
     */
    @Deprecated("Use adaptWithInterpolation")
    @JvmStatic
    fun adapt(level: WorldGenLevel, middle: BlockPos, width: Int, targetY: Int, random: RandomSource, cfg: ModConfig) {
        val halfWidth = (width + 1) / 2
        val bankWidth = 3
        val scanRadius = halfWidth + bankWidth

        val cx = middle.x
        val cz = middle.z
        val cursor = BlockPos.MutableBlockPos()

        for (dx in -scanRadius..scanRadius) {
            for (dz in -scanRadius..scanRadius) {
                val distSq = dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble()
                val dist = sqrt(distSq)
                if (dist > scanRadius) continue

                val x = cx + dx
                val z = cz + dz

                val isRoadSurface = dist <= halfWidth
                var edgeDist = dist - halfWidth
                if (edgeDist < 0.0) edgeDist = 0.0
                if (edgeDist > bankWidth && !isRoadSurface) continue

                val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

                if (targetY - 1 <= surfaceY) continue

                val targetBelowRoad = (targetY - 1).toDouble()
                val desiredY = if (isRoadSurface) {
                    targetBelowRoad
                } else {
                    var t = if (bankWidth <= 0) 1.0 else edgeDist / bankWidth.toDouble()
                    if (t < 0.0) t = 0.0
                    if (t > 1.0) t = 1.0
                    val s = t * t * (3.0 - 2.0 * t)
                    targetBelowRoad * (1.0 - s) + surfaceY.toDouble() * s
                }.let { if (it >= targetY) targetY - 1.0 else it }

                val fillTopY = floor(desiredY).toInt()
                if (fillTopY <= surfaceY) continue

                cursor.set(x, surfaceY - 1, z)
                var topState: BlockState = level.getBlockState(cursor)
                if (topState.isAir || topState.fluidState.isSource) {
                    topState = Blocks.DIRT.defaultBlockState()
                }

                var innerFill = topState
                var surfaceFill = topState
                if (topState.`is`(Blocks.GRASS_BLOCK) || topState.`is`(Blocks.DIRT)) {
                    innerFill = Blocks.DIRT.defaultBlockState()
                    surfaceFill = Blocks.GRASS_BLOCK.defaultBlockState()
                }

                for (y in surfaceY..fillTopY) {
                    cursor.y = y
                    val cur = level.getBlockState(cursor)
                    if (!cur.canBeReplaced()) continue
                    if (y == fillTopY && !isRoadSurface) {
                        level.setBlock(cursor, surfaceFill, 2)
                    } else {
                        level.setBlock(cursor, innerFill, 2)
                    }
                }
            }
        }
    }

    /**
     * 对道路段进行地形适配（使用插值高度）。
     */
    @JvmStatic
    fun adaptWithInterpolation(
        level: WorldGenLevel,
        middle: BlockPos,
        segmentIndex: Int,
        centers: List<BlockPos>?,
        targetYArr: IntArray?,
        width: Int,
        random: RandomSource,
        cfg: ModConfig
    ) {
        if (targetYArr == null || centers.isNullOrEmpty()) {
            return
        }

        val halfWidth = (width + 1) / 2
        val bankWidth = 3
        val scanRadius = halfWidth + bankWidth

        val cx = middle.x
        val cz = middle.z
        val cursor = BlockPos.MutableBlockPos()

        for (dx in -scanRadius..scanRadius) {
            for (dz in -scanRadius..scanRadius) {
                val distSq = dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble()
                val dist = sqrt(distSq)
                if (dist > scanRadius) continue

                val x = cx + dx
                val z = cz + dz

                val isRoadSurface = dist <= halfWidth
                var edgeDist = dist - halfWidth
                if (edgeDist < 0.0) edgeDist = 0.0
                if (edgeDist > bankWidth && !isRoadSurface) continue

                val targetY = RoadHeightInterpolator.getInterpolatedY(x, z, centers, targetYArr)

                val sea = level.level.seaLevel
                val motion = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)
                val surfaceY = if (motion > sea + 2) motion else surface

                if (targetY - 1 <= surfaceY) continue

                val targetBelowRoad = (targetY - 1).toDouble()
                val desiredY = if (isRoadSurface) {
                    targetBelowRoad
                } else {
                    var t = if (bankWidth <= 0) 1.0 else edgeDist / bankWidth.toDouble()
                    if (t < 0.0) t = 0.0
                    if (t > 1.0) t = 1.0
                    val s = t * t * (3.0 - 2.0 * t)
                    targetBelowRoad * (1.0 - s) + surfaceY.toDouble() * s
                }.let { if (it >= targetY) targetY - 1.0 else it }

                val fillTopY = floor(desiredY).toInt()
                if (fillTopY <= surfaceY) continue

                cursor.set(x, surfaceY - 1, z)
                var topState: BlockState = level.getBlockState(cursor)
                if (topState.isAir || topState.fluidState.isSource) {
                    topState = Blocks.DIRT.defaultBlockState()
                }

                var innerFill = topState
                var surfaceFill = topState
                if (topState.`is`(Blocks.GRASS_BLOCK) || topState.`is`(Blocks.DIRT)) {
                    innerFill = Blocks.DIRT.defaultBlockState()
                    surfaceFill = Blocks.GRASS_BLOCK.defaultBlockState()
                }

                for (y in surfaceY..fillTopY) {
                    cursor.y = y
                    val cur = level.getBlockState(cursor)
                    if (!cur.canBeReplaced()) continue

                    if (y == fillTopY && !isRoadSurface) {
                        level.setBlock(cursor, surfaceFill, 2)
                    } else {
                        level.setBlock(cursor, innerFill, 2)
                    }
                }
            }
        }
    }
}
