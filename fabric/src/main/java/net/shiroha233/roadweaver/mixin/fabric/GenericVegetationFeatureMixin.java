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
 * 用于兼容模组树木（BYG、BOP 等）和 NBT 结构树
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
            if (roadweaver$isTreeLikeFeature(config)) {
                if (RoadPositionQuery.isOnRoad(level, pos)) {
                    cir.setReturnValue(false);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 判断是否为树木类 Feature
     */
    @Unique
    private boolean roadweaver$isTreeLikeFeature(FeatureConfiguration config) {
        String className = this.getClass().getName().toLowerCase();
        
        // 排除已有专门 Mixin 的原版类
        if (className.equals("net.minecraft.world.level.levelgen.feature.treefeature") ||
            className.equals("net.minecraft.world.level.levelgen.feature.abstracthugemushroomfeature") ||
            className.equals("net.minecraft.world.level.levelgen.feature.hugefungusfeature") ||
            className.equals("net.minecraft.world.level.levelgen.feature.bamboofeature")) {
            return false;
        }
        
        // 检查 Feature 类名
        if (roadweaver$containsTreeKeyword(className)) {
            return true;
        }
        
        // 检查配置类名（用于 NBT 结构树等）
        if (config != null) {
            String configName = config.getClass().getName().toLowerCase();
            if (roadweaver$containsTreeKeyword(configName)) {
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
               name.contains("cactus") ||
               name.contains("chorus") ||
               name.contains("nbt") ||           // NBT 结构树
               name.contains("structure") ||     // 结构树
               name.contains("fromstructure");   // TYG 的 TreeFromStructure
    }
}
