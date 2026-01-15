package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Mixin 用于在创建世界界面初始化时获取 RegistryAccess 并发现结构
 * 
 * 这是解决 Forge 版结构选择列表无法提取的关键 Mixin。
 * 通过在创建世界界面初始化时获取 RegistryAccess，可以提前发现所有可用的结构。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitMixin extends Screen {

    @Shadow
    @Final
    WorldCreationUiState uiState;

    protected CreateWorldScreenInitMixin() {
        super(Component.empty());
    }

    /**
     * 在 CreateWorldScreen 构造函数返回时注入，获取 RegistryAccess 并发现结构
     * 
     * 1.20.1 构造函数签名：
     * private CreateWorldScreen(Minecraft, Screen, WorldCreationContext, Optional<ResourceKey<WorldPreset>>, OptionalLong)
     */
    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;Ljava/util/Optional;Ljava/util/OptionalLong;)V", at = @At("RETURN"))
    private void onInitEnd(
            Minecraft minecraft, Screen screen, WorldCreationContext worldCreationContext,
            Optional<ResourceKey<WorldPreset>> optional, OptionalLong optionalLong, CallbackInfo ci
    ) {
        // 从 WorldCreationContext 获取 RegistryAccess 并发现结构
        try {
            // 优先使用构造函数参数，避免某些阶段 uiState.getSettings() 尚未就绪。
            WorldCreationContext settings = (worldCreationContext != null) ? worldCreationContext : uiState.getSettings();
            if (settings != null) {
                RegistryAccess.Frozen registryAccess = settings.worldgenLoadContext();
                if (registryAccess == null) return;

                Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
                Registry<LevelStem> levelStemRegistry = null;
                try {
                    levelStemRegistry = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
                } catch (Exception ignored) {
                }

                if (levelStemRegistry == null) {
                    /*
                    try {
                        levelStemRegistry = settings.selectedDimensions().bake(settings.datapackDimensions()).dimensions();
                    } catch (Exception ignored) {
                        try {
                            levelStemRegistry = settings.selectedDimensions().dimensions();
                        } catch (Exception ignored2) {
                        }
                    }
                    */
                }

                if (levelStemRegistry != null) {
                    StructureDiscoveryService.discoverFromRegistries(structureRegistry, levelStemRegistry);
                } else {
                    StructureDiscoveryService.discoverFromRegistryAccess(registryAccess);
                }
            }
        } catch (Exception e) {
            // 忽略错误，不影响正常流程
        }
    }
}
