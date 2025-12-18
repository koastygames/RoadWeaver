package net.shiroha233.roadweaver.features.path.bridge

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.decoration.system.AboveColumnClearer
import net.shiroha233.roadweaver.helpers.Records

object BridgeBuilder {
    private val DECK: BlockState = Blocks.STONE_BRICKS.defaultBlockState()
    private val PIER: BlockState = Blocks.STONE_BRICKS.defaultBlockState()

    @JvmStatic
    fun placeSegment(
        world: WorldGenLevel,
        seg: Records.RoadSegmentPlacement,
        middle: BlockPos,
        prev: BlockPos,
        next: BlockPos,
        roadWidth: Int,
        deckY: Int,
        segmentIndex: Int,
        random: RandomSource,
        cfg: ModConfig,
        placePier: Boolean,
        placeRail: Boolean
    ) {
        // 1) 直接使用段落自身的positions来放置桥面，确保与普通道路宽度完全一致
        //    同时对桥面上方进行清障处理，防止冰刺/地形挡住桥面
        for (widthPos in seg.positions) {
            val deckPos = BlockPos(widthPos.x, deckY, widthPos.z)
            world.setBlock(deckPos, DECK, 3)
            // 注意：AboveColumnClearer 约定传入的是“路面上方一格”，否则会把路面本身清掉
            AboveColumnClearer.clearAboveColumn(world, deckPos.above(), cfg)
        }

        // 2) 桥墩（按段间隔）
        if (placePier) {
            val interval = maxOf(3, cfg.bridgePierInterval())
            if (segmentIndex % interval == 0) {
                placePierUnder(world, middle.x, middle.z, deckY - 1, cfg.bridgePierMaxHeight(), cfg.bridgePierWidth())
            }
        }
    }

    private fun placePierUnder(world: WorldGenLevel, x: Int, z: Int, fromY: Int, maxHeight: Int, pierWidth: Int) {
        val minY = world.minBuildHeight
        val half = maxOf(0, pierWidth - 1)
        for (dx in -half..half) {
            for (dz in -half..half) {
                var y = fromY
                var h = 0
                while (y >= minY && h < maxHeight) {
                    val cur = BlockPos(x + dx, y, z + dz)
                    // 若当前方块可承重，则停止在其上方，不再继续向下
                    if (world.getBlockState(cur).isFaceSturdy(world, cur, Direction.UP)) {
                        break
                    }
                    world.setBlock(cur, PIER, 3)
                    y--
                    h++
                }
            }
        }
    }
}
