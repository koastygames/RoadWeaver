package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.client.map.localoverride.ClientMapOverrideResolver;
import net.shiroha233.roadweaver.map.tile.core.ChunkTileCoord;
import net.shiroha233.roadweaver.map.tile.core.LodTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoi;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.render.HiResTileRenderer;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 单人模式瓦片调度器。
 */
public abstract class AbstractSingleplayerTileManager {
    private ServerLevel level;
    private MapTileRect visibleRect;
    private int zoom;
    private boolean lastRenderHadTexture;

    public synchronized void request(ServerLevel level, MapTileAoi aoi, MapView view, int contentW, int contentH) {
        if (level == null || view == null || contentW <= 0 || contentH <= 0) {
            return;
        }
        int nextZoom = chooseZoom(view, contentW, contentH);
        MapTileRect viewRect = MapTileScheme.tileRectForBlockRect(
                nextZoom,
                (int) Math.floor(view.getMinX()),
                (int) Math.floor(view.getMinZ()),
                (int) Math.ceil(view.getMaxX()),
                (int) Math.ceil(view.getMaxZ()));
        this.level = level;
        this.visibleRect = viewRect;
        this.zoom = nextZoom;
    }

    public void render(GuiGraphics g,
                       Minecraft mc,
                       MapView view,
                       int mapX,
                       int mapY,
                       int contentW,
                       int contentH) {
        if (mc == null || view == null || level == null || visibleRect == null) {
            lastRenderHadTexture = false;
            return;
        }
        lastRenderHadTexture = false;
        ResourceLocation dimensionId = level.dimension().location();
        Set<String> viewportKeys = new HashSet<>();

        for (MapTileCoord coord : visibleRect.coords()) {
            Path path = ClientMapOverrideResolver.resolveOrNull(
                    dimensionId,
                    layer(),
                    coord,
                    ServerMapTileStorage.path(level, layer(), coord));
            if (path == null) continue;

            String key = path.toAbsolutePath().normalize().toString();
            viewportKeys.add(key);

            ResourceLocation texture = ClientMapTileTextureCache.getOrLoad(mc, path, true);
            if (texture == null) continue;

            lastRenderHadTexture = true;
            blitTile(g, texture, coord, view, mapX, mapY, contentW, contentH);
        }

        double blocksPerPixel = 1.0 / Math.min(view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH));
        renderHiResOverlay(g, mc, level, view, dimensionId, mapX, mapY, contentW, contentH, viewportKeys, blocksPerPixel);

        ClientMapTileTextureCache.trimToViewport(mc, viewportKeys);
    }

    public int zoom() {
        return zoom;
    }

    public MapTileRect visibleRect() {
        return visibleRect;
    }

    public boolean hasRenderableTiles() {
        return lastRenderHadTexture;
    }

    public synchronized void clear() {
        this.level = null;
        this.visibleRect = null;
        this.zoom = 0;
        this.lastRenderHadTexture = false;
    }

    protected abstract MapTileLayer layer();

    private void renderHiResOverlay(GuiGraphics g, Minecraft mc, ServerLevel level,
                                     MapView view, ResourceLocation dimensionId,
                                     int mapX, int mapY, int contentW, int contentH,
                                     Set<String> viewportKeys, double blocksPerPixel) {
        if (blocksPerPixel <= 1.0) {
            renderChunkTiles(g, mc, level, view, dimensionId, mapX, mapY, contentW, contentH, viewportKeys);
        } else {
            renderLodTiles(g, mc, level, view, dimensionId, mapX, mapY, contentW, contentH, viewportKeys, blocksPerPixel);
        }
    }

    private void renderChunkTiles(GuiGraphics g, Minecraft mc, ServerLevel level,
                                   MapView view, ResourceLocation dimensionId,
                                   int mapX, int mapY, int contentW, int contentH,
                                   Set<String> viewportKeys) {
        int viewMinX = (int) Math.floor(view.getMinX());
        int viewMinZ = (int) Math.floor(view.getMinZ());
        int viewMaxX = (int) Math.ceil(view.getMaxX());
        int viewMaxZ = (int) Math.ceil(view.getMaxZ());

        int startCX = viewMinX >> 4;
        int startCZ = viewMinZ >> 4;
        int endCX = viewMaxX >> 4;
        int endCZ = viewMaxZ >> 4;

        int tileSizePx = HiResTileRenderer.CHUNK_SIZE;

        for (int cz = startCZ; cz <= endCZ; cz++) {
            for (int cx = startCX; cx <= endCX; cx++) {
                ChunkTileCoord chunkCoord = new ChunkTileCoord(cx, cz);
                Path path = ClientMapOverrideResolver.resolveChunkTileOrNull(level, dimensionId, chunkCoord);
                if (path == null) continue;

                String key = path.toAbsolutePath().normalize().toString();
                viewportKeys.add(key);

                ResourceLocation texture = ClientMapTileTextureCache.getOrLoad(mc, path, true);
                if (texture == null) continue;

                lastRenderHadTexture = true;
                blitWorldRect(g, texture, cx * 16, cz * 16, cx * 16 + 16, cz * 16 + 16,
                        view, mapX, mapY, contentW, contentH, tileSizePx);
            }
        }
    }

    private void renderLodTiles(GuiGraphics g, Minecraft mc, ServerLevel level,
                                 MapView view, ResourceLocation dimensionId,
                                 int mapX, int mapY, int contentW, int contentH,
                                 Set<String> viewportKeys, double blocksPerPixel) {
        int viewMinX = (int) Math.floor(view.getMinX());
        int viewMinZ = (int) Math.floor(view.getMinZ());
        int viewMaxX = (int) Math.ceil(view.getMaxX());
        int viewMaxZ = (int) Math.ceil(view.getMaxZ());

        int lodZoom = chooseLodZoom(blocksPerPixel);
        int blocksPerTile = LodTileCoord.blocksPerTile(lodZoom);
        int tileSizePx = LodTileCoord.TILE_SIZE_PX;

        LodTileCoord startCoord = LodTileCoord.fromBlock(lodZoom, viewMinX, viewMinZ);
        LodTileCoord endCoord = LodTileCoord.fromBlock(lodZoom, viewMaxX, viewMaxZ);

        for (int tz = startCoord.tileZ(); tz <= endCoord.tileZ(); tz++) {
            for (int tx = startCoord.tileX(); tx <= endCoord.tileX(); tx++) {
                LodTileCoord lodCoord = new LodTileCoord(lodZoom, tx, tz);
                Path path = ClientMapOverrideResolver.resolveLodTileOrNull(level, dimensionId, lodCoord);
                if (path == null) continue;

                String key = path.toAbsolutePath().normalize().toString();
                viewportKeys.add(key);

                ResourceLocation texture = ClientMapTileTextureCache.getOrLoad(mc, path, true);
                if (texture == null) continue;

                lastRenderHadTexture = true;
                blitWorldRect(g, texture, lodCoord.minBlockX(), lodCoord.minBlockZ(),
                        lodCoord.minBlockX() + blocksPerTile, lodCoord.minBlockZ() + blocksPerTile,
                        view, mapX, mapY, contentW, contentH, tileSizePx);
            }
        }
    }

    private static int chooseLodZoom(double blocksPerPixel) {
        for (int zoom = 0; zoom <= LodTileCoord.MAX_ZOOM; zoom++) {
            if (LodTileCoord.bpp(zoom) >= blocksPerPixel) {
                return zoom;
            }
        }
        return LodTileCoord.MAX_ZOOM;
    }

    private void blitWorldRect(GuiGraphics g, ResourceLocation texture,
                                int worldMinX, int worldMinZ, int worldMaxX, int worldMaxZ,
                                MapView view, int mapX, int mapY, int contentW, int contentH,
                                int tileSizePx) {
        int x0 = view.toScreenX(worldMinX, mapX, 0, contentW);
        int y0 = view.toScreenY(worldMinZ, mapY, 0, contentH);
        int x1 = view.toScreenX(worldMaxX, mapX, 0, contentW);
        int y1 = view.toScreenY(worldMaxZ, mapY, 0, contentH);
        int drawX = Math.min(x0, x1);
        int drawY = Math.min(y0, y1);
        int drawW = Math.max(1, Math.abs(x1 - x0));
        int drawH = Math.max(1, Math.abs(y1 - y0));
        g.blit(texture, drawX, drawY, drawW, drawH,
                0.0F, 0.0F, tileSizePx, tileSizePx, tileSizePx, tileSizePx);
    }

    private static void blitTile(GuiGraphics g, ResourceLocation texture, MapTileCoord coord,
                                  MapView view, int mapX, int mapY, int contentW, int contentH) {
        int worldMinX = MapTileScheme.tileMinBlockX(coord);
        int worldMinZ = MapTileScheme.tileMinBlockZ(coord);
        int worldMaxX = worldMinX + MapTileScheme.blocksPerTile(coord.zoom());
        int worldMaxZ = worldMinZ + MapTileScheme.blocksPerTile(coord.zoom());
        int x0 = view.toScreenX(worldMinX, mapX, 0, contentW);
        int y0 = view.toScreenY(worldMinZ, mapY, 0, contentH);
        int x1 = view.toScreenX(worldMaxX, mapX, 0, contentW);
        int y1 = view.toScreenY(worldMaxZ, mapY, 0, contentH);
        int drawX = Math.min(x0, x1);
        int drawY = Math.min(y0, y1);
        int drawW = Math.max(1, Math.abs(x1 - x0));
        int drawH = Math.max(1, Math.abs(y1 - y0));
        g.blit(texture, drawX, drawY, drawW, drawH,
                0.0F, 0.0F,
                MapTileScheme.TILE_SIZE_PX, MapTileScheme.TILE_SIZE_PX,
                MapTileScheme.TILE_SIZE_PX, MapTileScheme.TILE_SIZE_PX);
    }

    private static int chooseZoom(MapView view, int contentW, int contentH) {
        double ppbX = view.pxPerBlockX(contentW);
        double ppbZ = view.pxPerBlockZ(contentH);
        double ppb = Math.min(ppbX, ppbZ);
        if (ppb <= 0) return MapTileScheme.MIN_ZOOM;
        double blocksPerPixel = 1.0 / ppb;
        return MapTileScheme.zoomForBlocksPerPixel(blocksPerPixel);
    }
}