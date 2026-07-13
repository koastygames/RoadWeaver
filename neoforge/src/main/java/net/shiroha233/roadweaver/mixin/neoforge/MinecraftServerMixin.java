package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.H2MigrationCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void roadweaver$preloadBeforePrepareLevels(ChunkProgressListener listener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer)(Object)this;
        H2MigrationCoordinator.migrateServer(server);
        if (server.isDedicatedServer()) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }
}
