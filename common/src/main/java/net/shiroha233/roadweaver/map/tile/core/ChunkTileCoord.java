package net.shiroha233.roadweaver.map.tile.core;

/**
 * 区块级高精度瓦片坐标（16×16方块 = 16×16像素）。
 */
public record ChunkTileCoord(int chunkX, int chunkZ) {
    public static final int CHUNK_SIZE = 16;
    public static final int TILE_SIZE_PX = 16;

    public static ChunkTileCoord fromBlock(int blockX, int blockZ) {
        return new ChunkTileCoord(blockX >> 4, blockZ >> 4);
    }

    public int minBlockX() { return chunkX * CHUNK_SIZE; }
    public int minBlockZ() { return chunkZ * CHUNK_SIZE; }

    public String fileName() { return chunkZ + ".png"; }
}
