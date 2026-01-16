package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


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
     * 在 CreateWorldScreen.init 完成后注入，获取 RegistryAccess 并发现结构。
     * 使用 init 避免构造函数签名在 1.21.1 发生变化导致注入失效。
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitEnd(CallbackInfo ci) {
        // 从 WorldCreationContext 获取 RegistryAccess 并发现结构
        try {
            WorldCreationContext settings = uiState != null ? uiState.getSettings() : null;
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
                    try {
                        levelStemRegistry = settings.selectedDimensions().bake(settings.datapackDimensions()).dimensions();
                    } catch (Exception ignored) {
                    }
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
