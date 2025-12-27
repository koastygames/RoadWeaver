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
    private ServerPlanningHooks() {
    }

    private static int tick;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CacheManager.onServerStarted(); // 统一初始化缓存
            ThreadPoolManager.onServerStarted(server);
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null)
                return;

            // 无论是新世界还是已存在的世界，都发现并缓存结构（供结构选择 GUI 使用）
            net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level);

            boolean dedicated = server.isDedicatedServer();
            if (dedicated) {
                RoadGenerationService.onServerStarted();
                boolean highwayMode = ConfigService.get().highwayEnabled();
                if (highwayMode) {
                    HighwayPlanningService.initialPlanAsync(level);
                } else {
                    RoadPlanningService.initialPlanAsync(level);
                }
                return;
            }
            List<Records.StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
            if (conns == null || conns.isEmpty()) {
                RoadGenerationService.onServerStarted();
                InitialGenManager.begin(level);
                InitialGenManager.blockUntilDone(level);
            } else {
                RoadGenerationService.onServerStarted();
                // highway 模式：初次加载由 planAroundPlayer 在 tick 中按玩家所在 1x1 cell 触发。
            }

            // highway 模式下：启动时先以出生点/首个玩家位置做一次初始规划，保证进游戏即可看到网格边。
            if (ConfigService.get().highwayEnabled()) {
                HighwayPlanningService.initialPlanAsync(level);
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

            // 道路生成队列是“按维度”维护的，因此必须对所有已加载维度 tick，
            // 否则下界/末地/模组维度的连接永远不会被消费。
            for (ServerLevel level : server.getAllLevels()) {
                if (level == null)
                    continue;
                RoadGenerationService.tick(level);
                SignTextService.tick(level);
            }

            // Highway 与其 cell backfill 目前仅设计用于主世界。
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null && ConfigService.get().highwayEnabled()) {
                HighwayCellPathPlanningService.tick(overworld);
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
