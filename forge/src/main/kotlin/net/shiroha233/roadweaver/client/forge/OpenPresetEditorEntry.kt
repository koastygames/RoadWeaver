package net.shiroha233.roadweaver.client.forge

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.math.Rectangle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.config.RoadPresetEditorScreen
import java.util.Collections
import java.util.Optional

@Suppress("MemberVisibilityCanBePrivate")
class OpenPresetEditorEntry : AbstractConfigListEntry<Void>(Component.translatable("config.roadweaver.open_preset_editor"), false) {
    private var lastArea: Rectangle? = null

    override fun getValue(): Void? = null

    fun setValue(value: Void?) {
        // no-op: this entry only acts as a button
    }

    override fun getDefaultValue(): Optional<Void> {
        return Optional.empty()
    }

    override fun render(
        g: GuiGraphics,
        index: Int,
        y: Int,
        x: Int,
        entryWidth: Int,
        entryHeight: Int,
        mouseX: Int,
        mouseY: Int,
        isHovered: Boolean,
        delta: Float
    ) {
        super.render(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta)
        lastArea = getEntryArea(x, y, entryWidth, entryHeight)

        val font: Font = Minecraft.getInstance().font
        val label = displayedFieldName
        if (label != null) {
            val color = preferredTextColor
            val textY = y + (entryHeight - font.lineHeight) / 2
            g.drawString(font, label, x + 4, textY, color, false)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val area = lastArea
        if (button == 0 && area != null && area.contains(mouseX, mouseY)) {
            val mc = Minecraft.getInstance()
            mc.setScreen(RoadPresetEditorScreen(mc.screen))
            return true
        }
        return false
    }

    override fun children(): List<GuiEventListener> {
        return Collections.emptyList()
    }

    override fun narratables(): List<NarratableEntry> {
        return Collections.emptyList()
    }
}
