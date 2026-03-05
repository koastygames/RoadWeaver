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
 * Mixin 鐢ㄤ簬鍦ㄥ垱寤轰笘鐣岀晫闈㈠垵濮嬪寲鏃惰幏鍙?RegistryAccess 骞跺彂鐜扮粨鏋?
 * 
 * 杩欐槸瑙ｅ喅 NeoForge 鐗堢粨鏋勯€夋嫨鍒楄〃鏃犳硶鎻愬彇鐨勫叧閿?Mixin銆?
 * 閫氳繃鍦ㄥ垱寤轰笘鐣岀晫闈㈠垵濮嬪寲鏃惰幏鍙?RegistryAccess锛屽彲浠ユ彁鍓嶅彂鐜版墍鏈夊彲鐢ㄧ殑缁撴瀯銆?
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
     * 鍦?CreateWorldScreen.init 瀹屾垚鍚庢敞鍏ワ紝鑾峰彇 RegistryAccess 骞跺彂鐜扮粨鏋勩€?
     * 浣跨敤 init 閬垮厤鏋勯€犲嚱鏁扮鍚嶅湪 1.21.1 鍙戠敓鍙樺寲瀵艰嚧娉ㄥ叆澶辨晥銆?
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitEnd(CallbackInfo ci) {
        // 浠?WorldCreationContext 鑾峰彇 RegistryAccess 骞跺彂鐜扮粨鏋?
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

                if (levelStemRegistry != null) {
                    StructureDiscoveryService.discoverFromRegistries(structureRegistry, levelStemRegistry);
                } else {
                    StructureDiscoveryService.discoverFromRegistryAccess(registryAccess);
                }
            }
        } catch (Exception e) {
            // 蹇界暐閿欒锛屼笉褰卞搷姝ｅ父娴佺▼
        }
    }
}

