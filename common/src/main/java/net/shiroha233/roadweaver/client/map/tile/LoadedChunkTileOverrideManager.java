package net.shiroha233.roadweaver.client.map.tile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.localoverride.ClientMapOverrideStorage;
import net.shiroha233.roadweaver.map.tile.core.ChunkTileCoord;
import net.shiroha233.roadweaver.map.tile.core.LodTileCoord;
import net.shiroha233.roadweaver.map.tile.render.HiResTileRenderer;
import net.shiroha233.roadweaver.util.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块瓦片生成管理器。
 */
public final class LoadedChunkTileOverrideManager {
    private LoadedChunkTileOverrideManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> PENDING = new ConcurrentHashMap<>();

    public static void onChunkGenerated(ClientLevel clientLevel, ServerLevel serverLevel, int chunkX, int chunkZ) {
        if (clientLevel == null || serverLevel == null) return;

        ResourceLocation dimId = clientLevel.dimension().location();
        String key = dimId + "_chunk_" + chunkX + "_" + chunkZ;
        if (PENDING.containsKey(key)) return;

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> generateAll(clientLevel, serverLevel, dimId, chunkX, chunkZ),
                ComputeService.mapExecutor()
        ).thenRun(() -> {
            PENDING.remove(key);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> invalidateChunkTextures(mc, serverLevel, dimId, chunkX, chunkZ));
        });

        PENDING.put(key, future);
    }

    public static void clear() {
        Iterator<Map.Entry<String, CompletableFuture<Void>>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CompletableFuture<Void>> entry = it.next();
            entry.getValue().cancel(false);
            it.remove();
        }
    }

    private static void generateAll(ClientLevel clientLevel, ServerLevel serverLevel,
                                     ResourceLocation dimId, int chunkX, int chunkZ) {
        if (Thread.currentThread().isInterrupted()) return;

        ChunkTileCoord chunkCoord = new ChunkTileCoord(chunkX, chunkZ);
        BufferedImage chunkImage = HiResTileRenderer.renderChunk(clientLevel, chunkCoord);
        if (chunkImage == null) return;

        ClientMapOverrideStorage.writeChunkTile(serverLevel, dimId, chunkCoord, chunkImage);

        Path chunkPath = ClientMapOverrideStorage.chunkTilePath(serverLevel, dimId, chunkCoord);
        for (int zoom = 0; zoom <= LodTileCoord.MAX_ZOOM; zoom++) {
            LodTileCoord lodCoord = LodTileCoord.fromChunk(zoom, chunkX, chunkZ);
            generateLod(serverLevel, dimId, lodCoord, chunkPath);
        }
    }

    private static void generateLod(ServerLevel serverLevel, ResourceLocation dimId,
                                     LodTileCoord lodCoord, Path newChunkPath) {
        List<ChunkTileCoord> chunks = lodCoord.coveredChunks();
        List<Path> chunkPaths = new ArrayList<>(chunks.size());
        for (ChunkTileCoord chunk : chunks) {
            Path p = ClientMapOverrideStorage.chunkTilePath(serverLevel, dimId, chunk);
            chunkPaths.add(Files.exists(p) ? p : null);
        }

        BufferedImage lodImage = HiResTileRenderer.downsample(
                lodCoord.zoom(), chunkPaths, LodTileCoord.chunksPerTile(lodCoord.zoom()));

        boolean hasContent = false;
        for (int y = 0; y < lodImage.getHeight() && !hasContent; y++) {
            for (int x = 0; x < lodImage.getWidth(); x++) {
                if (lodImage.getRGB(x, y) != 0) { hasContent = true; break; }
            }
        }
        if (hasContent) {
            ClientMapOverrideStorage.writeLodTile(serverLevel, dimId, lodCoord, lodImage);
        }
    }

    private static void invalidateChunkTextures(Minecraft mc, ServerLevel serverLevel,
                                                 ResourceLocation dimId, int chunkX, int chunkZ) {
        ChunkTileCoord chunkCoord = new ChunkTileCoord(chunkX, chunkZ);
        Path chunkPath = ClientMapOverrideStorage.chunkTilePath(serverLevel, dimId, chunkCoord);
        ClientMapTileTextureCache.invalidate(mc, chunkPath);

        for (int zoom = 0; zoom <= LodTileCoord.MAX_ZOOM; zoom++) {
            LodTileCoord lodCoord = LodTileCoord.fromChunk(zoom, chunkX, chunkZ);
            Path lodPath = ClientMapOverrideStorage.lodTilePath(serverLevel, dimId, lodCoord);
            ClientMapTileTextureCache.invalidate(mc, lodPath);
        }
    }
}