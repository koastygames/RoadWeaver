package net.shiroha233.roadweaver.planning.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.StructureSystem;
import net.shiroha233.roadweaver.structures.index.StructureIndexRestorer;

import java.util.Objects;

public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register(IEventBus modBus) {
        // Server lifecycle events are on NeoForge.EVENT_BUS (Game bus)
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        StructureSystem.clearAll();
        net.shiroha233.roadweaver.runtime.ThreadPoolManager.onServerStarted(event.getServer());
        ServerLevel level = event.getServer().getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level == null) return;
        // 从持久化数据恢复结构索引
        StructureIndexRestorer.restore(level);
        boolean dedicated = event.getServer().isDedicatedServer();
        if (dedicated) {
            RoadGenerationService.onServerStarted();
            RoadPlanningService.initialPlanAsync(level);
            return;
        }
        java.util.List<net.shiroha233.roadweaver.helpers.Records.StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) return;
        if ((tick++ % 20) == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                RoadPlanningService.planAroundPlayer(p);
            }
        }
        ServerLevel level = server.getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level != null) {
            RoadGenerationService.tick(level);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        for (ServerLevel lvl : server.getAllLevels()) {
            RoadShardStorage.flushAll(lvl);
            RoadShardStorage.clearAll(lvl);
        }
        RoadGenerationService.onServerStopping();
        StructureSystem.clearAll();
        ComputeService.shutdownNow();
    }
}
