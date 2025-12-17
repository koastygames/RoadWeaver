package net.shiroha233.roadweaver.mixin.neoforge

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.tips.LoadingTipsRenderer
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingStats
import net.shiroha233.roadweaver.generation.InitialGenManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LevelLoadingScreen::class)
abstract class LevelLoadingScreenMixin {

    @Inject(method = ["render"], at = [At("TAIL")])
    private fun `roadweaver$renderProgress`(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        ci: CallbackInfo
    ) {
        LoadingTipsRenderer.render(graphics)
        if (!InitialGenManager.isActive()) return

        val total = InitialGenManager.getTotal()
        val done = InitialGenManager.getDone()

        val generating = InitialGenManager.getGenerating()
        val failed = InitialGenManager.getFailed()
        val percent = if (total <= 0) 0 else kotlin.math.round(100.0 * done / kotlin.math.max(1, total)).toInt()

        val mc = Minecraft.getInstance()
        val font = mc.font
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight

        val title = Component.translatable("gui.roadweaver.initgen.title")
        val summary = Component.translatable("gui.roadweaver.initgen.summary", total, generating, done, failed)
        val progress = Component.translatable("gui.roadweaver.initgen.progress", done, total, percent)

        var y = sh - 60

        var x = (sw - font.width(title)) / 2
        graphics.drawString(font, title, x, y, 0xFFFFFF, false)
        y += 12

        x = (sw - font.width(summary)) / 2
        graphics.drawString(font, summary, x, y, 0xA0A0A0, false)
        y += 12

        x = (sw - font.width(progress)) / 2
        graphics.drawString(font, progress, x, y, 0xA0FFA0, false)
        y += 12

        val hitRate = TerrainSamplingStats.getCacheHitRatePercent()
        val samplesPerSec = TerrainSamplingStats.updateAndGetSamplesPerSecond()
        val totalSamples = TerrainSamplingStats.getTotalNoiseSamples()
        val debug = Component.translatable(
            "gui.roadweaver.initgen.debug",
            hitRate,
            String.format("%.0f", samplesPerSec),
            totalSamples
        )

        x = (sw - font.width(debug)) / 2
        graphics.drawString(font, debug, x, y, 0x80C0FF, false)
    }
}
