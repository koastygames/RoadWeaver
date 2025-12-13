package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.generation.ChunkGenTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 通用 Feature 拦截器，阻止树木类 Feature 在道路上生成（1.21+ NeoForge 版本）。
 * <p>
 * 重要优化：只在区块生成阶段（WorldGenRegion）阻拦树木，
 * 生成完成后玩家种植的树木不受影响。
 * </p>
 * <p>
 * 判断策略：
 * 1. 基于 Feature 的注册 ID（最可靠）
 * 2. 基于配置类型（TreeConfiguration 及其子类）
 * 3. 基于类名关键字（兜底方案）
 * </p>
 */
@Mixin(Feature.class)
public abstract class GenericFeatureMixin {

    // 已知的树木类 Feature 注册 ID（命名空间:路径）
    @Unique
    private static final Set<String> TREE_FEATURE_IDS = Set.of(
            // 原版树木
            "minecraft:tree",
            "minecraft:huge_brown_mushroom",
            "minecraft:huge_red_mushroom",
            "minecraft:huge_fungus",
            "minecraft:chorus_plant",
            // Oh-The-Trees-You'll-Grow (TYG) - BWG 使用的 NBT 树库
            "oh_the_trees_youll_grow:tree_from_nbt_v1",
            "ohthetreesyoullgrow:tree_from_nbt_v1"
            // 注意：DynamicTrees 不走 Feature#place 流程，需要单独的 DynamicTreeFeatureMixin 拦截
    );

    // 已知的树木类 Feature 命名空间前缀
    @Unique
    private static final Set<String> TREE_FEATURE_NAMESPACES = Set.of(
            "biomesoplenty",    // Biomes O' Plenty
            "biomeswevegone",   // Oh-The-Biomes-We've-Gone
            "bwg",              // BWG 简称
            "byg",              // Biomes You'll Go
            "twilightforest",   // 暮色森林
            "regions_unexplored", // Regions Unexplored
            "aether"            // 天境
    );

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
        // 关键优化：只在区块生成阶段阻拦，玩家种植的树木不受影响
        if (!ChunkGenTracker.isWorldGenPhase(level)) {
            return; // 不是区块生成阶段，不阻拦
        }
        
        // 获取当前 Feature 实例
        Feature<?> feature = (Feature<?>) (Object) this;

        if (roadweaver$isTreeLikeFeature(feature, config)) {
            if (RoadPositionQuery.isOnRoad(level, pos)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * 判断是否为树木类 Feature
     * 使用多层判断策略，确保兼容原版和各种模组
     */
    @Unique
    private boolean roadweaver$isTreeLikeFeature(Feature<?> feature, FeatureConfiguration config) {
        // 策略1：检查 Feature 的注册 ID
        ResourceLocation featureId = BuiltInRegistries.FEATURE.getKey(feature);
        if (featureId != null) {
            String fullId = featureId.toString();

            // 精确匹配已知的树木 Feature ID
            if (TREE_FEATURE_IDS.contains(fullId)) {
                return true;
            }

            // 检查命名空间（某些模组的所有树都在特定命名空间下）
            String namespace = featureId.getNamespace();
            String path = featureId.getPath().toLowerCase();

            if (TREE_FEATURE_NAMESPACES.contains(namespace)) {
                // 对于这些模组，检查路径是否包含树木关键字
                if (roadweaver$containsTreeKeyword(path)) {
                    return true;
                }
            }

            // 检查路径中的树木关键字（适用于所有命名空间）
            if (roadweaver$containsTreeKeyword(path)) {
                return true;
            }
        }

        // 策略2：检查配置类型
        if (config != null) {
            // 原版 TreeConfiguration 及其子类（包括 BOP 的 BOPTreeConfiguration）
            if (config instanceof TreeConfiguration) {
                return true;
            }

            // 检查配置类名（用于 TYG 的 TreeFromStructureNBTConfig 等）
            String configClassName = config.getClass().getName().toLowerCase();
            if (configClassName.contains("tree") ||
                configClassName.contains("nbt") ||
                configClassName.contains("structure")) {
                return true;
            }
        }

        // 策略3：检查 Feature 类名（兜底）
        String featureClassName = feature.getClass().getName().toLowerCase();
        return roadweaver$containsTreeKeyword(featureClassName);
    }

    /**
     * 检查字符串是否包含树木相关关键字
     */
    @Unique
    private boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("chorus") ||
               name.contains("bush") ||
               name.contains("shrub");
    }
}
