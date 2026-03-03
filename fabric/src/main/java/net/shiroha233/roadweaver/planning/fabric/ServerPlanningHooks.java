package net.shiroha233.roadweaver.planning.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.features.longdrive.planning.LongDrivePlanningService;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.List;

/**
 * Fabric 服务端规划钩子
 */
public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CacheManager.onServerStarted();
            ThreadPoolManager.onServerStarted(server);
            SignTextService.clearPending();
            
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return;

            StructureDiscoveryService.discoverFromLevel(level);

            boolean dedicated = server.isDedicatedServer();
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

            if (ConfigService.get().longDrive().enabled()) {
                LongDrivePlanningService.initialPlan(level);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((tick++ % 20) == 0) {
                boolean highwayMode = ConfigService.get().highway().enabled();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    SignTextService.onChunkReady(p.serverLevel(), p.chunkPosition());
                    if (highwayMode) {
                        HighwayPlanningService.planAroundPlayer(p);
                    } else {
                        RoadPlanningService.planAroundPlayer(p);
                    }
                    LongDrivePlanningService.tickPlayer(p);
                }
            }

            for (ServerLevel level : server.getAllLevels()) {
                if (level == null) continue;
                RoadGenerationService.tick(level);
                SignTextService.tick(level);
            }

            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null && ConfigService.get().highway().enabled()) {
                HighwayCellPathPlanningService.tick(overworld);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoadGenerationService.onServerStopping();
            HighwayPlanningService.resetAll();
            HighwayCellPathPlanningService.resetAll();
            LongDrivePlanningService.resetAll();
            CacheManager.onServerStopping(server.getAllLevels());
            ThreadPoolManager.onServerStopping();
            SignTextService.clearPending();
        });
    }
}
