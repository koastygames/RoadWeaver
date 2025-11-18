package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SimpleTextInputScreen extends Screen {
    private final Component titleText;
    private final String initial;
    private final Consumer<String> onSubmit;

    private EditBox box;

    public SimpleTextInputScreen(Component titleText, String initial, Consumer<String> onSubmit) {
        super(titleText);
        this.titleText = titleText;
        this.initial = initial != null ? initial : "";
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        int w = 240;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 20;
        box = new EditBox(this.font, x, y, w, 20, titleText);
        box.setMaxLength(512);
        box.setValue(initial);
        this.addRenderableWidget(box);

        int bw = 80;
        int by = y + 28;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.ok"), b -> submit())
                .bounds(x, by, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.cancel"), b -> cancel())
                .bounds(x + w - bw, by, bw, 20).build());

        this.setInitialFocus(box);
    }

    private void submit() {
        if (onSubmit != null) onSubmit.accept(box.getValue());
        Minecraft.getInstance().setScreen(null);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            submit();
            return true;
        }
        if (keyCode == 256) { // ESC
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
