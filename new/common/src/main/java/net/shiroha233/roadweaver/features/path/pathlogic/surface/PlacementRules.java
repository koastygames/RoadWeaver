package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.features.path.decoration.compat.RoadFeatureCompat;

/**
 * 方块放置规则：判断道路是否可以在指定方块上放置
 */
public final class PlacementRules {
    private PlacementRules() {}

    /**
     * 放置检查：道路可覆盖任意方块。
     * 仅保留兼容层检查（如其他模组明确禁止的区域）。
     */
    public static boolean placeAllowedCheck(Block block) {
        return !RoadFeatureCompat.dontPlaceHere(block);
    }
}
