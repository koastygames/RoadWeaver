package net.shiroha233.roadweaver

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.config.ModConfig
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.datagen.RoadWeaverDataGenerator
import net.shiroha233.roadweaver.features.forge.RoadFeaturesForge
import net.shiroha233.roadweaver.network.forge.MapNetworkForge
import net.shiroha233.roadweaver.planning.forge.ServerPlanningHooks
import net.shiroha233.roadweaver.structures.forge.StructureRegistryNeoForge
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(RoadWeaver.MOD_ID)
class RoadWeaver() {
    private val modEventBus: IEventBus = FMLJavaModLoadingContext.get().modEventBus

    init {
        LOGGER.info("Initializing RoadWeaver (Forge)...")

        // 加载配置
        ConfigService.load()

        // 注册数据生成事件
        modEventBus.addListener(RoadWeaverDataGenerator::gatherData)

        // 注册结构类型
        StructureRegistryNeoForge.register(modEventBus)

        // 注册 Feature
        RoadFeaturesForge.register(modEventBus)

        // 注册网络通道
        MapNetworkForge.register(modEventBus)

        // 注册服务器规划钩子
        ServerPlanningHooks.register(modEventBus)

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Register Cloth Config Screen
            ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory::class.java
            ) {
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory { _, screen ->
                    net.shiroha233.roadweaver.client.forge.ConfigScreenFactoryImpl.createConfigScreen(screen)
                }
            }
            MinecraftForge.EVENT_BUS.addListener(::onClientLoggingIn)
            MinecraftForge.EVENT_BUS.addListener(::onClientLoggingOut)
        }
    }

    private fun onClientLoggingIn(e: ClientPlayerNetworkEvent.LoggingIn) {
        net.shiroha233.roadweaver.client.map.data.MapSnapshotCache.clearNow()
    }

    private fun onClientLoggingOut(e: ClientPlayerNetworkEvent.LoggingOut) {
        net.shiroha233.roadweaver.client.map.data.MapSnapshotCache.clearNow()
    }

    companion object {
        const val MOD_ID: String = "roadweaver"

        private val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

        @JvmStatic
        fun getLogger(): Logger {
            return LOGGER
        }
    }
}
