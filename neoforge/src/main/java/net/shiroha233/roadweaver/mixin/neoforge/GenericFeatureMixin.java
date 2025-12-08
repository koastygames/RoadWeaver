package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用 Feature 拦截器，阻止树木类 Feature 在道路上生成（1.21+ NeoForge 端）。
 */
@Mixin(Feature.class)
public abstract class GenericFeatureMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void roadweaver$skipTreeLikeFeaturesOnRoad(FeatureConfiguration config,
                                                       WorldGenLevel level,
                                                       ChunkGenerator chunkGenerator,
                                                       RandomSource random,
                                                       BlockPos pos,
                                                       CallbackInfoReturnable<Boolean> cir) {

        if (roadweaver$isTreeLikeFeature(config)) {
            if (RoadPositionQuery.isOnRoad(level, pos)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Unique
    private boolean roadweaver$isTreeLikeFeature(FeatureConfiguration config) {
        String className = this.getClass().getName().toLowerCase();
        if (roadweaver$containsTreeKeyword(className)) {
            return true;
        }

        if (config != null) {
            String configClassName = config.getClass().getName().toLowerCase();
            if (roadweaver$containsTreeKeyword(configClassName)) {
                return true;
            }
            if (config instanceof TreeConfiguration) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("chorus") ||
               name.contains("cactus") ||
               name.contains("nbt") ||
               name.contains("templatefeature") ||
               name.contains("bushfeature");
    }
}
