package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

public final class RoadTerrainAdapter {
    private RoadTerrainAdapter() {}

    /**
     * 对道路段进行地形适配（使用固定高度）。
     *
     * 策略与旧实现一致，仅在“路面明显高于原地形”时填充路基。
     */
    public static void adaptWithoutInterpolation(WorldGenLevel level, BlockPos middle, int width, int targetY, RandomSource random, ModConfig cfg) {
        int halfWidth = (width + 1) / 2;
        int bankWidth = 3; // 路基向外延伸 3 格
        int scanRadius = halfWidth + bankWidth;

        int cx = middle.getX();
        int cz = middle.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                double distSq = (double) dx * dx + (double) dz * dz;
                double dist = Math.sqrt(distSq);
                if (dist > scanRadius) continue;

                int x = cx + dx;
                int z = cz + dz;

                boolean isRoadSurface = dist <= halfWidth;
                double edgeDist = dist - halfWidth;
                if (edgeDist < 0.0) edgeDist = 0.0;
                if (edgeDist > bankWidth && !isRoadSurface) continue;

                // 当前原始地表高度
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                // 只在“路高地低”的情况下做路基，避免把山坡挖成沟
                if (targetY - 1 <= surfaceY) {
                    continue;
                }

                // 计算这一列应该填到多高
                double targetBelowRoad = targetY - 1; // 路面正下方一格
                double desiredY;

                if (isRoadSurface) {
                    // 路面下方直接填到路面下一格
                    desiredY = targetBelowRoad;
                } else {
                    // 距离路边的归一化参数 t: 0 在路边, 1 在路基外缘
                    double t = bankWidth <= 0 ? 1.0 : edgeDist / (double) bankWidth;
                    if (t < 0.0) t = 0.0;
                    if (t > 1.0) t = 1.0;

                    // smoothstep 让曲线更圆润
                    double s = t * t * (3.0 - 2.0 * t);

                    // 从「路面下一格」平滑过渡到原始地表高度
                    desiredY = targetBelowRoad * (1.0 - s) + surfaceY * s;
                }

                // 不允许路基高于路面本身
                if (desiredY >= targetY) {
                    desiredY = targetY - 1;
                }

                int fillTopY = (int) Math.floor(desiredY);
                if (fillTopY <= surfaceY) {
                    // 已经够高，不需要填
                    continue;
                }

                // 选取上方表层和内部填充材质
                cursor.set(x, surfaceY - 1, z);
                BlockState topState = level.getBlockState(cursor);
                if (topState.isAir() || topState.getFluidState().isSource()) {
                    topState = Blocks.DIRT.defaultBlockState();
                }

                BlockState innerFill = topState;
                BlockState surfaceFill = topState;
                // 草方块或泥土都视为草地表层：内部用泥土，最上面铺草
                if (topState.is(Blocks.GRASS_BLOCK) || topState.is(Blocks.DIRT)) {
                    innerFill = Blocks.DIRT.defaultBlockState();
                    surfaceFill = Blocks.GRASS_BLOCK.defaultBlockState();
                }

                // 从原始地表向上填到 fillTopY
                for (int y = surfaceY; y <= fillTopY; y++) {
                    cursor.setY(y);
                    BlockState cur = level.getBlockState(cursor);
                    if (!cur.canBeReplaced()) continue;

                    if (y == fillTopY && !isRoadSurface) {
                        // 路基表面尽量保持草皮
                        level.setBlock(cursor, surfaceFill, 2);
                    } else {
                        level.setBlock(cursor, innerFill, 2);
                    }
                }
            }
        }
    }

    /**
     * 对道路段进行地形适配（使用插值高度）。
     * 
     * 与 SegmentPaver 使用相同的插值逻辑，确保地形适配和路面铺设的高度一致。
     * 
     * @param level 世界
     * @param middle 当前路段中心点
     * @param segmentIndex 当前路段索引
     * @param centers 完整的道路中心点列表
     * @param targetYArr 目标高度数组
     * @param width 道路宽度
     * @param random 随机源
     * @param cfg 配置
     */
    public static void adaptWithInterpolation(WorldGenLevel level, 
                                              BlockPos middle, 
                                              int segmentIndex,
                                              List<BlockPos> centers, 
                                              int[] targetYArr,
                                              int width, 
                                              RandomSource random, 
                                              ModConfig cfg) {
        if (targetYArr == null || centers == null || centers.isEmpty()) {
            return;
        }
        
        int halfWidth = (width + 1) / 2;
        int bankWidth = 3; // 路基向外延伸 3 格
        int scanRadius = halfWidth + bankWidth;

        int cx = middle.getX();
        int cz = middle.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                double distSq = (double) dx * dx + (double) dz * dz;
                double dist = Math.sqrt(distSq);
                if (dist > scanRadius) continue;

                int x = cx + dx;
                int z = cz + dz;

                boolean isRoadSurface = dist <= halfWidth;
                double edgeDist = dist - halfWidth;
                if (edgeDist < 0.0) edgeDist = 0.0;
                if (edgeDist > bankWidth && !isRoadSurface) continue;

                // 使用插值计算该位置的目标高度（与 SegmentPaver 一致）
                int targetY = RoadHeightInterpolator.getInterpolatedY(x, z, centers, targetYArr);

                // 当前原始地表高度
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                // 只在"路高地低"的情况下做路基，避免把山坡挖成沟
                if (targetY - 1 <= surfaceY) {
                    continue;
                }

                // 计算这一列应该填到多高
                double targetBelowRoad = targetY - 1; // 路面正下方一格
                double desiredY;

                if (isRoadSurface) {
                    // 路面下方直接填到路面下一格
                    desiredY = targetBelowRoad;
                } else {
                    // 距离路边的归一化参数 t: 0 在路边, 1 在路基外缘
                    double t = bankWidth <= 0 ? 1.0 : edgeDist / (double) bankWidth;
                    if (t < 0.0) t = 0.0;
                    if (t > 1.0) t = 1.0;

                    // smoothstep 让曲线更圆润
                    double s = t * t * (3.0 - 2.0 * t);

                    // 从「路面下一格」平滑过渡到原始地表高度
                    desiredY = targetBelowRoad * (1.0 - s) + surfaceY * s;
                }

                // 不允许路基高于路面本身
                if (desiredY >= targetY) {
                    desiredY = targetY - 1;
                }

                int fillTopY = (int) Math.floor(desiredY);
                if (fillTopY <= surfaceY) {
                    // 已经够高，不需要填
                    continue;
                }

                // 选取上方表层和内部填充材质
                cursor.set(x, surfaceY - 1, z);
                BlockState topState = level.getBlockState(cursor);
                if (topState.isAir() || topState.getFluidState().isSource()) {
                    topState = Blocks.DIRT.defaultBlockState();
                }

                BlockState innerFill = topState;
                BlockState surfaceFill = topState;
                // 草方块或泥土都视为草地表层：内部用泥土，最上面铺草
                if (topState.is(Blocks.GRASS_BLOCK) || topState.is(Blocks.DIRT)) {
                    innerFill = Blocks.DIRT.defaultBlockState();
                    surfaceFill = Blocks.GRASS_BLOCK.defaultBlockState();
                }

                // 从原始地表向上填到 fillTopY
                for (int y = surfaceY; y <= fillTopY; y++) {
                    cursor.setY(y);
                    BlockState cur = level.getBlockState(cursor);
                    if (!cur.canBeReplaced()) continue;

                    if (y == fillTopY && !isRoadSurface) {
                        // 路基表面尽量保持草皮
                        level.setBlock(cursor, surfaceFill, 2);
                    } else {
                        level.setBlock(cursor, innerFill, 2);
                    }
                }
            }
        }
    }
}
