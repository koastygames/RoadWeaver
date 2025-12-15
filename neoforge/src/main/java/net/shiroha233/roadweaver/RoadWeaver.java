package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.config.ConfigService;

import net.shiroha233.roadweaver.datagen.RoadWeaverDataGenerator;
import net.shiroha233.roadweaver.network.neoforge.MapNetworkForge;
import net.shiroha233.roadweaver.planning.neoforge.ServerPlanningHooks;
import net.shiroha233.roadweaver.features.neoforge.RoadFeaturesForge;
import net.shiroha233.roadweaver.structures.neoforge.StructureRegistryNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {

    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RoadWeaver(IEventBus modEventBus) {
        LOGGER.info("Initializing RoadWeaver (NeoForge)...");
        
        // 加载配置（common 实现，写入 config/roadweaver.json）
        ConfigService.load();
        
        // 注册数据生成事件（确保 runData 时 provider 被加入）
        modEventBus.addListener(RoadWeaverDataGenerator::gatherData);

        // 注册结构类型（Structure、StructurePiece）
        StructureRegistryNeoForge.register(modEventBus);
        
        // 注册 Feature
        RoadFeaturesForge.register(modEventBus);
        
        // 注册网络通道
        MapNetworkForge.register(modEventBus);
        
        // 注册服务器规划钩子：初始与动态增量规划
        ServerPlanningHooks.register(modEventBus);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(
                    IConfigScreenFactory.class,
                    () -> (mc, screen) -> net.shiroha233.roadweaver.client.neoforge.ConfigScreenFactoryImpl.createConfigScreen(screen)
            );
            net.shiroha233.roadweaver.client.neoforge.ClientKeyMappings.register(modEventBus);
        }
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }
}
