package net.shiroha233.roadweaver.map.tile.core;

/**
 * 瓦片图层类型。
 */
public enum MapTileLayer {
    TERRAIN("terrain"),
    OVERLAY("overlay");

    private final String folderName;

    MapTileLayer(String folderName) {
        this.folderName = folderName;
    }

    public String folderName() {
        return folderName;
    }
}