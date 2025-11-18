package net.shiroha233.roadweaver.client.map.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ContextMenu {
    public static final int PADDING = 6;
    public static final int ITEM_HEIGHT = 14;

    public static final class Item {
        public final Component label;
        public final Runnable action;
        public Item(Component label, Runnable action) {
            this.label = label; this.action = action;
        }
    }

    private int x;
    private int y;
    private int width;
    private int height;
    private final List<Item> items = new ArrayList<>();
    private boolean open = false;

    public void open(int x, int y) {
        this.x = x;
        this.y = y;
        this.open = true;
    }

    public void close() { this.open = false; }
    public boolean isOpen() { return open; }

    public void clearItems() { items.clear(); }
    public void addItem(Component label, Runnable action) { items.add(new Item(label, action)); }

    public void layout(Font font) {
        int w = 0;
        for (Item it : items) {
            w = Math.max(w, font.width(it.label));
        }
        this.width = w + PADDING * 2;
        this.height = PADDING + items.size() * ITEM_HEIGHT + PADDING;
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH) {
        if (!open) return;
        layout(font);
        int rx = Math.min(x, Math.max(0, screenW - width));
        int ry = Math.min(y, Math.max(0, screenH - height));
        int bg = 0xEE2B2B2B;
        int border = 0xFFFFFFFF;
        g.fill(rx - 1, ry - 1, rx + width + 1, ry + height + 1, border);
        g.fill(rx, ry, rx + width, ry + height, bg);
        int ty = ry + PADDING;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            g.drawString(font, it.label, rx + PADDING, ty + 3, 0xFFFFFFFF, false);
            ty += ITEM_HEIGHT;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button != 0) return false;
        int rx = x;
        int ry = y;
        if (mouseX < rx || mouseX > rx + width || mouseY < ry || mouseY > ry + height) {
            close();
            return true;
        }
        int idx = (int)((mouseY - ry - PADDING) / ITEM_HEIGHT);
        if (idx >= 0 && idx < items.size()) {
            Item it = items.get(idx);
            close();
            if (it.action != null) it.action.run();
            return true;
        }
        close();
        return true;
    }
}
