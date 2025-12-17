package net.shiroha233.roadweaver.client.map.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.map.MapTheme
import java.util.ArrayList

/**
 * 通用右键菜单组件 - 支持动态添加菜单项
 */
class ContextMenu {
    companion object {
        const val PADDING: Int = 6
        const val ITEM_HEIGHT: Int = 16
        const val SEPARATOR_HEIGHT: Int = 8

        data class Item(val label: Component, val action: Runnable?, val enabled: Boolean, val isSeparator: Boolean) {
            companion object {
                @JvmStatic
                fun of(label: Component, action: Runnable): Item = Item(label, action, true, false)

                @JvmStatic
                fun disabled(label: Component): Item = Item(label, null, false, false)

                @JvmStatic
                fun createSeparator(): Item = Item(Component.empty(), null, false, true)
            }
        }
    }

    private var anchorX: Int = 0
    private var anchorY: Int = 0
    private var bounds: Rect? = null
    private val items: MutableList<Item> = ArrayList()
    private var open: Boolean = false
    private var hoverIndex: Int = -1

    fun open(x: Int, y: Int) {
        this.anchorX = x
        this.anchorY = y
        this.open = true
        this.hoverIndex = -1
    }

    fun close() {
        this.open = false
        this.hoverIndex = -1
    }

    fun isOpen(): Boolean = open

    fun clearItems() {
        items.clear()
    }

    fun addItem(label: Component, action: Runnable) {
        items.add(Item.of(label, action))
    }

    fun addItem(item: Item) {
        items.add(item)
    }

    fun addSeparator() {
        items.add(Item.createSeparator())
    }

    fun layout(font: Font, screenW: Int, screenH: Int) {
        var w = 0
        var h = PADDING * 2
        for (it in items) {
            if (it.isSeparator) {
                h += SEPARATOR_HEIGHT
            } else {
                w = kotlin.math.max(w, font.width(it.label))
                h += ITEM_HEIGHT
            }
        }
        w += PADDING * 2 + 12
        val raw = Rect(anchorX + 4, anchorY, w, h)
        bounds = raw.clampToScreen(screenW, screenH, 4)
    }

    fun render(g: GuiGraphics, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
        if (!open || items.isEmpty()) return
        layout(font, screenW, screenH)

        val b = bounds ?: return
        val x = b.x
        val y = b.y
        val w = b.width
        val h = b.height

        // 阴影
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000)
        // 边框
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, MapTheme.MENU_BORDER)
        // 背景
        g.fill(x, y, x + w, y + h, MapTheme.MENU_BG)

        updateHoverIndex(mouseX, mouseY)

        var ty = y + PADDING
        for (i in items.indices) {
            val it = items[i]
            if (it.isSeparator) {
                val lineY = ty + SEPARATOR_HEIGHT / 2
                g.fill(x + 4, lineY, x + w - 4, lineY + 1, 0x40FFFFFF)
                ty += SEPARATOR_HEIGHT
            } else {
                if (i == hoverIndex && it.enabled) {
                    g.fill(x + 2, ty, x + w - 2, ty + ITEM_HEIGHT, MapTheme.MENU_HOVER)
                }
                val textColor = if (it.enabled) MapTheme.MENU_TEXT else 0xFF808080.toInt()
                g.drawString(font, it.label, x + PADDING, ty + (ITEM_HEIGHT - font.lineHeight) / 2, textColor, false)
                ty += ITEM_HEIGHT
            }
        }
    }

    private fun updateHoverIndex(mouseX: Int, mouseY: Int) {
        hoverIndex = -1
        val b = bounds ?: return
        if (!b.contains(mouseX.toDouble(), mouseY.toDouble())) return

        var y = b.y + PADDING
        for (i in items.indices) {
            val it = items[i]
            val itemH = if (it.isSeparator) SEPARATOR_HEIGHT else ITEM_HEIGHT
            if (mouseY >= y && mouseY < y + itemH) {
                if (!it.isSeparator) hoverIndex = i
                return
            }
            y += itemH
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!open) return false
        if (button != 0) {
            close()
            return true
        }

        val b = bounds
        if (b == null || !b.contains(mouseX, mouseY)) {
            close()
            return true
        }

        if (hoverIndex in items.indices) {
            val it = items[hoverIndex]
            if (it.enabled && it.action != null) {
                close()
                it.action.run()
                return true
            }
        }

        close()
        return true
    }

    fun updateMousePos(mouseX: Int, mouseY: Int) {
        if (open && bounds != null) {
            updateHoverIndex(mouseX, mouseY)
        }
    }
}
