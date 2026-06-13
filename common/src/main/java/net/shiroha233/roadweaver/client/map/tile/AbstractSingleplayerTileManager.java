package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.client.map.localoverride.ClientMapOverrideResolver;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;

import java.nio.file.Path;
/**
 * 单人模式瓦片调度公共骨架。
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
        for (MapTileCoord coord : visibleRect.coords()) {
            Path path = ClientMapOverrideResolver.resolveOrNull(
                    dimensionId,
                    layer(),
                    coord,
                    ServerMapTileStorage.path(level, layer(), coord));
            ResourceLocation texture = ClientMapTileTextureCache.getOrLoad(mc, path);
            if (texture == null) {
                continue;
            }
            lastRenderHadTexture = true;
            int worldMinX = MapTileScheme.tileMinBlockX(coord);
            int worldMinZ = MapTileScheme.tileMinBlockZ(coord);
            int worldMaxExclusiveX = worldMinX + MapTileScheme.blocksPerTile(coord.zoom());
            int worldMaxExclusiveZ = worldMinZ + MapTileScheme.blocksPerTile(coord.zoom());
            int x0 = view.toScreenX(worldMinX, mapX, 0, contentW);
            int y0 = view.toScreenY(worldMinZ, mapY, 0, contentH);
            int x1 = view.toScreenX(worldMaxExclusiveX, mapX, 0, contentW);
            int y1 = view.toScreenY(worldMaxExclusiveZ, mapY, 0, contentH);
            int drawX = Math.min(x0, x1);
            int drawY = Math.min(y0, y1);
            int drawW = Math.max(1, Math.abs(x1 - x0));
            int drawH = Math.max(1, Math.abs(y1 - y0));
            g.blit(texture,
                    drawX,
                    drawY,
                    drawW,
                    drawH,
                    0.0F,
                    0.0F,
                    MapTileScheme.TILE_SIZE_PX,
                    MapTileScheme.TILE_SIZE_PX,
                    MapTileScheme.TILE_SIZE_PX,
                    MapTileScheme.TILE_SIZE_PX);
        }
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

    private static int chooseZoom(MapView view, int contentW, int contentH) {
        return MapTileScheme.MIN_ZOOM;
    }
}