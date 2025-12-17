package net.shiroha233.roadweaver.mixin.fabric

import net.minecraft.server.level.GenerationChunkHolder
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatusTasks
import net.minecraft.world.level.chunk.status.ChunkStep
import net.minecraft.world.level.chunk.status.WorldGenContext
import net.minecraft.util.StaticCache2D
import net.shiroha233.roadweaver.structures.precompute.StructureInjector
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.concurrent.CompletableFuture

@Mixin(ChunkStatusTasks::class)
class ChunkStatusTasksMixin {

    private companion object {
        @JvmStatic
        @Inject(method = ["generateStructureStarts"], at = [At("RETURN")])
        private fun `roadweaver$injectRoadsideStructures`(
            context: WorldGenContext,
            step: ChunkStep,
            cache: StaticCache2D<GenerationChunkHolder>,
            chunk: ChunkAccess,
            cir: CallbackInfoReturnable<CompletableFuture<ChunkAccess>>
        ) {
            val level = context.level()
            StructureInjector.injectPendingStructures(level, chunk)
        }
    }
}
