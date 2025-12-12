package net.shiroha233.roadweaver.features.roadlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 仅负责预热 TerrainSamplingCache：通过固定粗步长的轻量 A* 访问沿线采样点，
 * 提前填充高度/群系/水体等噪声采样缓存。
 * 不参与任何道路生成结果。
 */
public final class TerrainCachePrewarmer {
    private TerrainCachePrewarmer() {}

    private static final int COARSE_STEP = 64;

    public static void prewarmAlongRoute(BlockPos startGround,
                                        BlockPos endGround,
                                        ServerLevel level,
                                        int maxSteps,
                                        TerrainSamplingCache cache) {
        if (cache == null) return;
        if (startGround == null || endGround == null) return;
        if (maxSteps <= 0) return;

        // 仅预热：计算失败也不影响主流程
        calculateCoarseSkeleton(startGround, endGround, level, maxSteps, cache);
    }

    private static List<BlockPos> calculateCoarseSkeleton(BlockPos startGround,
                                                         BlockPos endGround,
                                                         ServerLevel level,
                                                         int maxSteps,
                                                         TerrainSamplingCache cache) {
        int d = COARSE_STEP;

        BlockPos start = new BlockPos(snapToGrid(startGround.getX(), d), 0, snapToGrid(startGround.getZ(), d));
        BlockPos end = new BlockPos(snapToGrid(endGround.getX(), d), 0, snapToGrid(endGround.getZ(), d));
        BlockPos startG = new BlockPos(start.getX(), heightSampler(cache, start.getX(), start.getZ(), level), start.getZ());
        BlockPos endG = new BlockPos(end.getX(), heightSampler(cache, end.getX(), end.getZ(), level), end.getZ());

        PriorityQueue<CoarseNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, CoarseNode> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        CoarseNode startNode = new CoarseNode(startG, null, 0.0, heuristicEuclid(startG, endG));
        open.add(startNode);
        best.put(posKey2d(startG), startNode);

        int[][] neighborOffsets = new int[][]{
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        int stepsBudget = Math.max(1, maxSteps);
        while (!open.isEmpty() && stepsBudget-- > 0) {
            if (Thread.currentThread().isInterrupted()) return null;
            CoarseNode cur = open.poll();
            if (cur == null) break;
            long ck = posKey2d(cur.pos);
            if (!closed.add(ck)) continue;

            // 到达终点附近即可：预热目的，不需要严格到点
            if (manhattan2d(cur.pos, endG) < d * 2) {
                // reconstructCoarse 会触发一些沿线采样（height/water/biome），进一步提升预热效果
                return reconstructCoarse(cur);
            }

            for (int[] off : neighborOffsets) {
                int nx = cur.pos.getX() + off[0];
                int nz = cur.pos.getZ() + off[1];
                int ny = heightSampler(cache, nx, nz, level);
                // 额外预热水体/群系相关缓存
                cache.isColumnWater(level, nx, nz);
                cache.getBiome(level, nx, nz);

                BlockPos np = new BlockPos(nx, ny, nz);
                long nk = posKey2d(np);
                if (closed.contains(nk)) continue;

                double stepCost = (Math.abs(off[0]) + Math.abs(off[1]) == 2 * d) ? 1.41421356237 : 1.0;
                double elevation = Math.abs(ny - cur.pos.getY());
                double g = cur.g + stepCost + elevation * 0.02;
                double h = heuristicEuclid(np, endG);
                double f = g + h;

                CoarseNode prevBest = best.get(nk);
                if (prevBest == null || g < prevBest.g) {
                    CoarseNode nxt = new CoarseNode(np, cur, g, f);
                    best.put(nk, nxt);
                    open.add(nxt);
                }
            }
        }
        return null;
    }

    private static long posKey2d(BlockPos p) {
        return (((long) p.getX()) << 32) ^ (p.getZ() & 0xffffffffL);
    }

    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristicEuclid(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) / (double) COARSE_STEP;
    }

    private static List<BlockPos> reconstructCoarse(CoarseNode end) {
        ArrayList<BlockPos> out = new ArrayList<>();
        CoarseNode c = end;
        while (c != null) {
            out.add(c.pos);
            c = c.parent;
        }
        Collections.reverse(out);
        return out;
    }

    private static int heightSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.height(level, x, z);
    }

    private static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    private static final class CoarseNode {
        final BlockPos pos;
        final CoarseNode parent;
        final double g;
        final double f;
        CoarseNode(BlockPos pos, CoarseNode parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }
}
