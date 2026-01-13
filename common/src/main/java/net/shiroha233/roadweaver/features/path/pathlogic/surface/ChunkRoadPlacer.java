package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

/**
 * 区块级道路放置器 - 实时高度计算与衔接
 * 
 * 核心改进：
 * - 不再依赖寻路阶段计算的高度（targetY）
 * - 在区块生成时获取实际地形高度
 * - 通过 RoadHeightCache 与相邻区块衔接
 * 
 * 放置流程：
 * 1. 获取当前位置的实际地形高度
 * 2. 查询相邻已放置道路的高度
 * 3. 计算衔接后的目标高度（每两格限制高度差）
 * 4. 铺设道路
 * 5. 缓存本位置的道路高度
 */
public final class ChunkRoadPlacer {
    private ChunkRoadPlacer() {}
    
    // 衔接搜索半径
    private static final int LINK_SEARCH_RADIUS = 8;
    
    /**
     * 计算并放置道路段
     * 
     * @param world           世界
     * @param server          服务端世界
     * @param seg             路段数据
     * @param segmentIndex    路段索引
     * @param middlePositions 所有中心点列表
     * @param roadType        道路类型
     * @param materials       材质列表
     * @param slabMaterials   半砖材质列表
     * @param random          随机源
     * @param cfg             配置
     * @param roadFingerprint 道路指纹（用于缓存）
     * @return 本段的实际放置高度
     */
    public static int placeSegmentWithRealHeight(WorldGenLevel world,
                                                  ServerLevel server,
                                                  Records.RoadSegmentPlacement seg,
                                                  int segmentIndex,
                                                  List<BlockPos> middlePositions,
                                                  int roadType,
                                                  List<BlockState> materials,
                                                  List<BlockState> slabMaterials,
                                                  RandomSource random,
                                                  ModConfig cfg,
                                                  long roadFingerprint) {
        BlockPos middle = seg.middlePos();
        int x = middle.getX();
        int z = middle.getZ();
        
        // 1. 获取实际地形高度（使用 OCEAN_FLOOR_WG 忽略植被）
        int terrainHeight = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int seaLevel = server.getSeaLevel();
        
        // 如果地形高度低于海平面，使用 WORLD_SURFACE_WG
        if (terrainHeight <= seaLevel + 2) {
            int surfaceHeight = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            terrainHeight = Math.max(terrainHeight, surfaceHeight);
        }
        
        // 获取配置的坡度限制（每两格最大高度差）
        int maxStep = cfg != null && cfg.slopeLimitEnabled() 
            ? Math.max(1, cfg.maxSlopeStepPerTwoSegments()) 
            : Integer.MAX_VALUE;
        
        // 2. 查询相邻已放置道路的高度（查找两格之前的位置）
        Integer linkedHeight = findLinkedHeight(server, middlePositions, segmentIndex);
        
        // 3. 计算目标高度（每两格限制高度差）
        int targetHeight;
        if (linkedHeight != null && maxStep != Integer.MAX_VALUE) {
            // 有衔接点：限制每两格的高度差
            int heightDiff = terrainHeight - linkedHeight;
            if (Math.abs(heightDiff) > maxStep) {
                targetHeight = linkedHeight + (heightDiff > 0 ? maxStep : -maxStep);
            } else {
                targetHeight = terrainHeight;
            }
        } else {
            // 无衔接点或未启用限制：使用地形高度
            targetHeight = terrainHeight;
        }
        
        // 4. 构建目标高度数组（用于 SegmentPaver）
        int[] targetY = buildTargetYArray(middlePositions, segmentIndex, targetHeight);
        
        // 5. 铺设道路
        SegmentPaver.paveSegment(world, seg, segmentIndex, middlePositions, targetY, 
                                 roadType, materials, slabMaterials, random, cfg);
        
        // 6. 缓存本位置的道路高度
        RoadHeightCache.cachePlacedHeight(server, x, z, targetHeight);
        
        // 7. 如果在区块边界，缓存边界高度
        ChunkPos chunk = new ChunkPos(middle);
        cacheBoundaryIfNeeded(server, roadFingerprint, chunk, x, z, targetHeight);
        
        return targetHeight;
    }
    
    /**
     * 查找衔接高度（查找两格之前的位置，用于每两格限制高度差）
     */
    private static Integer findLinkedHeight(ServerLevel server, 
                                            List<BlockPos> middlePositions, 
                                            int segmentIndex) {
        // 优先查找两格之前的路段高度（每两格限制）
        if (segmentIndex >= 2) {
            BlockPos prev = middlePositions.get(segmentIndex - 2);
            Integer prevHeight = RoadHeightCache.getPlacedHeight(server, prev.getX(), prev.getZ());
            if (prevHeight != null) {
                return prevHeight;
            }
        }
        
        // 回退：查找前一个路段的高度
        if (segmentIndex > 0) {
            BlockPos prev = middlePositions.get(segmentIndex - 1);
            Integer prevHeight = RoadHeightCache.getPlacedHeight(server, prev.getX(), prev.getZ());
            if (prevHeight != null) {
                return prevHeight;
            }
        }
        
        // 查找两格之后的路段高度
        if (segmentIndex < middlePositions.size() - 2) {
            BlockPos next = middlePositions.get(segmentIndex + 2);
            Integer nextHeight = RoadHeightCache.getPlacedHeight(server, next.getX(), next.getZ());
            if (nextHeight != null) {
                return nextHeight;
            }
        }
        
        // 回退：查找后一个路段的高度
        if (segmentIndex < middlePositions.size() - 1) {
            BlockPos next = middlePositions.get(segmentIndex + 1);
            Integer nextHeight = RoadHeightCache.getPlacedHeight(server, next.getX(), next.getZ());
            if (nextHeight != null) {
                return nextHeight;
            }
        }
        
        // 搜索附近的已放置高度
        BlockPos current = middlePositions.get(segmentIndex);
        return RoadHeightCache.getNearbyPlacedHeight(server, current.getX(), current.getZ(), LINK_SEARCH_RADIUS);
    }
    
    /**
     * 构建目标高度数组
     * 
     * 注意：这里只设置当前段的高度，其他段使用占位值
     * SegmentPaver 会使用 RoadHeightInterpolator 进行插值
     */
    private static int[] buildTargetYArray(List<BlockPos> middlePositions, int segmentIndex, int targetHeight) {
        int n = middlePositions.size();
        int[] targetY = new int[n];
        
        // 初始化为当前高度（简化处理）
        for (int i = 0; i < n; i++) {
            targetY[i] = targetHeight;
        }
        
        // 设置当前段的精确高度
        targetY[segmentIndex] = targetHeight;
        
        return targetY;
    }
    
    /**
     * 如果在区块边界，缓存边界高度
     */
    private static void cacheBoundaryIfNeeded(ServerLevel server, long roadId, 
                                               ChunkPos chunk, int x, int z, int height) {
        int minX = chunk.getMinBlockX();
        int maxX = chunk.getMaxBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxZ = chunk.getMaxBlockZ();
        
        // 检查是否在边界（允许2格误差）
        if (x <= minX + 2) {
            RoadHeightCache.cacheBoundaryHeight(server, roadId, chunk, RoadHeightCache.Direction.WEST, height);
        }
        if (x >= maxX - 2) {
            RoadHeightCache.cacheBoundaryHeight(server, roadId, chunk, RoadHeightCache.Direction.EAST, height);
        }
        if (z <= minZ + 2) {
            RoadHeightCache.cacheBoundaryHeight(server, roadId, chunk, RoadHeightCache.Direction.NORTH, height);
        }
        if (z >= maxZ - 2) {
            RoadHeightCache.cacheBoundaryHeight(server, roadId, chunk, RoadHeightCache.Direction.SOUTH, height);
        }
    }
}
