package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig
import kotlin.math.max
import kotlin.math.min

/**
 * 路面上方清障器。
 * 清除路面上方的所有障碍物，只保留宝箱（防止破坏战利品容器）。
 * 注：树木通过 Mixin 在生成阶段就被阻止，此处处理残留的树木、植被等。
 */
object AboveColumnClearer {
    @JvmStatic
    fun clearAboveColumn(world: WorldGenLevel, surfacePos: BlockPos, cfg: ModConfig?) {
        val tunnel = cfg != null && cfg.tunnelEnabled()
        val defaultClear = (cfg?.roadClearHeight() ?: 3)
        val maxH = if (tunnel) {
            max(2, min(16, cfg!!.tunnelClearHeight()))
        } else {
            max(1, min(16, defaultClear))
        }

        for (i in 0 until maxH) {
            val up = surfacePos.above(i)
            val st = world.getBlockState(up)
            if (st.isAir) continue

            // 只保留宝箱（防止破坏战利品容器）
            if (st.block is ChestBlock) continue

            // 清除所有其他方块（树木、植被、栅栏等）
            world.setBlock(up, Blocks.AIR.defaultBlockState(), 3)
        }

        // 生成阶段 setBlock 不一定会触发完整的邻居更新，可能导致上方/附着方块残留成“浮空”。
        // 这里额外向上检查一小段高度，把已经不满足存活条件的装饰类方块清掉。
        val extraCheck = 6
        for (i in maxH until (maxH + extraCheck)) {
            val up = surfacePos.above(i)
            val st = world.getBlockState(up)
            if (st.isAir) continue
            if (st.block is ChestBlock) continue
            if (!st.canSurvive(world, up)) {
                world.setBlock(up, Blocks.AIR.defaultBlockState(), 3)
            }
        }

        // 进一步：清理周围 1 格半径内的附着/装饰方块（例如藤蔓、墙上火把、告示牌等）
        // 它们可能在相邻列上，单纯清理当前柱体不足以覆盖。
        val r = 1
        val cursor = BlockPos.MutableBlockPos()
        for (dx in -r..r) {
            for (dz in -r..r) {
                for (dy in 0 until (maxH + extraCheck)) {
                    cursor.set(surfacePos.x + dx, surfacePos.y + dy, surfacePos.z + dz)
                    val st: BlockState = world.getBlockState(cursor)
                    if (st.isAir) continue
                    if (st.block is ChestBlock) continue
                    if (!st.canSurvive(world, cursor)) {
                        world.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3)
                    }
                }
            }
        }
    }
}
