package net.shiroha233.roadweaver.features.roadlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.search.StructurePredictor;

import java.util.List;

/**
 * 专门负责根据结构类型决定道路端点需要从结构点向外缩进多少格。
 * 
 * 职责：
 * - 检测给定端点附近是否属于特定结构（目前主要是原版村庄）
 * - 按结构类别返回一个“缩进距离”（单位：方块）
 * - 根据缩进距离和连接方向，计算新的道路起点/终点坐标
 */
public final class StructureRoadOffsetService {
    private StructureRoadOffsetService() {
    }

    private enum StructureCategory {
        VILLAGE,
        OTHER,
        UNKNOWN
    }

    // 村庄默认缩进：距离结构中心约 60 格以内不直接通到中心
    private static final int VILLAGE_OFFSET_BLOCKS = 60;

    // 当坐标与预测结构点不完全重合时，允许的匹配容差（半径，单位：方块）
    private static final int MATCH_TOLERANCE_BLOCKS = 16;

    /**
     * 计算单个端点在考虑结构缩进后的新位置。
     *
     * @param level    世界
     * @param endpoint 原始结构端点（结构锚点或结构中心）
     * @param otherEnd 连接另一端的端点（用于确定缩进方向）
     * @return 调整后的端点坐标；如无需调整则返回原端点
     */
    public static BlockPos adjustEndpoint(ServerLevel level, BlockPos endpoint, BlockPos otherEnd) {
        if (endpoint == null || otherEnd == null) return endpoint;
        if (!Level.OVERWORLD.equals(level.dimension())) return endpoint;

        int offset = getOffsetBlocksForEndpoint(level, endpoint);
        if (offset <= 0) return endpoint;

        long dx = (long) otherEnd.getX() - endpoint.getX();
        long dz = (long) otherEnd.getZ() - endpoint.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (len == 0.0) return endpoint;

        double nx = dx / len;
        double nz = dz / len;
        int ox = (int) Math.round(nx * offset);
        int oz = (int) Math.round(nz * offset);
        return endpoint.offset(ox, 0, oz);
    }

    private static int getOffsetBlocksForEndpoint(ServerLevel level, BlockPos endpoint) {
        StructureCategory cat = detectCategory(level, endpoint);
        return switch (cat) {
            case VILLAGE -> VILLAGE_OFFSET_BLOCKS;
            default -> 0;
        };
    }

    private static StructureCategory detectCategory(ServerLevel level, BlockPos endpoint) {
        if (!Level.OVERWORLD.equals(level.dimension())) return StructureCategory.UNKNOWN;

        // 只在当前端点所在区块附近做一次小范围结构预测，避免大范围扫描
        int cx = endpoint.getX() >> 4;
        int cz = endpoint.getZ() >> 4;

        List<Records.StructureInfo> infos = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cx, cz,
                cx, cz,
                true, // 复用原有生物群系预筛逻辑
                java.util.List.of("#minecraft:village"),
                java.util.List.of()
        );
        if (infos == null || infos.isEmpty()) return StructureCategory.UNKNOWN;

        long tol2 = (long) MATCH_TOLERANCE_BLOCKS * (long) MATCH_TOLERANCE_BLOCKS;
        for (Records.StructureInfo info : infos) {
            BlockPos p = info.pos();
            long dx = (long) p.getX() - endpoint.getX();
            long dz = (long) p.getZ() - endpoint.getZ();
            long d2 = dx * dx + dz * dz;
            if (d2 <= tol2) {
                // 目前只区分“村庄”与其他，后续可以在此扩展更多类别
                return StructureCategory.VILLAGE;
            }
        }
        return StructureCategory.UNKNOWN;
    }
}
