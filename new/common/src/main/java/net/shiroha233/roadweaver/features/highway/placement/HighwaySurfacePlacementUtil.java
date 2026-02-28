package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * Highway 路面放置工具
 * 职责：提供统一的路面放置和清障接口
 */
public final class HighwaySurfacePlacementUtil {
    private HighwaySurfacePlacementUtil() {}

    public static void placeRoadBlock(WorldGenLevel world,
                                      BlockState blockBelow,
                                      BlockPos surfacePos,
                                      List<BlockState> materials,
                                      RandomSource random,
                                      ModConfig cfg) {
        HighwayRoadBlockPlacer.placeRoadBlock(world, blockBelow, surfacePos, materials, random, cfg);
    }

    public static void clearAboveColumn(WorldGenLevel world, BlockPos surfacePos, ModConfig cfg) {
        HighwayAboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);
    }
}
