package net.shiroha233.roadweaver.features.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

/**
 * 路面上方清障器。
 * 清除路面上方的植被和障碍物，但保留木头和栅栏（防止破坏玩家建筑）。
 * 注：树木通过 Mixin 在生成阶段就被阻止，此处仅处理残留植被。
 */
public final class AboveColumnClearer {
    private AboveColumnClearer() {}

    public static void clearAboveColumn(WorldGenLevel world, BlockPos surfacePos, ModConfig cfg) {
        boolean tunnel = cfg != null && cfg.tunnelEnabled();
        int defaultClear = (cfg != null ? cfg.roadClearHeight() : 3);
        int maxH = tunnel
                ? Math.max(2, Math.min(16, cfg.tunnelClearHeight()))
                : Math.max(1, Math.min(16, defaultClear));

        for (int i = 0; i < maxH; i++) {
            BlockPos up = surfacePos.above(i);
            BlockState st = world.getBlockState(up);
            if (st.isAir()) continue;

            // 保留木头和栅栏（防止破坏玩家建筑）
            boolean isLog = st.is(BlockTags.LOGS);
            boolean isFence = st.is(BlockTags.FENCES);
            if (isLog || isFence) {
                break;
            }

            // 清除其他方块（草、花、藤蔓、雪等）
            world.setBlock(up, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
