package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.util.Line;

/**
 * 模板桥梁规划器
 * 使用结构模板处理长距离桥梁段生成
 */
public final class BridgeSegmentPlannerNew {
    private BridgeSegmentPlannerNew() {}

    /**
     * 使用结构模板处理长距离桥梁段生成
     * 
     * @return true 如果成功生成模板桥梁，false 如果模板不可用（调用方应回退到简单桥梁生成）
     */
    public static boolean processSegment(
            WorldGenLevel world,
            Line line,
            Records.RoadSegmentPlacement seg,
            BlockPos middle,
            BlockPos prev,
            int baseY) {
        double bridgeLength = line.getTotalLength();

        // 随机选择一个桥模板
        var bridge = BridgeTemplateStructureRegistry.choose(world.getLevel(), (int) bridgeLength);

        // 模板不可用时返回 false，由调用方回退到 BridgeSegmentPlanner
        if (bridge == null) {
            return false;
        }

        int minX = Math.min(prev.getX(), middle.getX()) - 10;
        int maxX = Math.max(prev.getX(), middle.getX()) + 10;
        int minZ = Math.min(prev.getZ(), middle.getZ()) - 10;
        int maxZ = Math.max(prev.getZ(), middle.getZ()) + 10;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // 获取测试点
                Vec3 testPos = new Vec3(x, baseY, z);
                // 获取当前 xz 位置上的标架
                Line.Frame frame = line.getFrame(testPos);

                Vec3 vec0 = testPos.subtract(frame.closestPoint);
                double testX = vec0.dot(frame.tangent0);
                double testZ = vec0.dot(frame.binormal0);

                // 检查方块是否在桥模板范围内
                if (Math.abs(testX) > 2 || !bridge.isInVoxelGrid(0, 0, testZ)) {
                    continue;
                }

                // 放置此竖列的方块
                for (int y = baseY + 20; y >= baseY - 60; y--) {
                    Vec3 vec = new Vec3(x, y, z).subtract(frame.closestPoint);

                    // 在标架下的坐标
                    double localX = Math.min(Math.max(0, frame.globalT), 1) * bridgeLength;
                    double localY = y - baseY;
                    double localZ = vec.dot(frame.binormal0);

                    // 判断放置桥面还是桥头桥尾部分
                    if (localX >= bridge.getStartLength() && localX <= bridgeLength - bridge.getEndLength()) {
                        localX = bridge.getStartLength() + (localX % bridge.getDeckLength());
                    } else if (localX > bridgeLength - bridge.getEndLength()) {
                        localX = bridge.getTotalLength() - (bridgeLength - localX);
                    }

                    // 获取对应方块并放置
                    BlockState state = bridge.getBlock(localX, localY, localZ);
                    if (state != null && !state.is(Blocks.AIR)) {
                        BlockPos pos = new BlockPos(x, y, z);
                        world.setBlock(pos, state, 3);
                        BlockState updatedState = Block.updateFromNeighbourShapes(
                                world.getBlockState(pos), world, pos);
                        world.setBlock(pos, updatedState, 3);
                    }

                    // 填充地基直到遇到支撑方块
                    BlockPos cur = new BlockPos(x, y - 1, z);
                    if (world.getBlockState(cur).isFaceSturdy(world, cur, Direction.UP)) {
                        break;
                    }
                }
            }
        }

        return true;
    }
}
