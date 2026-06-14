package net.shiroha233.roadweaver.map.tile.core;

import java.util.ArrayList;
import java.util.List;

/**
 * LOD 缩略瓦片坐标。
 */
public record LodTileCoord(int zoom, int tileX, int tileZ) {
    public LodTileCoord {
        if (zoom < 0) throw new IllegalArgumentException("zoom must be >= 0");
    }

    public static final int TILE_SIZE_PX = 64;
    public static final int BASE_CHUNKS_PER_TILE = 4;
    public static final int MAX_ZOOM = 4;

    public static int chunksPerTile(int zoom) {
        return BASE_CHUNKS_PER_TILE << zoom;
    }

    public static int blocksPerTile(int zoom) {
        return chunksPerTile(zoom) * 16;
    }

    public static int bpp(int zoom) {
        return blocksPerTile(zoom) / TILE_SIZE_PX;
    }

    public static LodTileCoord fromBlock(int zoom, int blockX, int blockZ) {
        int bpt = blocksPerTile(zoom);
        return new LodTileCoord(zoom,
                Math.floorDiv(blockX, bpt),
                Math.floorDiv(blockZ, bpt));
    }

    public static LodTileCoord fromChunk(int zoom, int chunkX, int chunkZ) {
        int cpt = chunksPerTile(zoom);
        return new LodTileCoord(zoom,
                Math.floorDiv(chunkX, cpt),
                Math.floorDiv(chunkZ, cpt));
    }

    public int minChunkX() { return tileX * chunksPerTile(zoom); }
    public int minChunkZ() { return tileZ * chunksPerTile(zoom); }
    public int minBlockX() { return minChunkX() * 16; }
    public int minBlockZ() { return minChunkZ() * 16; }

    public String zoomFolder() { return "z" + zoom; }
    public String fileName() { return tileZ + ".png"; }

    public List<ChunkTileCoord> coveredChunks() {
        int cpt = chunksPerTile(zoom);
        ArrayList<ChunkTileCoord> list = new ArrayList<>(cpt * cpt);
        for (int cz = minChunkZ(); cz < minChunkZ() + cpt; cz++) {
            for (int cx = minChunkX(); cx < minChunkX() + cpt; cx++) {
                list.add(new ChunkTileCoord(cx, cz));
            }
        }
        return list;
    }
}
