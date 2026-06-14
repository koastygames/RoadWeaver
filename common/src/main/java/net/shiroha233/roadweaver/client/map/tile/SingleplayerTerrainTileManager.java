package net.shiroha233.roadweaver.client.map.tile;

import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

/**
 * 单人模式 terrain 瓦片只读调度。
 */
public final class SingleplayerTerrainTileManager extends AbstractSingleplayerTileManager {
    @Override
    protected MapTileLayer layer() {
        return MapTileLayer.TERRAIN;
    }
}