package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Highway 放置兼容层
 * 职责：定义不适合被道路覆盖的方块
 */
public final class HighwayFeatureCompat {
    private HighwayFeatureCompat() {}

    private static final Set<Block> DONT_PLACE = new HashSet<>();

    static {
        DONT_PLACE.add(Blocks.TALL_SEAGRASS);
        DONT_PLACE.add(Blocks.MANGROVE_ROOTS);
    }

    public static boolean dontPlaceHere(Block b) {
        return DONT_PLACE.contains(b);
    }
}
