package dev.worldweaver.mixin;

import dev.worldweaver.WorldgenMetrics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes the two structure-related parts of the vanilla-compatible pipeline.
 *
 * createStructures creates structure starts/metadata early because terrain
 * adaptation, locate, jigsaw and references depend on it. It does NOT place the
 * structure blocks. Actual structure block placement is performed from
 * applyBiomeDecoration after terrain/noise, surface and carving have completed.
 * Preserving this split is what makes "terrain first, structures layered on
 * top" compatible with the existing worldgen ecosystem.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Unique
    private final ThreadLocal<Long> worldweaver$decorationStartNanos = new ThreadLocal<>();

    @Unique
    private final ThreadLocal<Long> worldweaver$structurePlanStartNanos = new ThreadLocal<>();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void worldweaver$decorationBegin(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci) {
        worldweaver$decorationStartNanos.set(System.nanoTime());
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void worldweaver$decorationEnd(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci) {
        Long started = worldweaver$decorationStartNanos.get();
        worldweaver$decorationStartNanos.remove();
        if (started != null) {
            WorldgenMetrics.recordDecoration(chunk.getPos(), System.nanoTime() - started);
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"))
    private void worldweaver$structurePlanBegin(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            CallbackInfo ci) {
        worldweaver$structurePlanStartNanos.set(System.nanoTime());
    }

    @Inject(method = "createStructures", at = @At("RETURN"))
    private void worldweaver$structurePlanEnd(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            CallbackInfo ci) {
        Long started = worldweaver$structurePlanStartNanos.get();
        worldweaver$structurePlanStartNanos.remove();
        if (started != null) {
            WorldgenMetrics.recordStructurePlanning(chunk.getPos(), System.nanoTime() - started);
        }
    }
}
