package net.shiroha233.roadweaver.features.path.decoration.system;

import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.features.path.decoration.compat.RoadFeatureCompat;

/**
 * 放置规则：定义道路方块的放置条件。
 */
public final class PlacementRules {
    private PlacementRules() {}

    public static boolean placeAllowedCheck(Block block) {
        return !RoadFeatureCompat.dontPlaceHere(block);
    }
}
