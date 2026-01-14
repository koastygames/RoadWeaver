package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.util.Line;

/**
 * 模板桥梁规划器（统一版）
 * 使用结构模板处理所有桥梁段生成，通过跨区块缓存确保一致性
 */
public final class BridgeSegmentPlannerNew {
    private BridgeSegmentPlannerNew() {}

    /**
     * 处理桥梁段生成（使用缓存的上下文确保跨区块一致性）
     * 
     * @param world 世界
     * @param server 服务端世界
     * @param currentChunk 当前区块
     * @param bridgeId 桥梁唯一ID（基于道路指纹+桥梁区间索引）
     * @param line 桥梁曲线
     * @param deckY 桥面高度
     * @return true 如果成功生成，false 如果模板不可用
     */
    public static boolean processWithContext(
            WorldGenLevel world,
            ServerLevel server,
            ChunkPos currentChunk,
            long bridgeId,
            Line line,
            int deckY) {
        
        double bridgeLength = line.getTotalLength();
        
        // 获取或创建桥梁上下文（确保跨区块一致性）
        BridgeContextCache.BridgeContext ctx = BridgeContextCache.getOrCreate(
                server, bridgeId, bridgeLength, deckY);
        
        if (ctx.templateId() == null) {
            return false;
        }
        
        // 使用缓存的模板ID获取模板
        var bridge = BridgeTemplateStructureRegistry.getById(server, ctx.templateId());
        if (bridge == null) {
            return false;
        }
        
        return placeTemplateInChunk(world, currentChunk, bridge, line, deckY, bridgeLength);
    }

    /**
     * 在当前区块内放置模板桥梁方块
     */
    private static boolean placeTemplateInChunk(
            WorldGenLevel world,
            ChunkPos currentChunk,
            BridgeTemplateStructureRegistry.BridgeTemplate bridge,
            Line line,
            int baseY,
            double bridgeLength) {
        
        int minX = currentChunk.getMinBlockX();
        int maxX = currentChunk.getMaxBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxZ = currentChunk.getMaxBlockZ();
        
        // 获取模板尺寸
        int templateStartLen = bridge.getStartLength();
        int templateEndLen = bridge.getEndLength();
        int templateTotalLen = bridge.getTotalLength();
        
        // 检查桥梁是否过短（小于模板最小长度）
        boolean isTooShort = bridgeLength < templateStartLen + templateEndLen;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if ((x >> 4) != currentChunk.x || (z >> 4) != currentChunk.z) {
                    continue;
                }
                
                Vec3 testPos = new Vec3(x, baseY, z);
                Line.Frame frame = line.getFrame(testPos);

                Vec3 vec0 = testPos.subtract(frame.closestPoint);
                double testZ = vec0.dot(frame.binormal0);

                // 检查方块是否在桥模板宽度范围内
                if (!bridge.isInVoxelGrid(0, 0, testZ)) {
                    continue;
                }

                // 放置此竖列的方块
                for (int y = baseY + 20; y >= baseY - 60; y--) {
                    Vec3 vec = new Vec3(x, y, z).subtract(frame.closestPoint);

                    double globalX = Math.min(Math.max(0, frame.globalT), 1) * bridgeLength;

                    double localX;
                    double localY = y - baseY;
                    double localZ = vec.dot(frame.binormal0);

                    if (isTooShort) {
                        // 短桥梁：线性缩放到模板长度
                        localX = (globalX / bridgeLength) * templateTotalLen;
                    } else {
                        // 正常桥梁：桥头 + 重复桥面 + 桥尾
                        localX = globalX;
                        double deckStart = templateStartLen;
                        double deckEnd = bridgeLength - templateEndLen;
                        
                        if (globalX < deckStart) {
                            // 桥头部分：直接使用
                            localX = globalX;
                        } else if (globalX > deckEnd) {
                            // 桥尾部分：映射到模板尾部
                            localX = templateTotalLen - (bridgeLength - globalX);
                        } else {
                            // 桥面部分：循环重复
                            int deckLen = bridge.getDeckLength();
                            if (deckLen > 0) {
                                double deckOffset = globalX - deckStart;
                                localX = deckStart + (deckOffset % deckLen);
                            } else {
                                localX = deckStart;
                            }
                        }
                    }

                    BlockState state = bridge.getBlock(localX, localY, localZ);
                    if (state != null && !state.is(Blocks.AIR)) {
                        BlockPos pos = new BlockPos(x, y, z);
                        world.setBlock(pos, state, 3);
                        BlockState updatedState = Block.updateFromNeighbourShapes(
                                world.getBlockState(pos), world, pos);
                        world.setBlock(pos, updatedState, 3);
                    }

                    // 遇到支撑方块停止向下
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
