package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig
import kotlin.math.max
import kotlin.math.min

object RoadBlockPlacer {
    @JvmStatic
    fun placeRoadBlock(
        world: WorldGenLevel,
        blockBelow: BlockState,
        surfacePos: BlockPos,
        materials: List<BlockState>,
        random: RandomSource,
        cfg: ModConfig?
    ) {
        if (!PlacementRules.placeAllowedCheck(blockBelow.block)) return
        val chosen = materials[random.nextInt(materials.size)]

        val MAX_CAUSEWAY_DEPTH = max(0, min(12, cfg?.causewayMaxDepth() ?: 1))
        val below1 = surfacePos.below()
        val below2 = surfacePos.below(2)
        val sturdy1 = world.getBlockState(below1).isFaceSturdy(world, below1, Direction.UP)
        val sturdy2 = world.getBlockState(below2).isFaceSturdy(world, below2, Direction.UP)

        if (!sturdy1 && !sturdy2) {
            var cursor = below2
            var depth = 0
            var base: BlockPos? = null
            while (cursor.y > world.minBuildHeight && depth < MAX_CAUSEWAY_DEPTH) {
                if (world.getBlockState(cursor).isFaceSturdy(world, cursor, Direction.UP)) {
                    base = cursor
                    break
                }
                cursor = cursor.below()
                depth++
            }

            var fillStart = base?.above()
                ?: below1.below(min(MAX_CAUSEWAY_DEPTH - 1, max(0, below1.y - world.minBuildHeight)))
            if (fillStart.y < world.minBuildHeight) {
                fillStart = BlockPos(fillStart.x, world.minBuildHeight, fillStart.z)
            }

            var pos = fillStart
            while (pos.y <= below1.y) {
                world.setBlock(pos, chosen, 3)
                pos = pos.above()
            }
        } else {
            world.setBlock(below1, chosen, 3)
        }

        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg)

        val belowPos1 = surfacePos.below(2)
        val belowState1 = world.getBlockState(belowPos1)
        if (belowState1.`is`(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), 3)
        }
    }
}
