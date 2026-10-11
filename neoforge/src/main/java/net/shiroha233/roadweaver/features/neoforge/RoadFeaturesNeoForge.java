package net.shiroha233.roadweaver.features.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.highway.HighwayFeature;
import net.shiroha233.roadweaver.features.path.PathFeature;

public final class RoadFeaturesNeoForge {
    private RoadFeaturesNeoForge() {}

    public static void register(IEventBus modBus) {
        // 使用 NeoForge 1.21.1 的新注册方式
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.FEATURE_TYPE)) {
                event.register(Registries.FEATURE_TYPE, 
                    Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "road_feature"), 
                    () -> PathFeature.CODEC);
                event.register(Registries.FEATURE_TYPE, 
                    Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "highway_feature"), 
                    () -> HighwayFeature.CODEC);
            }
        });
    }
}
