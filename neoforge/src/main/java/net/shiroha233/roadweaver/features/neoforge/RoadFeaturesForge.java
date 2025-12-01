package net.shiroha233.roadweaver.features.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.RoadFeature;
import net.shiroha233.roadweaver.features.config.RoadFeatureConfig;

public final class RoadFeaturesForge {
    private RoadFeaturesForge() {}

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, RoadWeaver.MOD_ID);

    public static final DeferredHolder<Feature<?>, Feature<RoadFeatureConfig>> ROAD_FEATURE = FEATURES.register(
            "road_feature",
            () -> new RoadFeature(RoadFeatureConfig.CODEC)
    );

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
