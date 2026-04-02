package net.shiroha233.roadweaver;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.forge.RoadFeaturesForge;
import net.shiroha233.roadweaver.network.forge.MapNetworkForge;
import net.shiroha233.roadweaver.planning.forge.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.forge.StructureRegistryForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (Forge)
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
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPermissionsChanged);
        
        ServerPlanningHooks.register();
        
        modBus.addListener(this::commonSetup);
    }
    
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("RoadWeaver common setup completed");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MapAccessCommand.register(event.getDispatcher());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetworkForge.syncMapAccess(player);
        }
    }

    private void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetworkForge.syncMapAccess(player);
        }
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }
}
