package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.world.level.block.Block;

/**
 * Highway 放置规则。
 */
public final class HighwayPlacementRules {
    private HighwayPlacementRules() {}

    /**
     * 放置检查：道路可覆盖任意方块。
     * 仅保留兼容层检查（如明确禁止的区域）。
     */
    public static boolean placeAllowedCheck(Block block) {
        return !HighwayFeatureCompat.dontPlaceHere(block);
    }
}
