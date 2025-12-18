package net.shiroha233.roadweaver.planning.forge

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.event.TickEvent
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
        // Lifecycle events are on MinecraftForge.EVENT_BUS
        MinecraftForge.EVENT_BUS.addListener(::onServerStarted)
        MinecraftForge.EVENT_BUS.addListener(::onServerTick)
        MinecraftForge.EVENT_BUS.addListener(::onServerStopping)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        CacheManager.onServerStarted()
        ThreadPoolManager.onServerStarted(event.server)

        val level: ServerLevel = event.server.getLevel(Objects.requireNonNull(Level.OVERWORLD)) ?: return

        // 无论是新世界还是已存在的世界，都发现并缓存结构
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

    private fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val server = event.getServer() ?: return
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
