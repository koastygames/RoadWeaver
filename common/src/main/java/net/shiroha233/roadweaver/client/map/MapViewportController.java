package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.tile.SingleplayerTerrainTileManager;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoi;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoiLocator;

import java.util.function.Consumer;

/**
 * 地图视口请求协调。
 */
public final class MapViewportController {
    private static final int SNAPSHOT_PADDING_BLOCKS = 32;

    private MapViewportController() {}

    public record RequestRect(int minX, int minZ, int maxX, int maxZ) {}

    public static RequestRect currentRequestRect(MapView view) {
        return new RequestRect(
                (int) Math.floor(Math.min(view.getMinX(), view.getMaxX())) - SNAPSHOT_PADDING_BLOCKS,
                (int) Math.floor(Math.min(view.getMinZ(), view.getMaxZ())) - SNAPSHOT_PADDING_BLOCKS,
                (int) Math.ceil(Math.max(view.getMinX(), view.getMaxX())) + SNAPSHOT_PADDING_BLOCKS,
                (int) Math.ceil(Math.max(view.getMinZ(), view.getMaxZ())) + SNAPSHOT_PADDING_BLOCKS);
    }

    public static ResourceLocation syncDimensionAndRestoreCache(Minecraft mc,
                                                                ResourceLocation currentDimensionId,
                                                                Consumer<MapSnapshot> onCachedSnapshot) {
        ResourceLocation nextDimensionId = mc != null && mc.level != null ? mc.level.dimension().location() : null;
        if (nextDimensionId == null) {
            return currentDimensionId;
        }
        if (currentDimensionId == null || !nextDimensionId.equals(currentDimensionId)) {
            MapSnapshot cached = MapSnapshotCache.peek(nextDimensionId);
            if (cached != null && onCachedSnapshot != null) {
                onCachedSnapshot.accept(cached);
            }
            return nextDimensionId;
        }
        return currentDimensionId;
    }

    public static ServerLevel resolveSingleplayerLevel(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return null;
        return server.getLevel(mc.level.dimension());
    }

    public static void requestTerrainTiles(Minecraft mc,
                                           SingleplayerTerrainTileManager terrainTiles,
                                           MapView view,
                                           int contentW,
                                           int contentH) {
        if (mc == null || terrainTiles == null || view == null) return;
        ServerLevel level = resolveSingleplayerLevel(mc);
        if (level == null) return;

        int cx = mc.player != null ? (int) Math.round(mc.player.getX()) : 0;
        int cz = mc.player != null ? (int) Math.round(mc.player.getZ()) : 0;
        int radiusBlocks = MapTileAoiLocator.dynamicPlanningRadiusBlocks();
        MapTileAoi aoi = new MapTileAoi(level.dimension().location(), cx, cz, radiusBlocks);
        terrainTiles.request(level, aoi, view, contentW, contentH);
    }
}