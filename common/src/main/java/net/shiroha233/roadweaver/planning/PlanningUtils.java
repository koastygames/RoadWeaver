package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;

public final class PlanningUtils {
    private PlanningUtils() {}

    public static long pos2dKey(BlockPos p) {
        long x = p.getX();
        long z = p.getZ();
        return (x << 32) ^ (z & 0xffffffffL);
    }

    public static long edgeKey(BlockPos a, BlockPos b) {
        long ka = pos2dKey(a);
        long kb = pos2dKey(b);
        long lo = Math.min(ka, kb);
        long hi = Math.max(ka, kb);
        return (hi << 1) ^ lo;
    }
}
