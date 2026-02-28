package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.List;

/**
 * Highway 路段铺设器
 * 职责：铺设混凝土路面和中央白色虚线
 */
public final class HighwaySegmentPaver {
    private HighwaySegmentPaver() {}

    private static final List<BlockState> BASE = List.of(Blocks.GRAY_CONCRETE.defaultBlockState());
    private static final List<BlockState> LINE = List.of(Blocks.WHITE_CONCRETE.defaultBlockState());

    public static void paveSegment(WorldGenLevel world,
                                   RoadSegmentPlacement seg,
                                   int segmentIndex,
                                   List<BlockPos> centers,
                                   int[] targetY,
                                   RandomSource random,
                                   ModConfig cfg) {
        List<BlockPos> positions = seg.positions();
        if (positions == null || positions.isEmpty()) return;

        int[] heights = HighwayHeightInterpolator.batchInterpolate(positions, segmentIndex, centers, targetY);

        BlockPos center = seg.middlePos();
        Vec3i ortho = computeOrthoForSegment(segmentIndex, centers);

        boolean dashOn = isDashOn(segmentIndex);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos widthBlock = positions.get(i);
            int y = heights[i];
            BlockPos surfacePos = new BlockPos(widthBlock.getX(), y, widthBlock.getZ());

            if (HighwayStructureAvoidanceService.shouldAvoid(world, surfacePos)) {
                continue;
            }

            int dx = widthBlock.getX() - center.getX();
            int dz = widthBlock.getZ() - center.getZ();
            int lateral = dx * ortho.getX() + dz * ortho.getZ();

            boolean isCenterLine = (lateral == 0);
            List<BlockState> mats = (isCenterLine && dashOn) ? LINE : BASE;

            BlockPos belowTop = surfacePos.below();
            HighwaySurfacePlacementUtil.placeRoadBlock(world, world.getBlockState(belowTop), surfacePos, mats, random, cfg);
        }
    }

    private static boolean isDashOn(int segmentIndex) {
        return ((segmentIndex / 4) % 2) == 0;
    }

    private static Vec3i computeOrthoForSegment(int segmentIndex, List<BlockPos> centers) {
        if (centers == null || centers.size() < 3) {
            return new Vec3i(1, 0, 0);
        }
        int idx = Math.max(1, Math.min(segmentIndex, centers.size() - 2));
        BlockPos prev = centers.get(idx - 1);
        BlockPos next = centers.get(idx + 1);

        int dx = next.getX() - prev.getX();
        int dz = next.getZ() - prev.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        int nx = len != 0 ? (int) Math.round(dx / len) : 1;
        int nz = len != 0 ? (int) Math.round(dz / len) : 0;

        Vec3i dir = new Vec3i(nx, 0, nz);
        return new Vec3i(-dir.getZ(), 0, dir.getX());
    }
}
