package net.shiroha233.roadweaver.client.map.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.Consumer

class SimpleTextInputScreen(
    private val titleText: Component,
    initial: String?,
    private val onSubmit: Consumer<String>?,
    private val parent: Screen? = null
) : Screen(titleText) {

    private val initialValue: String = initial ?: ""

    private var box: EditBox? = null

    constructor(titleText: Component, initial: String?, onSubmit: Consumer<String>?) : this(titleText, initial, onSubmit, null)

    override fun init() {
        val w = 240
        val x = (this.width - w) / 2
        val y = this.height / 2 - 20

        box = EditBox(this.font, x, y, w, 20, titleText).also {
            it.setMaxLength(512)
            it.value = initialValue
            this.addRenderableWidget(it)
            this.setInitialFocus(it)
        }

        val bw = 80
        val by = y + 28
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.common.ok")) { submit() }
                .bounds(x, by, bw, 20)
                .build()
        )
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.roadweaver.common.cancel")) { cancel() }
                .bounds(x + w - bw, by, bw, 20)
                .build()
        )
    }

    private fun submit() {
        val value = box?.value ?: ""
        onSubmit?.accept(value)
        Minecraft.getInstance().setScreen(parent)
    }

    private fun cancel() {
        Minecraft.getInstance().setScreen(parent)
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, this.width, this.height, 0x90000000.toInt())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 257 || keyCode == 335) { // Enter
            submit()
            return true
        }
        if (keyCode == 256) { // ESC
            cancel()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
