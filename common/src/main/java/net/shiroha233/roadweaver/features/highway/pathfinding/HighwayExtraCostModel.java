package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;

/**
 * Highway 额外成本模型（浮空/穿透）。
 */
public final class HighwayExtraCostModel {
    private HighwayExtraCostModel() {}

    /**
     * 基于“理想高度剖面”的额外成本。
     *
     * 原理：
     * - 以起点/终点的地表高度作为端点高度，做线性插值，得到当前位置的理想高度 refY。
     * - groundY < refY：需要高架/桥梁（浮空），按差值 * floatingWeight 计费。
     * - groundY > refY：需要切割/隧道（穿透），按差值 * penetrationWeight 计费。
     */
    public static double profileCost(BlockPos groundPos,
                                     BlockPos startGround,
                                     BlockPos endGround,
                                     double floatingWeight,
                                     double penetrationWeight) {
        if (groundPos == null || startGround == null || endGround == null) return 0.0;
        if (floatingWeight <= 0 && penetrationWeight <= 0) return 0.0;

        double sx = startGround.getX();
        double sz = startGround.getZ();
        double ex = endGround.getX();
        double ez = endGround.getZ();
        double px = groundPos.getX();
        double pz = groundPos.getZ();

        double dx = ex - sx;
        double dz = ez - sz;
        double lenSq = dx * dx + dz * dz;

        double t;
        if (lenSq <= 1e-9) {
            t = 0.0;
        } else {
            t = ((px - sx) * dx + (pz - sz) * dz) / lenSq;
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;
        }

        double refY = startGround.getY() + t * (endGround.getY() - startGround.getY());
        double y = groundPos.getY();

        double floating = Math.max(0.0, refY - y);
        double penetration = Math.max(0.0, y - refY);

        return floating * Math.max(0.0, floatingWeight) + penetration * Math.max(0.0, penetrationWeight);
    }
}
