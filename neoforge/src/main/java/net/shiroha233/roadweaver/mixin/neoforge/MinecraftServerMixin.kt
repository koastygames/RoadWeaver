package net.shiroha233.roadweaver.mixin.neoforge

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.progress.ChunkProgressListener
import net.shiroha233.roadweaver.generation.InitialGenManager
import net.shiroha233.roadweaver.generation.RoadGenerationService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(MinecraftServer::class)
abstract class MinecraftServerMixin {

    @Inject(method = ["prepareLevels"], at = [At("HEAD")])
    private fun `roadweaver$preloadBeforePrepareLevels`(listener: ChunkProgressListener, ci: CallbackInfo) {
        val server = this as MinecraftServer
        if (server.isDedicatedServer) return

        val level = server.overworld()
        val conns: kotlin.collections.List<Records.StructureConnection>? =
            level?.let { WorldDataProvider.getInstance().getStructureConnections(it) }

        if (conns.isNullOrEmpty()) {
            if (level != null) {
                InitialGenManager.begin(level)
                InitialGenManager.blockUntilDone(level)
            }
        } else {
            RoadGenerationService.onServerStarted()
        }
    }
}
