package net.shiroha233.roadweaver.mixin.neoforge

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.levelgen.presets.WorldPreset
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.Optional
import java.util.OptionalLong

@Mixin(CreateWorldScreen::class)
abstract class CreateWorldScreenInitMixin : Screen(null) {

    @Shadow
    @Final
    private lateinit var uiState: WorldCreationUiState

    @Inject(method = ["<init>"], at = [At("RETURN")])
    private fun onInitEnd(
        minecraft: Minecraft,
        screen: Screen,
        worldCreationContext: WorldCreationContext,
        optional: Optional<ResourceKey<WorldPreset>>,
        optionalLong: OptionalLong,
        ci: CallbackInfo
    ) {
        try {
            val settings = uiState.settings
            val registryAccess: RegistryAccess.Frozen? = settings?.worldgenLoadContext()
            if (registryAccess != null) {
                StructureDiscoveryService.discoverFromRegistryAccess(registryAccess)
            }
        } catch (_: Exception) {
        }
    }
}
