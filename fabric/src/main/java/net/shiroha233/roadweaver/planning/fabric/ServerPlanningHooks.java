package net.shiroha233.roadweaver.planning.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.List;

public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CacheManager.onServerStarted(); // 统一初始化缓存
            ThreadPoolManager.onServerStarted(server);
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return;
            
            // 无论是新世界还是已存在的世界，都发现并缓存结构（供结构选择 GUI 使用）
            net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level);
            
            boolean dedicated = server.isDedicatedServer();
            if (dedicated) {
                RoadGenerationService.onServerStarted();
                if (!ConfigService.get().highwayEnabled()) {
                    RoadPlanningService.initialPlanAsync(level);
                }
                return;
            }
            List<Records.StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
            if (conns == null || conns.isEmpty()) {
                InitialGenManager.begin(level);
                InitialGenManager.blockUntilDone(level);
            } else {
                RoadGenerationService.onServerStarted();
                // highway 模式：初次加载由 planAroundPlayer 在 tick 中按玩家所在 1x1 cell 触发。
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((tick++ % 20) == 0) {
                boolean highwayMode = ConfigService.get().highwayEnabled();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (highwayMode) {
                        HighwayPlanningService.planAroundPlayer(p);
                    } else {
                        RoadPlanningService.planAroundPlayer(p);
                    }
                }
            }
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                RoadGenerationService.tick(level);
                if (ConfigService.get().highwayEnabled()) {
                    HighwayCellPathPlanningService.tick(level);
                }
                SignTextService.tick(level);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoadGenerationService.onServerStopping();
            HighwayPlanningService.resetAll();
            HighwayCellPathPlanningService.resetAll();
            SignTextService.clearPending();
            CacheManager.onServerStopping(server.getAllLevels()); // 统一清理所有缓存
            ComputeService.shutdownNow();
        });
    }
}
