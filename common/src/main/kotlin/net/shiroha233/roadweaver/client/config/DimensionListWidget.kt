package net.shiroha233.roadweaver.client.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.Objects

class DimensionListWidget(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val onSelect: (ResourceLocation?) -> Unit
) : ContainerObjectSelectionList<DimensionListWidget.Entry>(minecraft, width, height, top, top + height, 22) {

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val x0 = getRowLeft()
        val x1 = x0 + width
        graphics.fill(x0, y0, x1, y1, 0xAA0A0A0A.toInt())
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    fun setRows(rows: List<Row>, active: ResourceLocation?) {
        clearRows()
        rows.forEach { row ->
            addEntry(RowEntry(this, row, Objects.equals(row.dimensionId, active), onSelect))
        }
    }

    fun clearRows() {
        super.clearEntries()
    }

    override fun getRowWidth(): Int = width - 20

    override fun getScrollbarPosition(): Int = getRowLeft() + getRowWidth() + 6

    data class Row(
        val dimensionId: ResourceLocation?,
        val title: Component,
        val subtitle: Component? = null
    )

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    private class RowEntry(
        private val list: DimensionListWidget,
        private val row: Row,
        private val active: Boolean,
        private val onSelect: (ResourceLocation?) -> Unit
    ) : Entry() {

        override fun render(
            graphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            val bg = when {
                active -> 0xFF3A3A3A.toInt()
                hovering -> 0xAA2A2A2A.toInt()
                else -> 0x00000000
            }
            if (bg != 0) {
                graphics.fill(left, top, left + width, top + height, bg)
            }

            val mc = Minecraft.getInstance()
            val maxTextWidth = (width - 24).coerceAtLeast(10)

            if (active) {
                graphics.drawString(mc.font, "✓", left + 6, top + 3, 0x55FF55, false)
            }

            val textX = left + 16

            val titleStr = row.title.string
            val titleMax = (maxTextWidth - mc.font.width("…")).coerceAtLeast(0)
            val titleCut = mc.font.plainSubstrByWidth(titleStr, titleMax)
            val titleFinal = if (mc.font.width(titleStr) > maxTextWidth) (titleCut + "…") else titleStr
            graphics.drawString(mc.font, titleFinal, textX, top + 3, 0xFFFFFF, false)

            row.subtitle?.let { sub ->
                val subStr = sub.string
                val subMax = (maxTextWidth - mc.font.width("…")).coerceAtLeast(0)
                val subCut = mc.font.plainSubstrByWidth(subStr, subMax)
                val subFinal = if (mc.font.width(subStr) > maxTextWidth) (subCut + "…") else subStr
                graphics.drawString(mc.font, subFinal, textX, top + 12, 0x888888, false)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                onSelect(row.dimensionId)
                return true
            }
            return false
        }

        override fun children(): List<net.minecraft.client.gui.components.events.GuiEventListener> = emptyList()

        override fun narratables(): List<net.minecraft.client.gui.narration.NarratableEntry> = emptyList()
    }
}
