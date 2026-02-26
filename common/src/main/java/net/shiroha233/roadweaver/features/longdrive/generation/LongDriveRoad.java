package net.shiroha233.roadweaver.features.longdrive.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.features.longdrive.LongDriveRoadTypes;
import net.shiroha233.roadweaver.features.longdrive.config.LongDriveGenerationConfig;
import net.shiroha233.roadweaver.features.longdrive.pathfinding.GreedyForwardPathfinder;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 长途旅行主干道生成器。
 * 使用 Highway 材质 + Path 机制（路基/路灯/半砖），roadType=LONG_DRIVE(3)。
 */
public final class LongDriveRoad {
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
     * 生成一段主干道。
     * @param maxSteps 贪婪寻路最大步数
     * @return 路径末端位置，null 表示失败
     */
    public BlockPos generate(int maxSteps) {
        if (level == null || genConfig == null) return null;
        int width = Math.max(1, genConfig.roadWidth());

        // 长途旅行使用 Highway 材质（灰色混凝土 + 白色中线），roadType 标记为 LONG_DRIVE
        List<BlockState> materials = List.of(
                net.minecraft.world.level.block.Blocks.GRAY_CONCRETE.defaultBlockState());
        List<BlockState> slabMaterials = List.of(
                net.minecraft.world.level.block.Blocks.GRAY_CONCRETE.defaultBlockState());

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            List<Records.RoadSegmentPlacement> segments = GreedyForwardPathfinder.findPath(
                    start, dirX, dirZ, maxSteps, width, level, cache,
                    genConfig.pathfinding(), genConfig.directionBias());
            if (segments == null || segments.size() < 3) return null;

            List<Records.RoadSpan> spans = RoadPathCalculator.extractSpans(
                    segments, level, cache, genConfig.pathfinding());
            List<Integer> targetY = computeTargetY(segments, spans);

            Records.RoadData rd = new Records.RoadData(
                    width, LongDriveRoadTypes.LONG_DRIVE,
                    materials, slabMaterials, segments, spans, targetY);
            RoadShardStorage.addRoad(level, rd);

            BlockPos last = segments.get(segments.size() - 1).middlePos();
            return last;
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("roadweaver")
                    .warn("LongDriveRoad: generation failed from {}", start, t);
            return null;
        } finally {
            cache.clear();
        }
    }

    private List<Integer> computeTargetY(List<Records.RoadSegmentPlacement> segments,
                                         List<Records.RoadSpan> spans) {
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
