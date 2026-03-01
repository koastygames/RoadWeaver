package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.world.level.block.Block;

/**
 * Highway 放置规则
 */
public final class HighwayPlacementRules {
    private HighwayPlacementRules() {}

    public static boolean placeAllowedCheck(Block block) {
        return !HighwayFeatureCompat.dontPlaceHere(block);
    }
}
