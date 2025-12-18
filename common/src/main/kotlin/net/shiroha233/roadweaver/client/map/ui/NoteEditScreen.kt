package net.shiroha233.roadweaver.client.map.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import java.util.ArrayList

/**
 * 地图笔记编辑界面 - 采用类似原版书与笔的风格
 */
class NoteEditScreen(
    private val targetPos: BlockPos,
    private val parent: Screen
) : Screen(Component.translatable("gui.roadweaver.map.note.title")) {

    companion object {
        private val BOOK_TEXTURE: ResourceLocation = ResourceLocation("minecraft", "textures/gui/book.png")

        // 书本尺寸（原版书本纹理参数）
        private const val BOOK_WIDTH = 192
        private const val BOOK_HEIGHT = 192
        private const val TEXT_WIDTH = 114
        private const val TEXT_X_OFFSET = 36
        private const val TEXT_Y_OFFSET = 32
        private const val MAX_LINES = 14
        private const val LINE_HEIGHT = 9
    }

    private val lines: MutableList<String> = ArrayList()

    private var cursorLine = 0
    private var cursorPos = 0
    private var bookX = 0
    private var bookY = 0
    private var lastBlink = 0L
    private var cursorVisible = true

    init {
        val existing = ClientMapNotes.getNotes(targetPos)
        if (existing.isNotEmpty()) {
            lines.addAll(existing)
        } else {
            lines.add("")
        }
    }

    override fun init() {
        bookX = (this.width - BOOK_WIDTH) / 2
        bookY = (this.height - BOOK_HEIGHT) / 2

        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.common.save")) { save() }
                .bounds(bookX + BOOK_WIDTH / 2 - 50, bookY + BOOK_HEIGHT + 4, 100, 20)
                .build()
        )
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, this.width, this.height, 0x90000000.toInt())

        g.blit(BOOK_TEXTURE, bookX, bookY, 0f, 0f, BOOK_WIDTH, BOOK_HEIGHT, 256, 256)

        val alias = ClientMapNotes.getAlias(targetPos)
        val titleStr = if (!alias.isNullOrEmpty()) alias else String.format("(%d, %d)", targetPos.x, targetPos.z)
        g.drawString(this.font, titleStr, bookX + TEXT_X_OFFSET, bookY + 18, 0x000000, false)

        val textX = bookX + TEXT_X_OFFSET
        val textY = bookY + TEXT_Y_OFFSET

        for (i in 0 until kotlin.math.min(lines.size, MAX_LINES)) {
            val line = lines[i]
            g.drawString(this.font, line, textX, textY + i * LINE_HEIGHT, 0x000000, false)
        }

        updateCursorBlink()
        if (cursorVisible && cursorLine < lines.size) {
            val currentLine = lines[cursorLine]
            val prefix = currentLine.substring(0, kotlin.math.min(cursorPos, currentLine.length))
            val cursorX = textX + this.font.width(prefix)
            val cursorY = textY + cursorLine * LINE_HEIGHT
            g.fill(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT, 0xFF000000.toInt())
        }

        super.render(g, mouseX, mouseY, partialTick)
    }

    private fun updateCursorBlink() {
        val now = System.currentTimeMillis()
        if (now - lastBlink > 500) {
            cursorVisible = !cursorVisible
            lastBlink = now
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // ESC 取消
        if (keyCode == 256) {
            this.minecraft?.setScreen(parent)
            return true
        }

        // Enter 换行
        if (keyCode == 257 || keyCode == 335) {
            if (lines.size < MAX_LINES) {
                val current = lines[cursorLine]
                val before = current.substring(0, kotlin.math.min(cursorPos, current.length))
                val after = current.substring(kotlin.math.min(cursorPos, current.length))
                lines[cursorLine] = before
                lines.add(cursorLine + 1, after)
                cursorLine++
                cursorPos = 0
            }
            return true
        }

        // Backspace 删除
        if (keyCode == 259) {
            if (cursorPos > 0) {
                val current = lines[cursorLine]
                val newLine = current.substring(0, cursorPos - 1) + current.substring(cursorPos)
                lines[cursorLine] = newLine
                cursorPos--
            } else if (cursorLine > 0) {
                val current = lines.removeAt(cursorLine)
                cursorLine--
                val prev = lines[cursorLine]
                cursorPos = prev.length
                lines[cursorLine] = prev + current
            }
            return true
        }

        // Delete 删除光标后字符
        if (keyCode == 261) {
            val current = lines[cursorLine]
            if (cursorPos < current.length) {
                val newLine = current.substring(0, cursorPos) + current.substring(cursorPos + 1)
                lines[cursorLine] = newLine
            } else if (cursorLine < lines.size - 1) {
                val next = lines.removeAt(cursorLine + 1)
                lines[cursorLine] = current + next
            }
            return true
        }

        // 方向键
        when (keyCode) {
            263 -> { // Left
                if (cursorPos > 0) cursorPos--
                else if (cursorLine > 0) {
                    cursorLine--
                    cursorPos = lines[cursorLine].length
                }
                return true
            }

            262 -> { // Right
                val current = lines[cursorLine]
                if (cursorPos < current.length) cursorPos++
                else if (cursorLine < lines.size - 1) {
                    cursorLine++
                    cursorPos = 0
                }
                return true
            }

            265 -> { // Up
                if (cursorLine > 0) {
                    cursorLine--
                    cursorPos = kotlin.math.min(cursorPos, lines[cursorLine].length)
                }
                return true
            }

            264 -> { // Down
                if (cursorLine < lines.size - 1) {
                    cursorLine++
                    cursorPos = kotlin.math.min(cursorPos, lines[cursorLine].length)
                }
                return true
            }

            268 -> { // Home
                cursorPos = 0
                return true
            }

            269 -> { // End
                cursorPos = lines[cursorLine].length
                return true
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(c: Char, modifiers: Int): Boolean {
        if (Character.isISOControl(c)) return false

        val current = lines[cursorLine]
        val newLine = current.substring(0, cursorPos) + c + current.substring(cursorPos)
        if (this.font.width(newLine) <= TEXT_WIDTH) {
            lines[cursorLine] = newLine
            cursorPos++
        }
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val textX = bookX + TEXT_X_OFFSET
        val textY = bookY + TEXT_Y_OFFSET

        if (mouseX >= textX && mouseX < textX + TEXT_WIDTH && mouseY >= textY && mouseY < textY + MAX_LINES * LINE_HEIGHT) {
            val clickedLine = ((mouseY - textY) / LINE_HEIGHT).toInt()
            if (clickedLine < lines.size) {
                cursorLine = clickedLine
                val line = lines[cursorLine]
                val relX = (mouseX - textX).toInt()

                cursorPos = 0
                for (i in 0..line.length) {
                    if (this.font.width(line.substring(0, i)) >= relX) {
                        cursorPos = kotlin.math.max(0, i - 1)
                        break
                    }
                    cursorPos = i
                }
            }
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun save() {
        ClientMapNotes.clearNotes(targetPos)
        for (line in lines) {
            if (line.isNotEmpty()) {
                ClientMapNotes.addNote(targetPos, line)
            }
        }
        this.minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
