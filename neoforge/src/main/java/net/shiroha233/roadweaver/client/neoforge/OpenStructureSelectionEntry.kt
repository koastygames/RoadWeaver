package net.shiroha233.roadweaver.client.neoforge

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.math.Rectangle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.config.StructureSelectionScreen
import java.util.Collections
import java.util.Optional

/**
 * 打开结构选择界面的配置条目
 */
@Suppress("MemberVisibilityCanBePrivate")
class OpenStructureSelectionEntry : AbstractConfigListEntry<Void>(Component.translatable("config.roadweaver.open_structure_selection"), false) {
    private var lastArea: Rectangle? = null

    private class DummyParentScreen : net.minecraft.client.gui.screens.Screen(Component.empty())

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
        val color = preferredTextColor
        val textY = y + (entryHeight - font.lineHeight) / 2
        g.drawString(font, label, x + 4, textY, color, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val area = lastArea
        if (button == 0 && area != null && area.contains(mouseX, mouseY)) {
            val mc = Minecraft.getInstance()
            mc.setScreen(StructureSelectionScreen(mc.screen ?: DummyParentScreen()))
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
