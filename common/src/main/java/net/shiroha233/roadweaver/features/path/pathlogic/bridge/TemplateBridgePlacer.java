package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import org.slf4j.Logger;

/**
 * 模板桥梁放置器 - 逐段放置桥梁
 * 
 * 核心逻辑：
 * 1. 每个路段放置一个"切片"
 * 2. 根据累计距离决定使用模板的哪一部分（桥头/桥身/桥尾）
 * 3. 桥身部分循环使用
 * 
 * 模板坐标系：
 * - X轴 = 桥的长度方向
 * - Y轴 = 高度
 * - Z轴 = 桥的宽度方向（居中）
 */
public final class TemplateBridgePlacer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private TemplateBridgePlacer() {}
    
    /** 桥梁段类型 */
    public enum BridgePart {
        START,  // 桥头
        DECK,   // 桥身
        END     // 桥尾
    }
    
    /**
     * 放置单个桥梁段切片
     * 
     * @param world          世界
     * @param middle         当前路段中心
     * @param prev           前一个路段中心
     * @param next           下一个路段中心
     * @param deckY          桥面高度
     * @param part           桥梁部分类型
     * @param distanceInPart 在当前部分中的累计距离
     */
    public static void placeSegment(WorldGenLevel world,
                                    BlockPos middle,
                                    BlockPos prev,
                                    BlockPos next,
                                    int deckY,
                                    BridgePart part,
                                    int distanceInPart) {
        ServerLevel server = world.getLevel();
        if (server == null) return;
        
        var bridge = BridgeTemplateStructureRegistry.choose(server, middle.hashCode());
        if (bridge == null) return;
        
        // 计算方向向量
        Vec3 tangent = new Vec3(next.getX() - prev.getX(), 0, next.getZ() - prev.getZ());
        double segmentLen = tangent.length();
        if (segmentLen < 0.001) return;
        tangent = tangent.normalize();
        
        // 垂直方向（桥宽度方向）
        Vec3 binormal = new Vec3(-tangent.z, 0, tangent.x);
        
        // 计算模板 X 坐标
        int templateX;
        switch (part) {
            case START:
                // 桥头部分：0 到 startLength-1
                templateX = Math.min(distanceInPart, bridge.getStartLength() - 1);
                break;
            case END:
                // 桥尾部分：从 totalLength - endLength 开始
                int endStart = bridge.getTotalLength() - bridge.getEndLength();
                templateX = endStart + Math.min(distanceInPart, bridge.getEndLength() - 1);
                break;
            case DECK:
            default:
                // 桥身循环
                int deckLen = Math.max(1, bridge.getDeckLength());
                templateX = bridge.getStartLength() + (distanceInPart % deckLen);
                break;
        }
        
        // 获取模板宽度
        // 放置当前切片
        Vec3 center = new Vec3(middle.getX() + 0.5, deckY, middle.getZ() + 0.5);
        int blocksPlaced = 0;
        
        // 遍历桥宽度方向
        for (int localZ = -8; localZ <= 8; localZ++) {
            // 检查是否在模板宽度范围内
            if (!bridge.isInVoxelGrid(templateX, 0, localZ)) continue;
            
            // 计算世界坐标
            double wx = center.x + binormal.x * localZ;
            double wz = center.z + binormal.z * localZ;
            int worldX = (int) Math.floor(wx);
            int worldZ = (int) Math.floor(wz);
            
            // 放置竖列
            for (int localY = -60; localY <= 20; localY++) {
                int worldY = deckY + localY;
                
                BlockState state = bridge.getBlock(templateX, localY, localZ);
                if (state == null || state.is(Blocks.AIR)) continue;
                
                BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                world.setBlock(pos, state, 3);
                
                BlockState updated = Block.updateFromNeighbourShapes(
                        world.getBlockState(pos), world, pos);
                if (!updated.equals(state)) {
                    world.setBlock(pos, updated, 3);
                }
                blocksPlaced++;
                
                // 遇到支撑方块停止向下
                BlockPos below = new BlockPos(worldX, worldY - 1, worldZ);
                if (world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)) {
                    break;
                }
            }
        }
        
        if (blocksPlaced > 0) {
            LOGGER.debug("[Bridge] {} 部分, templateX={}, 位置={}, 方块={}", 
                    part, templateX, middle, blocksPlaced);
        }
    }
    
    /**
     * 判断路段的桥梁部分类型
     */
    public static BridgePart determinePart(boolean prevIsWater, boolean currIsWater, boolean nextIsWater) {
        if (!currIsWater) return null;
        if (!prevIsWater) return BridgePart.START;
        if (!nextIsWater) return BridgePart.END;
        return BridgePart.DECK;
    }
}
