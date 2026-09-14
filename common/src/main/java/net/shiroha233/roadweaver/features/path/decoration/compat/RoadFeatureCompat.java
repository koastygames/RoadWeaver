package net.shiroha233.roadweaver.features.path.decoration.compat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Central road-placement compatibility rules.
 *
 * Keep this list focused on blocks that are unsafe to overwrite rather than
 * enumerating every mod. Custom terrain blocks remain eligible, which lets
 * RoadWeaver work with biome/worldgen mods without hard dependencies.
 */
public final class RoadFeatureCompat {
    private RoadFeatureCompat() {}

    private static final Set<Block> DONT_PLACE = Set.of(
            Blocks.TALL_SEAGRASS,
            Blocks.SEAGRASS,
            Blocks.KELP,
            Blocks.KELP_PLANT,
            Blocks.MANGROVE_ROOTS,
            Blocks.MUDDY_MANGROVE_ROOTS,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ICE,
            Blocks.PACKED_ICE,
            Blocks.BLUE_ICE,
            Blocks.SNOW,
            Blocks.SWEET_BERRY_BUSH,
            Blocks.SUGAR_CANE,
            Blocks.CACTUS,
            Blocks.VINE,
            Blocks.GLOW_LICHEN,
            Blocks.FIRE,
            Blocks.SOUL_FIRE
    );

    public static boolean dontPlaceHere(Block block) {
        return DONT_PLACE.contains(block);
    }
}
