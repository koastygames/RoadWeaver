package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * 通用植物 Feature 拦截器
 * 用于兼容其他模组的树木（如 BOP、BYG 等）
 * 通过类名关键字判断是否为树木类 Feature
 */
@Mixin(Feature.class)
public class GenericVegetationFeatureMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void roadweaver$blockModdedTreesOnRoad(FeatureConfiguration config,
                                                    WorldGenLevel level,
                                                    ChunkGenerator generator,
                                                    RandomSource random,
                                                    BlockPos pos,
                                                    CallbackInfoReturnable<Boolean> cir) {
        try {
            // 只处理模组的树木类 Feature（原版已有专门的 Mixin）
            if (roadweaver$isModdedTreeFeature()) {
                if (RoadPositionQuery.isOnRoad(level, pos)) {
                    cir.setReturnValue(false);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 判断是否为模组的树木类 Feature
     */
    @Unique
    private boolean roadweaver$isModdedTreeFeature() {
        String className = this.getClass().getName().toLowerCase();
        // 排除原版类（已有专门 Mixin）
        if (className.startsWith("net.minecraft.")) return false;
        
        // 检查类名是否包含树木相关关键字
        return className.contains("tree") ||
               className.contains("fungus") ||
               className.contains("mushroom") ||
               className.contains("bamboo") ||
               className.contains("cactus") ||
               className.contains("chorus");
    }
}
