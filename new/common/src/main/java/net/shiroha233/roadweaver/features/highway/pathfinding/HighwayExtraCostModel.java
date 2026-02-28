package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;

/**
 * Highway 额外成本模型
 * 职责：计算高速公路的浮空成本和障碍穿透成本
 */
public final class HighwayExtraCostModel {
    private HighwayExtraCostModel() {}

    /**
     * 基于理想高度剖面计算额外成本
     * 原理：线性插值起终点高度得到理想高度，与实际地表高度比较
     * - 地表低于理想高度：需要高架/桥梁（浮空成本）
     * - 地表高于理想高度：需要切割/隧道（穿透成本）
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
