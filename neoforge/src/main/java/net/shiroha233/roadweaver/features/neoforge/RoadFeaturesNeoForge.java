package net.shiroha233.roadweaver.features.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.highway.HighwayFeature;
import net.shiroha233.roadweaver.features.highway.config.HighwayFeatureConfig;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.PathFeature;

public final class RoadFeaturesNeoForge {
    private RoadFeaturesNeoForge() {}

    public static void register(IEventBus modBus) {
        // 使用 NeoForge 1.21.1 的新注册方式
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.FEATURE)) {
                event.register(Registries.FEATURE, 
                    ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "road_feature"), 
                    () -> new PathFeature(PathFeatureConfig.CODEC));
                event.register(Registries.FEATURE, 
                    ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "highway_feature"), 
                    () -> new HighwayFeature(HighwayFeatureConfig.CODEC));
            }
        });
    }
}
