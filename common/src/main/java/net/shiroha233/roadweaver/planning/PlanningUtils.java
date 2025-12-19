package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.helpers.Records;

/**
 * 道路规划工具类
 * 提供边键生成、边比较等通用方法
 */
public final class PlanningUtils {
    private PlanningUtils() {}

    /**
     * 生成二维位置键（忽略 Y 坐标）
     */
    public static long pos2dKey(BlockPos p) {
        long x = p.getX();
        long z = p.getZ();
        return (x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 生成边键（无向边，a-b 与 b-a 生成相同的键）
     */
    public static long edgeKey(BlockPos a, BlockPos b) {
        long ka = pos2dKey(a);
        long kb = pos2dKey(b);
        long lo = Math.min(ka, kb);
        long hi = Math.max(ka, kb);
        return (hi << 1) ^ lo;
    }

    /**
     * 判断两个连接是否代表同一条边（无向边比较）
     */
    public static boolean sameEdge(Records.StructureConnection a, Records.StructureConnection b) {
        BlockPos af = a.from(), at = a.to();
        BlockPos bf = b.from(), bt = b.to();
        return (af.equals(bf) && at.equals(bt)) || (af.equals(bt) && at.equals(bf));
    }

    /**
     * 判断连接是否与给定的两个端点匹配（无向边比较）
     */
    public static boolean sameEdge(Records.StructureConnection c, BlockPos a, BlockPos b) {
        BlockPos cf = c.from();
        BlockPos ct = c.to();
        return (cf.equals(a) && ct.equals(b)) || (cf.equals(b) && ct.equals(a));
    }
}
