package net.shiroha233.roadweaver.planning.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.IdleRoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.H2MigrationCoordinator;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.List;

/**
 * Forge 服务端规划钩子
 */
public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        H2MigrationCoordinator.migrateServer(event.getServer());
        CacheManager.onServerStarted();
        ThreadPoolManager.onServerStarted(event.getServer());
        SignTextService.clearPending();
        IdleRoadGenerationService.onServerStarted();
        
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;

        StructureDiscoveryService.discoverFromLevel(level);

        boolean dedicated = event.getServer().isDedicatedServer();
        if (dedicated) {
            RoadGenerationService.onServerStarted();
            if (InitialGenManager.shouldRunInitialGeneration(level)) {
                RoadPlanningService.initialPlanAsync(level);
            }
            return;
        }
        
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            RoadGenerationService.onServerStarted();
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server == null) return;
        
        if ((tick++ % 20) == 0) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (Level.OVERWORLD.equals(p.serverLevel().dimension())) {
                        SignTextService.onChunkReady(p.serverLevel(), p.chunkPosition());
                        RoadPlanningService.planAroundPlayer(p);
                        IdleRoadGenerationService.tickPlayer(p);
                    }
                }
            }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level != null) {
            IdleRoadGenerationService.tick(level);
            RoadGenerationService.tick(level);
            SignTextService.tick(level);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        RoadGenerationService.onServerStopping();
        RoadPlanningService.resetAll();
        CacheManager.onServerStopping(event.getServer().getAllLevels());
        ThreadPoolManager.onServerStopping();
        SignTextService.clearPending();
    }
}
