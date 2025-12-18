package net.shiroha233.roadweaver.features.forge

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.features.path.PathFeature
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig
import java.util.function.Supplier

class RoadFeaturesForge private constructor() {
    companion object {
        @JvmField
        val FEATURES: DeferredRegister<Feature<*>> = DeferredRegister.create(Registries.FEATURE, RoadWeaver.MOD_ID)

        @JvmField
        val ROAD_FEATURE: RegistryObject<Feature<PathFeatureConfig>> = FEATURES.register("road_feature") {
            PathFeature(PathFeatureConfig.CODEC)
        }

        @JvmStatic
        fun register(modBus: IEventBus) {
            FEATURES.register(modBus)
        }
    }
}
