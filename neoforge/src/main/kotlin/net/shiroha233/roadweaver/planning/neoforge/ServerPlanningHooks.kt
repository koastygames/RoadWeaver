package net.shiroha233.roadweaver.planning.neoforge

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.shiroha233.roadweaver.generation.InitialGenManager
import net.shiroha233.roadweaver.generation.RoadGenerationService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.planning.RoadPlanningService
import net.shiroha233.roadweaver.runtime.CacheManager
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import net.shiroha233.roadweaver.util.ComputeService
import java.util.Objects

@Suppress("MemberVisibilityCanBePrivate")
object ServerPlanningHooks {
    private var tick: Int = 0

    @JvmStatic
    fun register(modBus: IEventBus) {
        // Server lifecycle events are on NeoForge.EVENT_BUS (Game bus)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        CacheManager.onServerStarted()
        ThreadPoolManager.onServerStarted(event.server)

        val level: ServerLevel = event.server.getLevel(Objects.requireNonNull(Level.OVERWORLD)) ?: return

        // 无论是新世界还是已存在的世界，都发现并缓存结构（供结构选择 GUI 使用）
        net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level)

        val dedicated = event.server.isDedicatedServer
        if (dedicated) {
            RoadGenerationService.onServerStarted()
            RoadPlanningService.initialPlanAsync(level)
            return
        }

        val conns: List<Records.StructureConnection>? = WorldDataProvider.getInstance().getStructureConnections(level)
        if (conns == null || conns.isEmpty()) {
            InitialGenManager.begin(level)
            InitialGenManager.blockUntilDone(level)
        } else {
            RoadGenerationService.onServerStarted()
        }
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server ?: return
        if ((tick++ % 20) == 0) {
            for (p: ServerPlayer in server.playerList.players) {
                RoadPlanningService.planAroundPlayer(p)
            }
        }
        val level = server.getLevel(Objects.requireNonNull(Level.OVERWORLD))
        if (level != null) {
            RoadGenerationService.tick(level)
        }
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        RoadGenerationService.onServerStopping()
        CacheManager.onServerStopping(event.server.allLevels)
        ComputeService.shutdownNow()
    }
}
