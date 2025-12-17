package net.shiroha233.roadweaver.mixin.fabric

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.tabs.GridLayoutTab
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.fabric.ConfigScreenFactoryImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["net.minecraft.client.gui.screens.worldselection.CreateWorldScreen\$GameTab"])
abstract class CreateWorldScreenMixin(title: Component) : GridLayoutTab(title) {

    @Inject(method = ["<init>"], at = [At("TAIL")])
    private fun addConfigButton(ci: CallbackInfo) {
        val configButton = Button.builder(
            Component.translatable("gui.roadweaver.config_button")
        ) { _ ->
            val mc = Minecraft.getInstance()
            val screen = mc.screen
            if (screen is CreateWorldScreen) {
                mc.setScreen(ConfigScreenFactoryImpl.createConfigScreen(screen as Screen))
            }
        }
            .width(210)
            .build()

        val row = if (net.minecraft.SharedConstants.getCurrentVersion().isStable) 4 else 5
        this.layout.addChild(configButton, row, 0, this.layout.newCellSettings().alignHorizontallyCenter())
    }
}
