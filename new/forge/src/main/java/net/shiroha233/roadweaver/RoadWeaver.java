package net.shiroha233.roadweaver;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.forge.RoadFeaturesForge;
import net.shiroha233.roadweaver.network.forge.MapNetworkForge;
import net.shiroha233.roadweaver.planning.forge.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.forge.StructureRegistryForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (Forge)
 * 职责：模组初始化入口，注册所有系统组件
 */
@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {
    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RoadWeaver() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        LOGGER.info("Initializing RoadWeaver (Forge)...");
        
        ConfigService.load();
        LOGGER.info("Configuration loaded");
        
        StructureRegistryForge.register(modBus);
        LOGGER.info("Structure types registered");

        RoadFeaturesForge.register(modBus);
        LOGGER.info("Road features registered");

        MapNetworkForge.register();
        
        ServerPlanningHooks.register();
        
        modBus.addListener(this::commonSetup);
    }
    
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("RoadWeaver common setup completed");
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }
}
