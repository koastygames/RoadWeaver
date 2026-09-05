package net.koastygames.witherdimension;

import net.fabricmc.api.ModInitializer;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.koastygames.witherdimension.registry.ModEntities;
import net.koastygames.witherdimension.registry.ModItems;
import net.koastygames.witherdimension.world.ModWorldgen;
import net.koastygames.witherdimension.world.PortalHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WitherDimensionMod implements ModInitializer {
    public static final String MOD_ID = "witherdimension";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceKey<Level> WITHER_LEVEL = ResourceKey.create(Registries.DIMENSION, id("wither"));

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModEntities.initialize();
        ModWorldgen.initialize();
        PortalHandler.initialize();
        LOGGER.info("The Wither Dimension initialized");
    }
}
