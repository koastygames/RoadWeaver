package net.shiroha233.roadweaver.features.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.decoration.system.AboveColumnClearer;

/**
 * 浮标放置器。
 *
 * 职责：在水域跨度内放置一个简单的浮标标记（木板 + 栅栏 + 火把）。
 * 该类只负责“放置方块”，不负责“决定哪里放、间隔是多少”。
 */
public final class BuoyBuilder {
    private BuoyBuilder() {}

    private static final BlockState BASE = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState POST = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.TORCH.defaultBlockState();

    public static void placeBuoy(WorldGenLevel world,
                                BlockPos center,
                                int seaLevel,
                                RandomSource random,
                                ModConfig cfg) {
        if (world == null || center == null) return;

        // 海平面高度通常就是水面；直接替换水方块即可形成“漂浮”的浮标。
        BlockPos basePos = new BlockPos(center.getX(), seaLevel, center.getZ());
        BlockPos postPos = basePos.above();
        BlockPos lightPos = basePos.above(2);

        world.setBlock(basePos, BASE, 3);
        world.setBlock(postPos, POST, 3);
        world.setBlock(lightPos, LIGHT, 3);

        // 清理火把上方的遮挡，避免树叶/冰刺影响可见性
        AboveColumnClearer.clearAboveColumn(world, lightPos.above(), cfg);
    }
}
