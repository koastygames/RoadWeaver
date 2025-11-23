package net.shiroha233.roadweaver.features.roadlogic.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;

public final class RoadTerrainAdapter {
    private RoadTerrainAdapter() {}

    /**
     * 对道路段进行地形适配。
     *
     * 现在的策略刻意做得非常保守：
     * - 只在「路面明显高于原地形」时，向下方和两侧填土，做一个简单的路基（Embarkment）。
     * - 完全不做削坡(Cut)，避免把已有地形挖坏、露出大量泥土台阶。
     * - 路侧的路基高度用 smoothstep 从路面下一格平滑过渡到原始地表高度，形成一个“凸”字形托起。
     */
    public static void adapt(WorldGenLevel level, BlockPos middle, int width, int targetY, RandomSource random, ModConfig cfg) {
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
}
