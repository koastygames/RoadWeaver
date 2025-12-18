package net.shiroha233.roadweaver.mixin.fabric

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.presets.WorldPreset
import net.minecraft.world.level.levelgen.structure.Structure
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.*

/**
 * Mixin 用于在创建世界界面初始化时获取 RegistryAccess 并发现结构
 *
 * 这是解决 Forge/Fabric 版结构选择列表无法提取的关键 Mixin。
 * 通过在创建世界界面初始化时获取 RegistryAccess，可以提前发现所有可用的结构。
 */
@Mixin(CreateWorldScreen::class)
abstract class CreateWorldScreenInitMixin protected constructor() : Screen(Component.empty()) {

    @Shadow
    @Final
    private lateinit var uiState: WorldCreationUiState

    /**
     * 在 CreateWorldScreen 构造函数返回时注入，获取 RegistryAccess 并发现结构
     *
     * 1.20.1 构造函数签名：
     * private CreateWorldScreen(Minecraft, Screen, WorldCreationContext, Optional<ResourceKey<WorldPreset>>, OptionalLong)
     */
    @Inject(
        method = ["<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;Ljava/util/Optional;Ljava/util/OptionalLong;)V"],
        at = [At("RETURN")]
    )
    private fun onInitEnd(
        minecraft: Minecraft, screen: Screen, worldCreationContext: WorldCreationContext,
        optional: Optional<ResourceKey<WorldPreset>>, optionalLong: OptionalLong, ci: CallbackInfo
    ) {
        // 从 WorldCreationContext 获取 RegistryAccess 并发现结构
        try {
            val settings: WorldCreationContext? = uiState.settings
            if (settings !== null) {
                val registryAccess: RegistryAccess.Frozen = settings.worldgenLoadContext()

                // 1. 获取结构注册表
                val structureRegistry: Registry<Structure>? = registryAccess.registryOrThrow(Registries.STRUCTURE)

                // 2. 获取维度注册表
                // 说明：
                // - WorldCreationContext.worldgenLoadContext() 会从 DIMENSIONS layer 起 replace 成 EMPTY（见 MCP 源码），
                //   因此 registryAccess 往往不包含 LEVEL_STEM。
                // - datapackDimensions() 通常只包含“数据包追加维度”（例如暮色森林）。
                // - selectedDimensions() 通常只包含世界预设的三维度（overworld/nether/end）。
                // - 需要按原版逻辑 bake() 将二者合并，得到完整维度表。
                val levelStemRegistry: Registry<LevelStem>? = try {
                    settings.selectedDimensions().bake(settings.datapackDimensions()).dimensions()
                } catch (_: Exception) {
                    settings.selectedDimensions().dimensions()
                }

                if (structureRegistry !== null && levelStemRegistry !== null) {
                    StructureDiscoveryService.discoverFromRegistries(structureRegistry, levelStemRegistry)
                }
            }
        } catch (e: Exception) {
            // 忽略错误，不影响正常流程
        }
    }
}
