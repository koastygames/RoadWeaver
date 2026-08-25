package dev.worldweaver.mixin;

import dev.worldweaver.WorldWeaverSettings;
import dev.worldweaver.WorldgenMetrics;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimizes the expensive, repeatedly-requested base-height path used by
 * structures, features and spawn/placement checks. The cache is per generator
 * instance and includes RandomState identity so it cannot leak answers across
 * world seeds if a generator instance is ever reused.
 *
 * We intentionally do not replace NoiseBasedChunkGenerator or its executor.
 * That keeps biome mods, density-function datapacks, structure mods and other
 * worldgen mixins on the normal Minecraft/NeoForge compatibility path.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Unique
    private final ConcurrentHashMap<WorldWeaverHeightKey, Integer> worldweaver$heightCache = new ConcurrentHashMap<>();

    @Unique
    private final ThreadLocal<Long> worldweaver$terrainStartNanos = new ThreadLocal<>();

    @Inject(method = "getBaseHeight", at = @At("HEAD"), cancellable = true)
    private void worldweaver$getCachedBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor level,
            RandomState randomState,
            CallbackInfoReturnable<Integer> cir) {
        if (!WorldWeaverSettings.HEIGHT_CACHE_ENABLED) {
            return;
        }

        WorldWeaverHeightKey key = new WorldWeaverHeightKey(
                x,
                z,
                heightmapType,
                System.identityHashCode(randomState));
        Integer cached = worldweaver$heightCache.get(key);
        if (cached != null) {
            WorldgenMetrics.cacheHit();
            cir.setReturnValue(cached);
        } else {
            WorldgenMetrics.cacheMiss();
        }
    }

    @Inject(method = "getBaseHeight", at = @At("RETURN"))
    private void worldweaver$rememberBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor level,
            RandomState randomState,
            CallbackInfoReturnable<Integer> cir) {
        if (!WorldWeaverSettings.HEIGHT_CACHE_ENABLED) {
            return;
        }

        if (worldweaver$heightCache.size() >= WorldWeaverSettings.HEIGHT_CACHE_MAX_ENTRIES) {
            worldweaver$trimHeightCache();
        }

        WorldWeaverHeightKey key = new WorldWeaverHeightKey(
                x,
                z,
                heightmapType,
                System.identityHashCode(randomState));
        worldweaver$heightCache.putIfAbsent(key, cir.getReturnValue());
    }

    @Unique
    private void worldweaver$trimHeightCache() {
        synchronized (worldweaver$heightCache) {
            if (worldweaver$heightCache.size() >= WorldWeaverSettings.HEIGHT_CACHE_MAX_ENTRIES) {
                worldweaver$heightCache.clear();
            }
        }
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"))
    private void worldweaver$terrainBegin(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        worldweaver$terrainStartNanos.set(System.nanoTime());
    }

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void worldweaver$terrainEnd(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        Long started = worldweaver$terrainStartNanos.get();
        worldweaver$terrainStartNanos.remove();
        CompletableFuture<ChunkAccess> original = cir.getReturnValue();
        if (started == null || original == null) {
            return;
        }

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        cir.setReturnValue(original.whenComplete((result, error) ->
                WorldgenMetrics.recordTerrain(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ),
                        System.nanoTime() - started,
                        error)));
    }

    @Unique
    private record WorldWeaverHeightKey(int x, int z, Heightmap.Types type, int randomStateIdentity) {
    }
}
