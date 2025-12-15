package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.precompute.RoadsideStructurePrecomputer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路生成器
 * 
 * 职责：根据结构连接生成一条道路，包括寻路、路面生成、路边结构预计算
 * 
 * 设计原则：
 * - 接受配置快照，不依赖全局单例
 * - 所有配置在入口层读取，通过参数传递
 */
public final class Road {
    private final ServerLevel level;
    private final Records.StructureConnection connection;
    private final PathFeatureConfig featureConfig;
    private final RoadGenerationConfig genConfig;

    /**
     * 创建道路生成器
     * 
     * @param level          服务端世界
     * @param connection     结构连接
     * @param featureConfig  Feature 配置
     * @param genConfig      道路生成配置快照
     */
    public Road(ServerLevel level, Records.StructureConnection connection, 
                PathFeatureConfig featureConfig, RoadGenerationConfig genConfig) {
        this.level = level;
        this.connection = connection;
        this.featureConfig = featureConfig;
        this.genConfig = genConfig;
    }
    
    /**
     * 兼容旧 API：从全局配置创建
     * @deprecated 使用带 RoadGenerationConfig 参数的构造函数
     */
    @Deprecated
    public Road(ServerLevel level, Records.StructureConnection connection, PathFeatureConfig config) {
        this(level, connection, config, RoadGenerationConfig.from(ConfigService.get()));
    }

    /**
     * 生成道路
     * 
     * @param maxSteps 最大寻路步数
     */
    public void generateRoad(int maxSteps) {
        RandomSource random = RandomSource.create();
        int width = genConfig.effectiveRoadWidth(getRandomWidth(random, featureConfig));
        boolean allowA = genConfig.allowArtificial();
        boolean allowN = genConfig.allowNatural();
        if (!allowA && !allowN) return;
        int type = allowA && allowN ? (random.nextBoolean() ? 0 : 1) : (allowA ? 0 : 1);
        List<BlockState> materials;
        List<BlockState> slabMaterials = java.util.List.of();
        if (type == 0) {
            // 人工道路始终从 JSON 预设系统中选择一套材质
            ModConfig modCfg = ConfigService.get(); // 预设服务仍需要访问全局配置
            PresetService.PresetDef preset = PresetService.choosePresetForArtificial(random, modCfg);
            materials = PresetService.toBlockStatesFromIds(preset.materials());
            slabMaterials = PresetService.toBlockStatesFromIds(preset.slabMaterials());
        } else {
            materials = java.util.List.of(Blocks.DIRT_PATH.defaultBlockState(), Blocks.GRAVEL.defaultBlockState());
        }

        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();
        
        // 直接用原始端点做 A* 寻路，不预设偏移方向
        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            List<Records.RoadSegmentPlacement> rawSegments = RoadPathCalculator.calculateAStarRoadPath(
                    rawStart, rawEnd, width, level, maxSteps, cache, genConfig);
            if (rawSegments == null || rawSegments.size() < 5) return;
            
            // 寻路完成后，根据实际路径方向裁剪掉进入结构保护区的路段
            // 这样即使路径从意外方向绕过来，也不会穿过结构
            List<Records.RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level, rawSegments, rawStart, rawEnd);
            if (segments == null || segments.size() < 5) return;
            
            List<Records.RoadSpan> spans = RoadPathCalculator.extractSpans(segments, level, cache, genConfig.pathfinding());

            List<Integer> targetY = computeTargetY(level, segments, spans, cache, genConfig);

            Records.RoadData rd = new Records.RoadData(width, type, materials, slabMaterials, segments, spans, targetY);
            RoadShardStorage.addRoad(level, rd);
            
            // 寻路完成后，预计算路边结构位置
            // 如果区块还没生成，结构会在 STRUCTURE_STARTS 阶段注入，Beardifier 会自动处理地形
            // 如果区块已经生成，则在 Feature 阶段通过 RoadsideStructurePlacer 放置（无地形适应）
            RoadsideStructurePrecomputer.precomputeStructures(level, segments, spans, width, cache, random);
        } finally {
            // 单条道路生成结束后清空噪声采样缓存，避免长时间占用内存
            cache.clear();
        }
    }

    

    private static int getRandomWidth(RandomSource rnd, PathFeatureConfig cfg) {
        return 3;
    }
    
    private static List<Integer> computeTargetY(ServerLevel level, List<Records.RoadSegmentPlacement> segments, 
                                                   List<Records.RoadSpan> spans, TerrainSamplingCache cache,
                                                   RoadGenerationConfig cfg) {
        int n = segments.size();
        List<BlockPos> centers = new ArrayList<>(n);
        for (Records.RoadSegmentPlacement s : segments) centers.add(s.middlePos());

        // 将 spans 映射到索引范围，用于 BRIDGE
        boolean[] isBridge = new boolean[n];
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < centers.size(); i++) indexMap.put(centers.get(i).asLong(), i);
            for (Records.RoadSpan sp : spans) {
                if (sp.type() != Records.SpanType.BRIDGE) continue;
                Integer si = indexMap.get(sp.start().asLong());
                Integer ei = indexMap.get(sp.end().asLong());
                if (si == null || ei == null) continue;
                int a = Math.max(0, Math.min(si, ei));
                int b = Math.min(n - 1, Math.max(si, ei));
                for (int k = a; k <= b; k++) isBridge[k] = true;
            }
        }

        int avg = Math.max(0, cfg.averagingRadius());
        int[] base = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0, cnt = 0;
            int lo = Math.max(0, i - avg);
            int hi = Math.min(n - 1, i + avg);
            for (int j = lo; j <= hi; j++) {
                BlockPos sp = centers.get(j);
                int yTop = cache.height(level, sp.getX(), sp.getZ());
                sum += yTop; cnt++;
            }
            base[i] = cnt > 0 ? (int) Math.round(sum / (double) cnt) : centers.get(i).getY();
        }

        // 如果关闭限坡平滑，则直接使用基础平均高度，不再进行每两段步进限制
        if (!cfg.slopeLimitEnabled()) {
            List<Integer> out = new ArrayList<>(n);
            for (int v : base) out.add(v);
            return out;
        }

        int[] smoothed = base.clone();
        // 对每个连续非桥梁段进行平滑，以避免奇偶振荡
        int i = 0;
        while (i < n) {
            // 跳过桥梁索引
            while (i < n && isBridge[i]) i++;
            int s = i;
            while (i < n && !isBridge[i]) i++;
            int e = i - 1; // inclusive
            if (s <= e) {
                int step2 = Math.max(0, Math.min(8, cfg.maxSlopeStepPerTwoSegments()));
                int halfLow = Math.max(0, step2 / 2);
                int halfHigh = Math.max(0, (step2 + 1) / 2);
                for (int ii = s + 1; ii <= e; ii++) {
                    int y = smoothed[ii];
                    if (ii == s + 1) {
                        int py = smoothed[ii - 1];
                        if (y > py + halfLow) y = py + halfLow;
                        if (y < py - halfLow) y = py - halfLow;
                    } else {
                        int py = smoothed[ii - 1];
                        if (y > py + halfHigh) y = py + halfHigh;
                        if (y < py - halfHigh) y = py - halfHigh;
                        int p2 = smoothed[ii - 2];
                        int hi = p2 + step2;
                        int lo = p2 - step2;
                        if (y > hi) y = hi;
                        if (y < lo) y = lo;
                    }
                    smoothed[ii] = y;
                }
                for (int ii = e - 1; ii >= s; ii--) {
                    int y = smoothed[ii];
                    if (ii == e - 1) {
                        int ny = smoothed[ii + 1];
                        if (y > ny + halfLow) y = ny + halfLow;
                        if (y < ny - halfLow) y = ny - halfLow;
                    } else {
                        int ny = smoothed[ii + 1];
                        if (y > ny + halfHigh) y = ny + halfHigh;
                        if (y < ny - halfHigh) y = ny - halfHigh;
                        int n2 = smoothed[ii + 2];
                        int hi = n2 + step2;
                        int lo = n2 - step2;
                        if (y > hi) y = hi;
                        if (y < lo) y = lo;
                    }
                    smoothed[ii] = y;
                }
            }
        }

        List<Integer> out = new ArrayList<>(n);
        for (int v : smoothed) out.add(v);
        return out;
    }
    
}
