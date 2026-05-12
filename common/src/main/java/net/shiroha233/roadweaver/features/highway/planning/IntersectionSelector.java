/* 文件职责：基于地形平坦度分析为 Highway 单元格选择最优交叉点位置。 */
package net.shiroha233.roadweaver.features.highway.planning;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.features.highway.terrain.HighwayCellTerrainField;

/**
 * Highway 交叉点选择器，使用积分图加速滑动窗口方差计算
 */
public final class IntersectionSelector {
    private IntersectionSelector() {}

    private static final long OCEAN_SKIP_MARKER = Long.MIN_VALUE;

    /**
     * 为单元格选择最优交叉点位置
     * @return 编码后的位置 (x << 32 | z & 0xFFFFFFFFL)，或 OCEAN_SKIP_MARKER 表示跳过
     */
    public static long selectIntersection(HighwayCellTerrainField terrain,
                                          int cellMinX,
                                          int cellMinZ,
                                          int gridBlocks,
                                          int windowSize,
                                          double edgeMargin) {
        if (terrain == null) return OCEAN_SKIP_MARKER;

        int step = terrain.step();
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        int windowGrid = Math.max(2, windowSize / step);

        int marginGrid = (int) (sizeX * edgeMargin);
        int scanMinX = marginGrid;
        int scanMinZ = marginGrid;
        int scanMaxX = sizeX - marginGrid - windowGrid;
        int scanMaxZ = sizeZ - marginGrid - windowGrid;

        if (scanMaxX <= scanMinX || scanMaxZ <= scanMinZ) {
            scanMinX = 0;
            scanMinZ = 0;
            scanMaxX = sizeX - windowGrid;
            scanMaxZ = sizeZ - windowGrid;
        }
        if (scanMaxX <= scanMinX || scanMaxZ <= scanMinZ) {
            return OCEAN_SKIP_MARKER;
        }

        // 构建积分图：高度和、高度平方和、水体计数
        long[] sumH = new long[(sizeX + 1) * (sizeZ + 1)];
        long[] sumH2 = new long[(sizeX + 1) * (sizeZ + 1)];
        int[] sumW = new int[(sizeX + 1) * (sizeZ + 1)];
        int stride = sizeX + 1;

        for (int gz = 0; gz < sizeZ; gz++) {
            for (int gx = 0; gx < sizeX; gx++) {
                int h = terrain.heightAt(gx, gz);
                int w = terrain.isWaterAt(gx, gz) ? 1 : 0;
                int idx = (gz + 1) * stride + (gx + 1);
                sumH[idx] = h + sumH[gz * stride + (gx + 1)] + sumH[(gz + 1) * stride + gx] - sumH[gz * stride + gx];
                sumH2[idx] = (long) h * h + sumH2[gz * stride + (gx + 1)] + sumH2[(gz + 1) * stride + gx] - sumH2[gz * stride + gx];
                sumW[idx] = w + sumW[gz * stride + (gx + 1)] + sumW[(gz + 1) * stride + gx] - sumW[gz * stride + gx];
            }
        }

        double bestScore = Double.MAX_VALUE;
        int bestGx = -1;
        int bestGz = -1;
        int centerX = sizeX / 2;
        int centerZ = sizeZ / 2;
        double maxEdgeDist = Math.hypot(centerX, centerZ);

        int scanStep = Math.max(1, windowGrid / 4);

        for (int wz = scanMinZ; wz <= scanMaxZ; wz += scanStep) {
            for (int wx = scanMinX; wx <= scanMaxX; wx += scanStep) {
                int x0 = wx;
                int z0 = wz;
                int x1 = wx + windowGrid;
                int z1 = wz + windowGrid;
                int area = windowGrid * windowGrid;

                int waterCount = rectSum(sumW, stride, x0, z0, x1, z1);
                double waterRatio = (double) waterCount / area;
                if (waterRatio > 0.5) continue;

                long hSum = rectSumLong(sumH, stride, x0, z0, x1, z1);
                long h2Sum = rectSumLong(sumH2, stride, x0, z0, x1, z1);
                double mean = (double) hSum / area;
                double variance = (double) h2Sum / area - mean * mean;

                int midGx = wx + windowGrid / 2;
                int midGz = wz + windowGrid / 2;
                double edgeDist = Math.hypot(midGx - centerX, midGz - centerZ) / maxEdgeDist;

                double score = variance + waterRatio * 10000.0 + edgeDist * 50.0;

                if (score < bestScore) {
                    bestScore = score;
                    bestGx = midGx;
                    bestGz = midGz;
                }
            }
        }

        if (bestGx < 0) return OCEAN_SKIP_MARKER;

        // 精扫：在粗扫最优位置附近细化
        int refineRadius = scanStep * 2;
        int fineMinX = Math.max(scanMinX, bestGx - refineRadius - windowGrid / 2);
        int fineMinZ = Math.max(scanMinZ, bestGz - refineRadius - windowGrid / 2);
        int fineMaxX = Math.min(scanMaxX, bestGx + refineRadius - windowGrid / 2);
        int fineMaxZ = Math.min(scanMaxZ, bestGz + refineRadius - windowGrid / 2);

        for (int wz = fineMinZ; wz <= fineMaxZ; wz++) {
            for (int wx = fineMinX; wx <= fineMaxX; wx++) {
                int x0 = wx;
                int z0 = wz;
                int x1 = wx + windowGrid;
                int z1 = wz + windowGrid;
                int area = windowGrid * windowGrid;

                int waterCount = rectSum(sumW, stride, x0, z0, x1, z1);
                double waterRatio = (double) waterCount / area;
                if (waterRatio > 0.5) continue;

                long hSum = rectSumLong(sumH, stride, x0, z0, x1, z1);
                long h2Sum = rectSumLong(sumH2, stride, x0, z0, x1, z1);
                double mean = (double) hSum / area;
                double variance = (double) h2Sum / area - mean * mean;

                int midGx = wx + windowGrid / 2;
                int midGz = wz + windowGrid / 2;
                double edgeDist = Math.hypot(midGx - centerX, midGz - centerZ) / maxEdgeDist;

                double score = variance + waterRatio * 10000.0 + edgeDist * 50.0;

                if (score < bestScore) {
                    bestScore = score;
                    bestGx = midGx;
                    bestGz = midGz;
                }
            }
        }

        if (bestGx < 0 || terrain.isWaterAt(bestGx, bestGz)) {
            return OCEAN_SKIP_MARKER;
        }

        int worldX = cellMinX + bestGx * step;
        int worldZ = cellMinZ + bestGz * step;
        return packPos(worldX, worldZ);
    }

    public static boolean isOceanSkip(long encoded) {
        return encoded == OCEAN_SKIP_MARKER;
    }

    public static BlockPos decodePos(long encoded) {
        int x = (int) (encoded >> 32);
        int z = (int) encoded;
        return new BlockPos(x, 0, z);
    }

    public static long packPos(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 检查两点连线上的水域占比
     */
    public static double waterRatioBetween(HighwayCellTerrainField terrain, int fromGx, int fromGz, int toGx, int toGz) {
        if (terrain == null) return 1.0;
        int dx = toGx - fromGx;
        int dz = toGz - fromGz;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) return terrain.isWaterAt(fromGx, fromGz) ? 1.0 : 0.0;

        int waterCount = 0;
        for (int i = 0; i <= steps; i++) {
            int gx = fromGx + dx * i / steps;
            int gz = fromGz + dz * i / steps;
            if (gx >= 0 && gx < terrain.sizeX() && gz >= 0 && gz < terrain.sizeZ()) {
                if (terrain.isWaterAt(gx, gz)) waterCount++;
            }
        }
        return (double) waterCount / (steps + 1);
    }

    private static int rectSum(int[] sat, int stride, int x0, int z0, int x1, int z1) {
        return sat[(z1) * stride + x1]
                - sat[(z0) * stride + x1]
                - sat[(z1) * stride + x0]
                + sat[(z0) * stride + x0];
    }

    private static long rectSumLong(long[] sat, int stride, int x0, int z0, int x1, int z1) {
        return sat[(z1) * stride + x1]
                - sat[(z0) * stride + x1]
                - sat[(z1) * stride + x0]
                + sat[(z0) * stride + x0];
    }
}
