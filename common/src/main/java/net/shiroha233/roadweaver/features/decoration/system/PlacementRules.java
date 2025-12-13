package net.shiroha233.roadweaver.features.decoration.system;

import net.minecraft.world.level.block.Block;

/**
 * 道路放置规则检查器。
 * 现在允许在任何方块上放置道路（海草、海带等水下植物也可以）。
 */
public final class PlacementRules {
    private PlacementRules() {}

    /**
     * 检查是否允许在指定方块上放置道路。
     * 当前实现：始终返回 true，允许在任何方块上放置。
     */
    public static boolean placeAllowedCheck(Block block) {
        return true;
    }
}
