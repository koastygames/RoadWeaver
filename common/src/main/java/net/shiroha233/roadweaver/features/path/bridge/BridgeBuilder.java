package net.shiroha233.roadweaver.features.path.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.decoration.system.AboveColumnClearer;
import net.shiroha233.roadweaver.helpers.Records;

/**
 * 桥梁构建器
 * 负责放置桥面和桥墩
 */
public final class BridgeBuilder {
    private BridgeBuilder() {}

    private static final BlockState DECK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState PIER = Blocks.STONE_BRICKS.defaultBlockState();

    /**
     * 放置单个桥梁段
     */
    public static void placeSegment(WorldGenLevel world,
                                    Records.RoadSegmentPlacement seg,
                                    BlockPos middle,
                                    BlockPos prev,
                                    BlockPos next,
                                    int roadWidth,
                                    int deckY,
                                    int segmentIndex,
                                    RandomSource random,
                                    ModConfig cfg,
                                    boolean placePier,
                                    boolean placeRail) {
        
        // 使用段落自身的 positions 放置桥面，确保与普通道路宽度一致
        for (BlockPos widthPos : seg.positions()) {
            BlockPos deckPos = new BlockPos(widthPos.getX(), deckY, widthPos.getZ());
            world.setBlock(deckPos, DECK, 3);
            // 清理桥面上方遮挡
            AboveColumnClearer.clearAboveColumn(world, deckPos.above(), cfg);
        }

        // 桥墩（按段间隔）
        if (placePier) {
            int interval = Math.max(3, cfg.bridgePierInterval());
            if (segmentIndex % interval == 0) {
                placePierUnder(world, middle.getX(), middle.getZ(), deckY - 1, 
                        cfg.bridgePierMaxHeight(), cfg.bridgePierWidth());
            }
        }
    }

    /** 放置桥墩 */
    private static void placePierUnder(WorldGenLevel world, int x, int z, int fromY, 
                                       int maxHeight, int pierWidth) {
        int minY = world.getMinBuildHeight();
        int half = Math.max(0, pierWidth - 1);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int y = fromY;
                int h = 0;
                while (y >= minY && h < maxHeight) {
                    BlockPos cur = new BlockPos(x + dx, y, z + dz);
                    // 遇到可承重方块停止
                    if (world.getBlockState(cur).isFaceSturdy(world, cur, Direction.UP)) {
                        break;
                    }
                    world.setBlock(cur, PIER, 3);
                    y--;
                    h++;
                }
            }
        }
    }
}
