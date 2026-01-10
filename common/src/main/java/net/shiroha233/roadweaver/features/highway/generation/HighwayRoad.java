package net.shiroha233.roadweaver.features.highway.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.features.highway.HighwayRoadTypes;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.highway.pathfinding.HighwayPathCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureRoadOffsetService;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highway 道路生成器。
 *
 * 职责：
 * - 生成一条 Highway：寻路 -> 后处理 -> 写入 RoadShardStorage（SQLite）。
 */
public final class HighwayRoad {
    private final ServerLevel level;
    private final Records.StructureConnection connection;
    private final HighwayGenerationConfig genConfig;

    public HighwayRoad(ServerLevel level, Records.StructureConnection connection, HighwayGenerationConfig genConfig) {
        this.level = level;
        this.connection = connection;
        this.genConfig = genConfig;
    }

    public boolean generateRoad(int maxSteps) {
        if (level == null || connection == null || genConfig == null) return false;
        if (!Level.OVERWORLD.equals(level.dimension())) return false;
        int width = Math.max(1, genConfig.roadWidth());

        // Highway 不使用 preset 材质系统：铺设阶段固定混凝土。
        List<BlockState> materials = List.of();
        List<BlockState> slabMaterials = List.of();

        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            List<Records.RoadSegmentPlacement> rawSegments = HighwayPathCalculator.calculateHighwayPath(
                    rawStart, rawEnd, width, level, Math.max(1, maxSteps), cache, genConfig);
            if (rawSegments == null || rawSegments.size() < 3) return false;

            List<Records.RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level, rawSegments, rawStart, rawEnd);
            if (segments == null || segments.size() < 3) return false;

            // 桥梁检测已移至区块生成阶段（RealTimeBridgeDetector），寻路阶段不再预计算 spans
            List<Records.RoadSpan> spans = List.of();
            List<Integer> targetY = computeTargetY(level, segments, spans, cache, genConfig);

            Records.RoadData rd = new Records.RoadData(
                    width,
                    HighwayRoadTypes.HIGHWAY,
                    materials,
                    slabMaterials,
                    segments,
                    spans,
                    targetY
            );
            RoadShardStorage.addRoad(level, rd);
            return true;
        } finally {
            cache.clear();
        }
    }

    private static List<Integer> computeTargetY(ServerLevel level,
                                               List<Records.RoadSegmentPlacement> segments,
                                               List<Records.RoadSpan> spans,
                                               TerrainSamplingCache cache,
                                               HighwayGenerationConfig cfg) {
        int n = segments.size();
        List<BlockPos> centers = new ArrayList<>(n);
        for (Records.RoadSegmentPlacement s : segments) centers.add(s.middlePos());

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
                sum += yTop;
                cnt++;
            }
            base[i] = cnt > 0 ? (int) Math.round(sum / (double) cnt) : centers.get(i).getY();
        }

        if (!cfg.slopeLimitEnabled()) {
            List<Integer> out = new ArrayList<>(n);
            for (int v : base) out.add(v);
            return out;
        }

        // 可配置的限坡平滑：默认每 5 格高度差为 1。
        // 原理：将“最大坡度”表达为 rise/run（单位：方块/方块），并根据相邻中心点实际水平距离计算允许的最大高度变化。
        int slopeRunBlocks = Math.max(1, cfg.slopeRunBlocks());
        int slopeRiseBlocks = Math.max(0, cfg.slopeRiseBlocks());
        int[] smoothed = HighwayHeightSmoother.smooth(base, centers, isBridge, slopeRunBlocks, slopeRiseBlocks);

        List<Integer> out = new ArrayList<>(n);
        for (int v : smoothed) out.add(v);
        return out;
    }
}
