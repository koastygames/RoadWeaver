package net.shiroha233.roadweaver.features.highway.generation;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Highway 高度平滑器（仅负责“限坡平滑”算法）。
 */
public final class HighwayHeightSmoother {
    private HighwayHeightSmoother() {}

    public static int[] smooth(int[] baseY,
                               List<BlockPos> centers,
                               boolean[] isBridge,
                               int slopeRunBlocks,
                               int slopeRiseBlocks) {
        if (baseY == null || centers == null || isBridge == null) return baseY;
        if (centers.size() != baseY.length || isBridge.length != baseY.length) return baseY;

        int n = baseY.length;
        if (n <= 2) return baseY.clone();

        int run = Math.max(1, slopeRunBlocks);
        int rise = Math.max(0, slopeRiseBlocks);
        if (rise <= 0) {
            return baseY.clone();
        }

        // 核心思想：用 double 高度做“连续限坡”，再 round 回 int。
        // 这样就能表达诸如 1/5 的坡度（每格 0.2），最终落到方块高度会自然形成“每 5 格升 1”的台阶。
        double maxSlope = rise / (double) run;

        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = baseY[i];
        }

        int i = 0;
        while (i < n) {
            while (i < n && isBridge[i]) i++;
            int s = i;
            while (i < n && !isBridge[i]) i++;
            int e = i - 1;
            if (s <= e) {
                // forward: 约束 y[i] 与 y[i-1] 的最大高度差
                for (int k = s + 1; k <= e; k++) {
                    double dist = dist2d(centers.get(k - 1), centers.get(k));
                    double maxDelta = maxSlope * dist;
                    y[k] = clamp(y[k], y[k - 1] - maxDelta, y[k - 1] + maxDelta);
                }

                // backward: 再从后往前约束一次，避免单向传播造成偏移
                for (int k = e - 1; k >= s; k--) {
                    double dist = dist2d(centers.get(k), centers.get(k + 1));
                    double maxDelta = maxSlope * dist;
                    y[k] = clamp(y[k], y[k + 1] - maxDelta, y[k + 1] + maxDelta);
                }
            }
        }

        int[] out = new int[n];
        // 将 double 高度离散为 int：
        // - 上坡用 floor：保证台阶不会“提前跳高”（例如 1/5 的坡度应该尽量接近每 5 格升 1）。
        // - 下坡用 ceil：保证台阶不会“提前跳低”。
        // 这样更符合“最大坡度限制”的直觉观感。
        out[0] = baseY[0];
        for (int k = 1; k < n; k++) {
            if (isBridge[k]) {
                out[k] = baseY[k];
                continue;
            }
            double v = y[k];
            int prev = out[k - 1];
            if (v >= prev) {
                out[k] = (int) Math.floor(v + 1e-9);
            } else {
                out[k] = (int) Math.ceil(v - 1e-9);
            }
        }
        return out;
    }

    private static double dist2d(BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
