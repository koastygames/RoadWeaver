package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * 通用植物 Feature 拦截器
 * 拦截 ConfiguredFeature.place() 方法，这是所有 Feature 生成的统一入口
 */
@Mixin(ConfiguredFeature.class)
public class GenericVegetationFeatureMixin<FC extends FeatureConfiguration, F extends Feature<FC>> {

    @Shadow @Final public F feature;
    @Shadow @Final public FC config;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void roadweaver$blockTreesOnRoad(WorldGenLevel level,
                                              ChunkGenerator generator,
                                              RandomSource random,
                                              BlockPos pos,
                                              CallbackInfoReturnable<Boolean> cir) {
        try {
            if (roadweaver$isTreeLikeFeature()) {
                if (RoadPositionQuery.isOnRoad(level, pos)) {
                    cir.setReturnValue(false);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Unique
    private boolean roadweaver$isTreeLikeFeature() {
        if (this.feature == null) return false;
        
        String featureClassName = this.feature.getClass().getName().toLowerCase();
        
        if (roadweaver$isVanillaTreeFeature(featureClassName)) {
            return false;
        }
        
        if (roadweaver$containsTreeKeyword(featureClassName)) {
            return true;
        }
        
        if (this.config != null) {
            String configName = this.config.getClass().getName().toLowerCase();
            if (roadweaver$containsTreeKeyword(configName)) {
                return true;
            }
        }
        
        return false;
    }

    @Unique
    private boolean roadweaver$isVanillaTreeFeature(String className) {
        return className.equals("net.minecraft.world.level.levelgen.feature.treefeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.abstracthugemushroomfeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.hugefungusfeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.bamboofeature");
    }

    @Unique
    private boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("cactus") ||
               name.contains("chorus") ||
               name.contains("nbt") ||
               name.contains("structure") ||
               name.contains("fromstructure") ||
               name.contains("template") ||
               name.contains("bush");
    }
}
