package net.shiroha233.roadweaver.features.longdrive.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.features.longdrive.config.LongDriveGenerationConfig;
import net.shiroha233.roadweaver.features.longdrive.pathfinding.GreedyForwardPathfinder;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.impl.PathPostProcessor;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 长途驾驶主干道生成器
 * 使用 Highway 材质 + Path 机制（路基/路灯/半砖），roadType=LONG_DRIVE(3)
 */
public final class LongDriveRoad {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private final ServerLevel level;
    private final BlockPos start;
    private final double dirX;
    private final double dirZ;
    private final LongDriveGenerationConfig genConfig;

    public LongDriveRoad(ServerLevel level, BlockPos start, double dirX, double dirZ,
                         LongDriveGenerationConfig genConfig) {
        this.level = level;
        this.start = start;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.genConfig = genConfig;
    }

    /**
     * 生成一段主干道
     * @param maxSteps 贪婪寻路最大步数
     * @return 路径末端位置，null 表示失败
     */
    public BlockPos generate(int maxSteps) {
        if (level == null || genConfig == null) return null;
        int width = Math.max(1, genConfig.roadWidth());

        List<BlockState> materials = List.of(Blocks.GRAY_CONCRETE.defaultBlockState());
        List<BlockState> slabMaterials = List.of(Blocks.GRAY_CONCRETE.defaultBlockState());

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            // 长途模式使用精准采样，避免快速采样导致的地形/水深误判
            cache.enableHighPrecision(level);
            GreedyForwardPathfinder pathfinder = new GreedyForwardPathfinder();
            PathResult result = pathfinder.findPath(
                    start, dirX, dirZ, maxSteps, width, level, cache,
                    genConfig.pathfindingCost(), genConfig.directionBias());

            if (!result.success() || result.segments().size() < 3) return null;

            List<BlockPos> rawPath = result.segments().stream()
                    .map(RoadSegmentPlacement::middlePos)
                    .toList();
            List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                    rawPath, width, level, cache, genConfig.bridgeMinWaterDepth());

            if (segments == null || segments.size() < 3) return null;

            List<RoadSpan> spans = extractSpans(segments, level, cache);
            List<Integer> targetY = computeTargetY(segments, spans);

            RoadData rd = new RoadData(
                    width, LongDriveRoadTypes.LONG_DRIVE,
                    materials, slabMaterials, segments, spans, targetY);
            RoadShardStorage.addRoad(level, rd);

            BlockPos last = segments.get(segments.size() - 1).middlePos();
            return last;
        } catch (Throwable t) {
            LOGGER.warn("LongDriveRoad: generation failed from {}", start, t);
            return null;
        } finally {
            cache.clear();
        }
    }

    private List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
                                        ServerLevel level, TerrainSamplingCache cache) {
        List<RoadSpan> spans = new ArrayList<>();
        int minWaterDepth = genConfig.bridgeMinWaterDepth();
        
        int i = 0;
        while (i < segments.size()) {
            BlockPos pos = segments.get(i).middlePos();
            int oceanFloor = cache.oceanFloor(level, pos.getX(), pos.getZ());
            int waterDepth = Math.max(0, level.getSeaLevel() - oceanFloor);
            
            if (waterDepth >= minWaterDepth) {
                int start = i;
                while (i < segments.size()) {
                    BlockPos p = segments.get(i).middlePos();
                    int floor = cache.oceanFloor(level, p.getX(), p.getZ());
                    int depth = Math.max(0, level.getSeaLevel() - floor);
                    if (depth < minWaterDepth) break;
                    i++;
                }
                if (i > start) {
                    spans.add(new RoadSpan(
                            segments.get(start).middlePos(),
                            segments.get(i - 1).middlePos(),
                            SpanType.BRIDGE));
                }
            } else {
                i++;
            }
        }
        return spans;
    }

    private List<Integer> computeTargetY(List<RoadSegmentPlacement> segments, List<RoadSpan> spans) {
        int n = segments.size();
        List<BlockPos> centers = new ArrayList<>(n);
        for (RoadSegmentPlacement s : segments) centers.add(s.middlePos());

        boolean[] isBridge = new boolean[n];
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < centers.size(); i++) {
                indexMap.put(centers.get(i).asLong(), i);
            }
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

        int avg = Math.max(0, genConfig.averagingRadius());
        int[] base = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0, cnt = 0;
            int lo = Math.max(0, i - avg);
            int hi = Math.min(n - 1, i + avg);
            for (int j = lo; j <= hi; j++) {
                sum += centers.get(j).getY();
                cnt++;
            }
            base[i] = cnt > 0 ? Math.round(sum / (float) cnt) : centers.get(i).getY();
        }

        if (!genConfig.slopeLimitEnabled()) {
            List<Integer> out = new ArrayList<>(n);
            for (int v : base) out.add(v);
            return out;
        }

        int[] smoothed = base.clone();
        int step2 = Math.max(0, Math.min(8, genConfig.maxSlopeStepPerTwoSegments()));
        int halfLow = Math.max(0, step2 / 2);
        int halfHigh = Math.max(0, (step2 + 1) / 2);

        int i = 0;
        while (i < n) {
            while (i < n && isBridge[i]) i++;
            int s = i;
            while (i < n && !isBridge[i]) i++;
            int e = i - 1;
            if (s > e) continue;
            
            for (int ii = s + 1; ii <= e; ii++) {
                int y = smoothed[ii];
                int py = smoothed[ii - 1];
                int limit = (ii == s + 1) ? halfLow : halfHigh;
                if (y > py + limit) y = py + limit;
                if (y < py - limit) y = py - limit;
                if (ii >= s + 2) {
                    int p2 = smoothed[ii - 2];
                    y = Math.max(p2 - step2, Math.min(p2 + step2, y));
                }
                smoothed[ii] = y;
            }
            
            for (int ii = e - 1; ii >= s; ii--) {
                int y = smoothed[ii];
                int ny = smoothed[ii + 1];
                int limit = (ii == e - 1) ? halfLow : halfHigh;
                if (y > ny + limit) y = ny + limit;
                if (y < ny - limit) y = ny - limit;
                if (ii <= e - 2) {
                    int n2 = smoothed[ii + 2];
                    y = Math.max(n2 - step2, Math.min(n2 + step2, y));
                }
                smoothed[ii] = y;
            }
        }

        List<Integer> out = new ArrayList<>(n);
        for (int v : smoothed) out.add(v);
        return out;
    }
}
