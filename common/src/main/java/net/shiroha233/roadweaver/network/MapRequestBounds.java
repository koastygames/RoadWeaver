package net.shiroha233.roadweaver.network;

import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 地图请求范围裁剪。
 */
public final class MapRequestBounds {
    private MapRequestBounds() {}

    public record Rect(int minX, int minZ, int maxX, int maxZ, int radiusBlocks) {}

    public static Rect clampToPlayer(ServerPlayer player, int minX, int minZ, int maxX, int maxZ) {
        int radiusBlocks = resolveRadiusBlocks();
        int cx = player != null ? (int) Math.round(player.getX()) : 0;
        int cz = player != null ? (int) Math.round(player.getZ()) : 0;
        int reqMinX = Math.min(minX, maxX);
        int reqMaxX = Math.max(minX, maxX);
        int reqMinZ = Math.min(minZ, maxZ);
        int reqMaxZ = Math.max(minZ, maxZ);
        int allowedMinX = cx - radiusBlocks;
        int allowedMaxX = cx + radiusBlocks;
        int allowedMinZ = cz - radiusBlocks;
        int allowedMaxZ = cz + radiusBlocks;
        int outMinX = Math.max(reqMinX, allowedMinX);
        int outMinZ = Math.max(reqMinZ, allowedMinZ);
        int outMaxX = Math.min(reqMaxX, allowedMaxX);
        int outMaxZ = Math.min(reqMaxZ, allowedMaxZ);
        if (outMinX > outMaxX || outMinZ > outMaxZ) {
            outMinX = cx - RoadConstants.CHUNK_SIZE_BLOCKS;
            outMaxX = cx + RoadConstants.CHUNK_SIZE_BLOCKS;
            outMinZ = cz - RoadConstants.CHUNK_SIZE_BLOCKS;
            outMaxZ = cz + RoadConstants.CHUNK_SIZE_BLOCKS;
        }
        return new Rect(outMinX, outMinZ, outMaxX, outMaxZ, radiusBlocks);
    }

    public static int resolveRadiusBlocks() {
        try {
            var cfg = ConfigService.get();
            int chunks;
            if (cfg.highway().enabled()) {
                chunks = Math.max(1, Math.floorDiv(Math.max(16, cfg.highway().planningRadiusBlocks()), RoadConstants.CHUNK_SIZE_BLOCKS));
            } else {
                chunks = cfg.planning().dynamicPlanEnabled()
                        ? cfg.planning().dynamicPlanRadiusChunks()
                        : cfg.planning().initialPlanRadiusChunks();
            }
            chunks = Math.max(1, Math.min(RoadConstants.COARSE_REGION_MAX_RADIUS_CHUNKS, chunks));
            return chunks * RoadConstants.CHUNK_SIZE_BLOCKS;
        } catch (Throwable ignored) {
            return RoadConstants.DEFAULT_DYNAMIC_PLAN_RADIUS_CHUNKS * RoadConstants.CHUNK_SIZE_BLOCKS;
        }
    }
}