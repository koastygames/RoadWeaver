package net.shiroha233.roadweaver.planning.fabric

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.generation.InitialGenManager
import net.shiroha233.roadweaver.generation.RoadGenerationService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.planning.RoadPlanningService
import net.shiroha233.roadweaver.runtime.CacheManager
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import net.shiroha233.roadweaver.util.ComputeService

object ServerPlanningHooks {
    private var tick: Int = 0

    @JvmStatic
    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            CacheManager.onServerStarted() // 统一初始化缓存
            ThreadPoolManager.onServerStarted(server)
            val level: ServerLevel = server.getLevel(Level.OVERWORLD) ?: return@register

            // 无论是新世界还是已存在的世界，都发现并缓存结构（供结构选择 GUI 使用）
            net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level)

            val dedicated = server.isDedicatedServer
            if (dedicated) {
                RoadPlanningService.initialPlanAsync(level)
                return@register
            }

            val conns: List<Records.StructureConnection> = WorldDataProvider.getInstance().getStructureConnections(level)
            if (conns.isEmpty()) {
                InitialGenManager.begin(level)
                InitialGenManager.blockUntilDone(level)
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if ((tick++ % 20) == 0) {
                for (p: ServerPlayer in server.playerList.players) {
                    RoadPlanningService.planAroundPlayer(p)
                }
            }
            val level: ServerLevel? = server.getLevel(Level.OVERWORLD)
            if (level != null) {
                RoadGenerationService.tick(level)
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            RoadGenerationService.onServerStopping()
            CacheManager.onServerStopping(server.allLevels) // 统一清理所有缓存
            ComputeService.shutdownNow()
        }
    }
}
