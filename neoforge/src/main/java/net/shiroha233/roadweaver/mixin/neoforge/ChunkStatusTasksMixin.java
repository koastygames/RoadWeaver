package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.shiroha233.roadweaver.structures.precompute.StructureInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Mixin 钩入区块的 STRUCTURE_STARTS 阶段
 * 
 * 在原版结构生成完成后，注入预计算的路边结构。
 * 这样 Beardifier 在噪声生成阶段可以检测到这些结构并自动进行地形适应。
 */
@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    
    /**
     * 在 generateStructureStarts 方法返回之前注入路边结构
     */
    @Inject(
        method = "generateStructureStarts",
        at = @At("RETURN")
    )
    private static void roadweaver$injectRoadsideStructures(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        ServerLevel level = context.level();
        StructureInjector.injectPendingStructures(level, chunk);
    }
}
