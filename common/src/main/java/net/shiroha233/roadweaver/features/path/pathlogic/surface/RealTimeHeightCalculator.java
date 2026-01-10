package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 实时高度计算器 - 区块生成阶段计算道路高度
 * 
 * 核心改进：
 * - 只对当前区块内的位置获取实际地形高度
 * - 使用缓存的已放置高度作为"锚点"进行衔接
 * - 从锚点向两端平滑，确保跨区块的坡度连续性
 */
public final class RealTimeHeightCalculator {
    private RealTimeHeightCalculator() {}
    
    private static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    
    /**
     * 计算路段的实际放置高度
     * 
     * @param world           世界
     * @param server          服务端世界
     * @param middlePositions 所有中心点列表
     * @param currentChunk    当前正在生成的区块
     * @param cfg             配置
     * @return 计算后的目标高度数组
     */
    public static int[] calculateHeights(WorldGenLevel world,
                                         ServerLevel server,
                                         List<BlockPos> middlePositions,
                                         ChunkPos currentChunk,
                                         ModConfig cfg) {
        int n = middlePositions.size();
        int[] heights = new int[n];
        int[] terrainHeights = new int[n]; // 原始地形高度
        boolean[] isAnchored = new boolean[n]; // 是否为锚点（已放置的高度）
        int seaLevel = server.getSeaLevel();
        
        // 配置参数
        int maxStep = cfg != null && cfg.slopeLimitEnabled() 
            ? Math.max(1, cfg.maxSlopeStepPerTwoSegments()) 
            : Integer.MAX_VALUE;
        
        // 第一遍：收集高度信息
        for (int i = 0; i < n; i++) {
            BlockPos pos = middlePositions.get(i);
            ChunkPos posChunk = new ChunkPos(pos);
            
            // 优先查询缓存（已放置的道路高度 = 锚点）
            Integer cachedHeight = RoadHeightCache.getPlacedHeight(server, pos.getX(), pos.getZ());
            if (cachedHeight != null) {
                heights[i] = cachedHeight;
                terrainHeights[i] = cachedHeight;
                isAnchored[i] = true;
                continue;
            }
            
            // 只有当前区块内的位置才能安全获取高度
            if (posChunk.equals(currentChunk)) {
                int h = getTerrainHeightSafe(world, pos.getX(), pos.getZ(), seaLevel);
                heights[i] = h;
                terrainHeights[i] = h;
            } else {
                heights[i] = UNKNOWN_HEIGHT;
                terrainHeights[i] = UNKNOWN_HEIGHT;
            }
            isAnchored[i] = false;
        }
        
        // 第二遍：填充未知高度（使用插值）
        fillUnknownHeights(heights, terrainHeights, middlePositions, server, seaLevel);
        
        // 第三遍：从锚点向外平滑（核心：跨区块衔接）
        smoothFromAnchors(heights, terrainHeights, isAnchored, maxStep);
        
        return heights;
    }
    
    /**
     * 填充未知高度
     */
    private static void fillUnknownHeights(int[] heights, int[] terrainHeights,
                                           List<BlockPos> positions, 
                                           ServerLevel server, int seaLevel) {
        int n = heights.length;
        
        // 找到第一个和最后一个已知高度
        int firstKnown = -1;
        int lastKnown = -1;
        for (int i = 0; i < n; i++) {
            if (heights[i] != UNKNOWN_HEIGHT) {
                if (firstKnown < 0) firstKnown = i;
                lastKnown = i;
            }
        }
        
        // 如果没有任何已知高度，尝试从缓存获取附近高度
        if (firstKnown < 0) {
            int fallbackHeight = seaLevel + 4;
            if (!positions.isEmpty()) {
                BlockPos first = positions.get(0);
                Integer nearby = RoadHeightCache.getNearbyPlacedHeight(server, first.getX(), first.getZ(), 32);
                if (nearby != null) {
                    fallbackHeight = nearby;
                }
            }
            for (int i = 0; i < n; i++) {
                heights[i] = fallbackHeight;
                terrainHeights[i] = fallbackHeight;
            }
            return;
        }
        
        // 填充开头的未知高度
        for (int i = 0; i < firstKnown; i++) {
            heights[i] = heights[firstKnown];
            terrainHeights[i] = terrainHeights[firstKnown];
        }
        
        // 填充结尾的未知高度
        for (int i = lastKnown + 1; i < n; i++) {
            heights[i] = heights[lastKnown];
            terrainHeights[i] = terrainHeights[lastKnown];
        }
        
        // 填充中间的未知高度（线性插值）
        int prevKnown = firstKnown;
        for (int i = firstKnown + 1; i <= lastKnown; i++) {
            if (heights[i] != UNKNOWN_HEIGHT) {
                if (i > prevKnown + 1) {
                    interpolate(heights, prevKnown, i);
                    interpolate(terrainHeights, prevKnown, i);
                }
                prevKnown = i;
            }
        }
    }
    
    /**
     * 线性插值填充
     */
    private static void interpolate(int[] arr, int start, int end) {
        int startH = arr[start];
        int endH = arr[end];
        int span = end - start;
        for (int i = start + 1; i < end; i++) {
            float t = (float)(i - start) / span;
            arr[i] = Math.round(startH + (endH - startH) * t);
        }
    }
    
    /**
     * 从锚点向外平滑（核心算法）
     * 
     * 策略：
     * 1. 找到所有锚点（已放置的道路高度）
     * 2. 从每个锚点向两端扩展，限制坡度
     * 3. 对于非锚点位置，在地形高度和平滑高度之间取较接近锚点的值
     */
    private static void smoothFromAnchors(int[] heights, int[] terrainHeights, 
                                          boolean[] isAnchored, int maxStep) {
        int n = heights.length;
        if (n < 2 || maxStep == Integer.MAX_VALUE) return;
        
        // 找到所有锚点
        int firstAnchor = -1;
        int lastAnchor = -1;
        for (int i = 0; i < n; i++) {
            if (isAnchored[i]) {
                if (firstAnchor < 0) firstAnchor = i;
                lastAnchor = i;
            }
        }
        
        // 如果有锚点，从锚点向两端平滑（每两格限制高度差）
        if (firstAnchor >= 0) {
            // 从第一个锚点向前平滑
            for (int i = firstAnchor - 1; i >= 0; i--) {
                // 每两格检查一次高度差
                int refIndex = Math.min(i + 2, firstAnchor);
                int refH = heights[refIndex];
                int targetH = terrainHeights[i];
                heights[i] = clampToSlope(targetH, refH, maxStep);
            }
            
            // 从最后一个锚点向后平滑
            for (int i = lastAnchor + 1; i < n; i++) {
                // 每两格检查一次高度差
                int refIndex = Math.max(i - 2, lastAnchor);
                int refH = heights[refIndex];
                int targetH = terrainHeights[i];
                heights[i] = clampToSlope(targetH, refH, maxStep);
            }
            
            // 处理锚点之间的区间
            int prevAnchor = firstAnchor;
            for (int i = firstAnchor + 1; i <= lastAnchor; i++) {
                if (isAnchored[i]) {
                    if (i > prevAnchor + 1) {
                        smoothBetweenAnchors(heights, terrainHeights, prevAnchor, i, maxStep);
                    }
                    prevAnchor = i;
                }
            }
        } else {
            // 没有锚点，使用简单的双向平滑
            simpleSmooth(heights, terrainHeights, maxStep);
        }
    }
    
    /**
     * 在两个锚点之间平滑
     */
    private static void smoothBetweenAnchors(int[] heights, int[] terrainHeights,
                                             int startAnchor, int endAnchor, int maxStep) {
        int span = endAnchor - startAnchor;
        int startH = heights[startAnchor];
        int endH = heights[endAnchor];
        
        // 计算理想的线性插值高度
        int[] idealHeights = new int[span + 1];
        for (int i = 0; i <= span; i++) {
            float t = (float) i / span;
            idealHeights[i] = Math.round(startH + (endH - startH) * t);
        }
        
        // 从起点向终点平滑（每两格检查）
        int[] forwardHeights = new int[span + 1];
        forwardHeights[0] = startH;
        for (int i = 1; i <= span; i++) {
            int idx = startAnchor + i;
            int targetH = terrainHeights[idx];
            // 在地形高度和理想高度之间选择更接近参考点的
            int refIdx = Math.max(0, i - 2);
            int refH = forwardHeights[refIdx];
            int idealH = idealHeights[i];
            int preferred = Math.abs(targetH - refH) < Math.abs(idealH - refH) 
                ? targetH : idealH;
            forwardHeights[i] = clampToSlope(preferred, refH, maxStep);
        }
        
        // 从终点向起点平滑（每两格检查）
        int[] backwardHeights = new int[span + 1];
        backwardHeights[span] = endH;
        for (int i = span - 1; i >= 0; i--) {
            int idx = startAnchor + i;
            int targetH = terrainHeights[idx];
            int refIdx = Math.min(span, i + 2);
            int refH = backwardHeights[refIdx];
            int idealH = idealHeights[i];
            int preferred = Math.abs(targetH - refH) < Math.abs(idealH - refH) 
                ? targetH : idealH;
            backwardHeights[i] = clampToSlope(preferred, refH, maxStep);
        }
        
        // 取两个方向的平均值
        for (int i = 1; i < span; i++) {
            heights[startAnchor + i] = (forwardHeights[i] + backwardHeights[i]) / 2;
        }
    }
    
    /**
     * 简单的双向平滑（无锚点时使用，每两格限制高度差）
     */
    private static void simpleSmooth(int[] heights, int[] terrainHeights, int maxStep) {
        int n = heights.length;
        
        // 正向平滑（每两格检查）
        for (int i = 1; i < n; i++) {
            int refIndex = Math.max(0, i - 2);
            heights[i] = clampToSlope(terrainHeights[i], heights[refIndex], maxStep);
        }
        
        // 反向平滑并取平均
        int[] backward = new int[n];
        backward[n - 1] = heights[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            int refIndex = Math.min(n - 1, i + 2);
            backward[i] = clampToSlope(terrainHeights[i], backward[refIndex], maxStep);
        }
        
        // 取平均
        for (int i = 0; i < n; i++) {
            heights[i] = (heights[i] + backward[i]) / 2;
        }
    }
    
    /**
     * 限制坡度（每两格高度差不超过 maxStep）
     * 
     * @param targetHeight    目标高度
     * @param referenceHeight 参考高度（两格之前的高度）
     * @param maxStep         每两格最大高度差
     */
    private static int clampToSlope(int targetHeight, int referenceHeight, int maxStep) {
        int diff = targetHeight - referenceHeight;
        if (Math.abs(diff) <= maxStep) {
            return targetHeight;
        }
        return referenceHeight + (diff > 0 ? maxStep : -maxStep);
    }
    
    /**
     * 安全获取地形高度
     */
    private static int getTerrainHeightSafe(WorldGenLevel world, int x, int z, int seaLevel) {
        try {
            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (motion > seaLevel + 2) {
                return motion;
            }
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            return Math.max(motion, surface);
        } catch (Exception e) {
            return UNKNOWN_HEIGHT;
        }
    }
}
