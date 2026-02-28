package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.shiroha233.roadweaver.structures.precompute.StructureInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 钩入区块的 STRUCTURE_STARTS 阶段
 * 职责：在原版结构生成完成后注入预计算的路边结构
 */
@Mixin(ServerLevel.class)
public class ServerLevelStructureMixin {
    
    @Inject(
        method = "onStructureStartsAvailable",
        at = @At("HEAD")
    )
    private void roadweaver$injectRoadsideStructures(ChunkAccess chunk, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        StructureInjector.injectPendingStructures(level, chunk);
    }
}
