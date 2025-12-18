package net.shiroha233.roadweaver.client.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.network.chat.Component

/**
 * 预设/群系列表组件。
 *
 * SRP：只负责展示滚动列表与点击选中，不关心业务数据如何保存。
 */
class RoadPresetListWidget(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val onSelect: (Int) -> Unit
) : ContainerObjectSelectionList<RoadPresetListWidget.Entry>(minecraft, width, height, top, top + height, 22) {

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 列表区域底色加深，避免背景模糊透出造成“层级不对”的错觉
        val x0 = getRowLeft()
        val x1 = x0 + width
        graphics.fill(x0, y0, x1, y1, 0xAA0A0A0A.toInt())
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    fun setRows(rows: List<Row>, activeIndex: Int) {
        clearRows()
        rows.forEachIndexed { idx, row ->
            addEntry(RowEntry(this, idx, row, idx == activeIndex, onSelect))
        }
    }

    fun clearRows() {
        super.clearEntries()
    }

    override fun getRowWidth(): Int = width - 20

    override fun getScrollbarPosition(): Int = getRowLeft() + width - 6

    data class Row(
        val title: Component,
        val subtitle: Component? = null
    )

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    private class RowEntry(
        private val list: RoadPresetListWidget,
        private val index: Int,
        private val row: Row,
        private val active: Boolean,
        private val onSelect: (Int) -> Unit
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

            // 22px 行高内绘制两行文本：需要上移并裁剪，避免溢出选框
            val maxTextWidth = (width - 12).coerceAtLeast(10)

            val titleStr = row.title.string
            val titleMax = (maxTextWidth - mc.font.width("…")).coerceAtLeast(0)
            val titleCut = mc.font.plainSubstrByWidth(titleStr, titleMax)
            val titleFinal = if (mc.font.width(titleStr) > maxTextWidth) (titleCut + "…") else titleStr
            graphics.drawString(mc.font, titleFinal, left + 6, top + 3, 0xFFFFFF, false)

            row.subtitle?.let { sub ->
                val subStr = sub.string
                val subMax = (maxTextWidth - mc.font.width("…")).coerceAtLeast(0)
                val subCut = mc.font.plainSubstrByWidth(subStr, subMax)
                val subFinal = if (mc.font.width(subStr) > maxTextWidth) (subCut + "…") else subStr
                graphics.drawString(mc.font, subFinal, left + 6, top + 12, 0x888888, false)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                onSelect(index)
                return true
            }
            return false
        }

        override fun children(): List<net.minecraft.client.gui.components.events.GuiEventListener> = emptyList()

        override fun narratables(): List<net.minecraft.client.gui.narration.NarratableEntry> = emptyList()
    }
}
