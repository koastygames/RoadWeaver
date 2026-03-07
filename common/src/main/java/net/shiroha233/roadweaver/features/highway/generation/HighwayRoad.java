package net.shiroha233.roadweaver.features.highway.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.HighwayRoadTypes;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.highway.pathfinding.HighwayPathCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureRoadOffsetService;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highway 道路生成器
 */
public final class HighwayRoad {
    private final ServerLevel level;
    private final StructureConnection connection;
    private final HighwayGenerationConfig genConfig;

    public HighwayRoad(ServerLevel level, StructureConnection connection, HighwayGenerationConfig genConfig) {
        this.level = level;
        this.connection = connection;
        this.genConfig = genConfig;
    }

    public boolean generateRoad(int maxSteps) {
        if (level == null || connection == null || genConfig == null) return false;
        if (!Level.OVERWORLD.equals(level.dimension())) return false;
        int width = Math.max(1, genConfig.roadWidth());

        List<BlockState> materials = List.of();
        List<BlockState> slabMaterials = List.of();

        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            RoadGenerationConfig adapted = genConfig.toRoadGenerationConfig();
            List<RoadSegmentPlacement> rawSegments = HighwayPathCalculator.calculateHighwayPath(
                    rawStart, rawEnd, width, level, Math.max(1, maxSteps), cache, genConfig);
            if (rawSegments == null || rawSegments.size() < 3) return false;

            List<RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level, rawSegments, rawStart, rawEnd);
            if (segments == null || segments.size() < 3) return false;

            List<RoadSpan> spans = RoadPathCalculator.extractSpans(segments, level, cache, adapted.bridgeMinWaterDepth());
            List<Integer> targetY = computeTargetY(level, segments, spans, cache, genConfig);

            RoadData rd = new RoadData(
                    width,
                    HighwayRoadTypes.HIGHWAY,
                    materials,
                    slabMaterials,
                    segments,
                    spans,
                    targetY,
                    RoadData.NO_OWNER_2D,
                    RoadData.NO_OWNER_2D
            );
            RoadShardStorage.addRoad(level, rd);
            return true;
        } finally {
            cache.clear();
        }
    }

    private static List<Integer> computeTargetY(ServerLevel level,
                                               List<RoadSegmentPlacement> segments,
                                               List<RoadSpan> spans,
                                               TerrainSamplingCache cache,
                                               HighwayGenerationConfig cfg) {
        int n = segments.size();
        List<BlockPos> centers = new ArrayList<>(n);
        for (RoadSegmentPlacement s : segments) centers.add(s.middlePos());

        boolean[] isBridge = new boolean[n];
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < centers.size(); i++) indexMap.put(centers.get(i).asLong(), i);
            for (RoadSpan sp : spans) {
                if (sp.type() != SpanType.BRIDGE) continue;
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
                int yTop = sp.getY();
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

        int slopeRunBlocks = Math.max(1, cfg.slopeRunBlocks());
        int slopeRiseBlocks = Math.max(0, cfg.slopeRiseBlocks());
        int[] smoothed = HighwayHeightSmoother.smooth(base, centers, isBridge, slopeRunBlocks, slopeRiseBlocks);

        List<Integer> out = new ArrayList<>(n);
        for (int v : smoothed) out.add(v);
        return out;
    }
}
