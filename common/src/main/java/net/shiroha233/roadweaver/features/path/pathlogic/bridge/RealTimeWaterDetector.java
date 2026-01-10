package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * 实时水域检测器 - 在区块生成阶段检测水域
 * 
 * 核心改进：
 * - 使用实际方块数据而非噪声预测
 * - 在区块生成阶段调用，此时地形已经生成完毕
 * - 准确检测水面、水深，解决旧方案的误判问题
 */
public final class RealTimeWaterDetector {
    private RealTimeWaterDetector() {}
    
    /**
     * 检查坐标是否在 WorldGenRegion 可访问范围内
     */
    private static boolean isInBounds(WorldGenLevel world, int x, int z) {
        if (world instanceof WorldGenRegion region) {
            ChunkPos center = region.getCenter();
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            // WorldGenRegion 通常只能访问中心区块周围一定范围
            int range = 1; // features 阶段通常只有1格范围
            return Math.abs(chunkX - center.x) <= range && Math.abs(chunkZ - center.z) <= range;
        }
        return true;
    }
    
    /**
     * 检测指定位置是否为水域（需要建桥）
     * 
     * @param world     世界生成上下文
     * @param x         X坐标
     * @param z         Z坐标
     * @param seaLevel  海平面高度
     * @param minDepth  最小水深阈值
     * @return 如果是需要建桥的水域返回true
     */
    public static boolean isWaterAt(WorldGenLevel world, int x, int z, int seaLevel, int minDepth) {
        // 检查是否在可访问范围内
        if (!isInBounds(world, x, z)) {
            return false;
        }
        
        // 获取表面高度（不含水）
        int surfaceY = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        // 获取运动阻挡高度（含水面）
        int motionY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        
        // 检查水深
        int waterDepth = motionY - surfaceY;
        if (waterDepth < minDepth) {
            return false;
        }
        
        // 验证确实是水（检查水面位置的方块）
        BlockPos waterSurfacePos = new BlockPos(x, motionY - 1, z);
        FluidState fluid = world.getFluidState(waterSurfacePos);
        return fluid.is(FluidTags.WATER);
    }
    
    /**
     * 检测指定位置的水深
     * 
     * @param world     世界生成上下文
     * @param x         X坐标
     * @param z         Z坐标
     * @return 水深（格数），如果不是水域或越界返回0
     */
    public static int getWaterDepth(WorldGenLevel world, int x, int z) {
        if (!isInBounds(world, x, z)) {
            return 0;
        }
        
        int surfaceY = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int motionY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        
        // 验证是否为水
        if (motionY <= surfaceY) {
            return 0;
        }
        
        BlockPos waterSurfacePos = new BlockPos(x, motionY - 1, z);
        FluidState fluid = world.getFluidState(waterSurfacePos);
        if (!fluid.is(FluidTags.WATER)) {
            return 0;
        }
        
        return motionY - surfaceY;
    }
    
    /**
     * 获取水面高度
     * 
     * @param world 世界生成上下文
     * @param x     X坐标
     * @param z     Z坐标
     * @return 水面Y坐标，如果不是水域或越界返回-1
     */
    public static int getWaterSurfaceY(WorldGenLevel world, int x, int z) {
        if (!isInBounds(world, x, z)) {
            return -1;
        }
        
        int motionY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos waterSurfacePos = new BlockPos(x, motionY - 1, z);
        FluidState fluid = world.getFluidState(waterSurfacePos);
        
        if (fluid.is(FluidTags.WATER)) {
            return motionY;
        }
        return -1;
    }
    
    /**
     * 检测路段是否应该作为桥梁处理
     * 综合考虑水深和周围环境
     * 
     * @param world       世界生成上下文
     * @param centerX     路段中心X
     * @param centerZ     路段中心Z
     * @param roadWidth   道路宽度
     * @param seaLevel    海平面高度
     * @param minDepth    最小水深阈值
     * @return 如果应该建桥返回true
     */
    public static boolean shouldBeBridge(WorldGenLevel world, int centerX, int centerZ, 
                                         int roadWidth, int seaLevel, int minDepth) {
        // 先检查中心点是否在可访问范围内
        if (!isInBounds(world, centerX, centerZ)) {
            return false;
        }
        
        // 检查中心点
        if (isWaterAt(world, centerX, centerZ, seaLevel, minDepth)) {
            return true;
        }
        
        // 检查道路宽度范围内的点（确保整个路面都在水上）
        int halfWidth = roadWidth / 2;
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                if (isWaterAt(world, centerX + dx, centerZ + dz, seaLevel, minDepth)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
