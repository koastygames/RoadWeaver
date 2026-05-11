package net.shiroha233.roadweaver.planning.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.IdleRoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
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
            boolean highwayMode = ConfigService.get().highway().enabled();
            if (highwayMode) {
                HighwayPlanningService.initialPlanAsync(level);
            } else {
                RoadPlanningService.initialPlanAsync(level);
            }
            return;
        }
        
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) {
            RoadGenerationService.onServerStarted();
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }

        if (ConfigService.get().highway().enabled()) {
            HighwayPlanningService.initialPlanAsync(level);
        }

    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server == null) return;
        
        if ((tick++ % 20) == 0) {
            boolean highwayMode = ConfigService.get().highway().enabled();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                SignTextService.onChunkReady(p.serverLevel(), p.chunkPosition());
                if (highwayMode) {
                    HighwayPlanningService.planAroundPlayer(p);
                } else {
                    RoadPlanningService.planAroundPlayer(p);
                }
                IdleRoadGenerationService.tickPlayer(p);
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (level == null) continue;
            IdleRoadGenerationService.tick(level);
            RoadGenerationService.tick(level);
            SignTextService.tick(level);
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null && ConfigService.get().highway().enabled()) {
            HighwayCellPathPlanningService.tick(overworld);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        RoadGenerationService.onServerStopping();
        HighwayPlanningService.resetAll();
        HighwayCellPathPlanningService.resetAll();
        CacheManager.onServerStopping(event.getServer().getAllLevels());
        ThreadPoolManager.onServerStopping();
        SignTextService.clearPending();
    }
}
