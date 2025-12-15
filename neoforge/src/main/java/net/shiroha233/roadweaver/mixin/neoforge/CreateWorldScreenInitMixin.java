package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
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
 * 参考 ImmersivePortals 的实现方式
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitMixin extends Screen {

    @Shadow
    @Final
    private WorldCreationUiState uiState;

    protected CreateWorldScreenInitMixin() {
        super(null);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInitEnd(
            Minecraft minecraft, Runnable runnable, WorldCreationContext worldCreationContext,
            Optional<ResourceKey<WorldPreset>> optional, OptionalLong optionalLong, CreateWorldCallback createWorldCallback, CallbackInfo ci
    ) {
        // 从 WorldCreationContext 获取 RegistryAccess 并发现结构
        try {
            WorldCreationContext settings = uiState.getSettings();
            if (settings != null) {
                RegistryAccess.Frozen registryAccess = settings.worldgenLoadContext();
                if (registryAccess != null) {
                    StructureDiscoveryService.discoverFromRegistryAccess(registryAccess);
                }
            }
        } catch (Exception e) {
            // 忽略错误，不影响正常流程
        }
    }
}
