package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 简单文本输入界面。
 */
public class SimpleTextInputScreen extends Screen {
    private final Component titleText;
    private final String initial;
    private final Consumer<String> onSubmit;
    private final Screen parent;

    private EditBox box;

    public SimpleTextInputScreen(Component titleText, String initial, Consumer<String> onSubmit) {
        this(titleText, initial, onSubmit, null);
    }

    public SimpleTextInputScreen(Component titleText, String initial, Consumer<String> onSubmit, Screen parent) {
        super(titleText);
        this.titleText = titleText;
        this.initial = initial != null ? initial : "";
        this.onSubmit = onSubmit;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = 240;
        int x = (this.width - width) / 2;
        int y = this.height / 2 - 20;
        this.box = new EditBox(this.font, x, y, width, 20, titleText);
        this.box.setMaxLength(512);
        this.box.setValue(initial);
        this.addRenderableWidget(box);

        int buttonWidth = 80;
        int buttonY = y + 28;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.ok"), button -> submit())
                .bounds(x, buttonY, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.cancel"), button -> cancel())
                .bounds(x + width - buttonWidth, buttonY, buttonWidth, 20)
                .build());

        this.setInitialFocus(box);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.titleText, this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
    }

    private void submit() {
        if (onSubmit != null) {
            onSubmit.accept(box.getValue());
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        if (keyCode == 256) {
            cancel();
            return true;
        }
        return super.keyPressed(event);
    }
}
