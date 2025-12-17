package net.shiroha233.roadweaver.features.path.bridge

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.decoration.system.AboveColumnClearer

/**
 * 浮标放置器。
 *
 * 职责：在水域跨度内放置一个简单的浮标标记（木板 + 栅栏 + 火把）。
 * 该类只负责“放置方块”，不负责“决定哪里放、间隔是多少”。
 */
object BuoyBuilder {
    private val BASE: BlockState = Blocks.OAK_PLANKS.defaultBlockState()
    private val POST: BlockState = Blocks.OAK_FENCE.defaultBlockState()
    private val LIGHT: BlockState = Blocks.TORCH.defaultBlockState()

    @JvmStatic
    fun placeBuoy(world: WorldGenLevel?, center: BlockPos?, seaLevel: Int, random: RandomSource, cfg: ModConfig) {
        if (world == null || center == null) return

        // 海平面高度通常就是水面；直接替换水方块即可形成“漂浮”的浮标。
        val basePos = BlockPos(center.x, seaLevel, center.z)
        val postPos = basePos.above()
        val lightPos = basePos.above(2)

        world.setBlock(basePos, BASE, 3)
        world.setBlock(postPos, POST, 3)
        world.setBlock(lightPos, LIGHT, 3)

        // 清理火把上方的遮挡，避免树叶/冰刺影响可见性
        AboveColumnClearer.clearAboveColumn(world, lightPos.above(), cfg)
    }
}
