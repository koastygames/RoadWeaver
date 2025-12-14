package net.shiroha233.roadweaver.planning.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.List;
import java.util.Objects;

public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        CacheManager.onServerStarted(); // 统一初始化缓存
        ThreadPoolManager.onServerStarted(event.getServer());
        ServerLevel level = event.getServer().getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level == null) return;
        
        // 无论是新世界还是已存在的世界，都发现并缓存结构（供结构选择 GUI 使用）
        net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level);
        
        boolean dedicated = event.getServer().isDedicatedServer();
        if (dedicated) {
            RoadGenerationService.onServerStarted();
            RoadPlanningService.initialPlanAsync(level);
            return;
        }
        List<Records.StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) {
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
                RoadPlanningService.planAroundPlayer(p);
            }
        }
        ServerLevel level = server.getLevel(Objects.requireNonNull(Level.OVERWORLD));
        if (level != null) {
            RoadGenerationService.tick(level);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        RoadGenerationService.onServerStopping();
        CacheManager.onServerStopping(event.getServer().getAllLevels()); // 统一清理所有缓存
        ComputeService.shutdownNow();
    }
}
