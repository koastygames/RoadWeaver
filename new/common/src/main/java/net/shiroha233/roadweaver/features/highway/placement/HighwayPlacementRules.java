package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.world.level.block.Block;

/**
 * Highway 放置规则
 * 职责：判断方块是否允许被道路覆盖
 */
public final class HighwayPlacementRules {
    private HighwayPlacementRules() {}

    public static boolean placeAllowedCheck(Block block) {
        return !HighwayFeatureCompat.dontPlaceHere(block);
    }
}
