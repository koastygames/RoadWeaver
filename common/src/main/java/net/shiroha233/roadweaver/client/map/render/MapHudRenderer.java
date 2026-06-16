package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.ui.Rect;

/**
 * 地图 HUD 绘制与布局。
 */
public final class MapHudRenderer {
    private static final Component BTN_CONFIG = Component.translatable("gui.roadweaver.config_button");
    private static final Component BTN_MANUAL = Component.translatable("gui.roadweaver.map.manual_connect");

    private MapHudRenderer() {}

    public record ToolbarLayout(Rect configButton, Rect manualButton, Component configLabel, Component manualLabel) {}

    public static ToolbarLayout buildToolbar(Font font, int mapHeight, boolean manualMode) {
        Component configLabel = BTN_CONFIG;
        Rect configButton = new Rect(4, 4, font.width(configLabel) + 6, font.lineHeight + 4);

        Component manualLabel = Component.empty().append(BTN_MANUAL).append(": ")
                .append(manualMode
                        ? Component.translatable("gui.roadweaver.common.on")
                        : Component.translatable("gui.roadweaver.common.off"));
        Rect manualButton = new Rect(4, mapHeight - 4 - (font.lineHeight + 4), font.width(manualLabel) + 6, font.lineHeight + 4);
        return new ToolbarLayout(configButton, manualButton, configLabel, manualLabel);
    }

    public static void renderLegendWithBackground(GuiGraphics g, Font font, int rightBound, int startY, MapSnapshot snapshot) {
        int gap = 8;
        int lineHeight = 16;
        int itemCount = 5;
        int bgW = 140;
        int bgH = lineHeight * itemCount + 12;
        int bgX = rightBound - bgW;
        int bgY = startY - 4;

        g.fill(bgX, bgY, bgX + bgW, bgY + bgH, MapTheme.LEGEND_BG);
        MapRenderers.renderLegend(g, font, rightBound, startY, gap,
                MapTheme.COLOR_TEXT, MapTheme.COLOR_STRUCTURE,
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING,
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                snapshot.structuresCount(), snapshot.plannedCount(),
                snapshot.generatingCount(), snapshot.completedCount(), snapshot.failedCount());
    }

    public static void renderToolbarButtons(GuiGraphics g, Font font, ToolbarLayout toolbar, int mouseX, int mouseY) {
        renderTextButton(g, font, toolbar.configLabel(), toolbar.configButton(), mouseX, mouseY);
        renderTextButton(g, font, toolbar.manualLabel(), toolbar.manualButton(), mouseX, mouseY);
    }

    private static void renderTextButton(GuiGraphics g, Font font, Component label, Rect bounds, int mouseX, int mouseY) {
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), MapTheme.TOOLBAR_BUTTON_BG);
        int ty = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        g.drawString(font, label, bounds.x() + 3, ty, MapTheme.COLOR_TEXT, false);

        if (bounds.contains(mouseX, mouseY)) {
            int textW = font.width(label);
            int uy = ty + font.lineHeight + 1;
            int underline = (MapTheme.COLOR_TEXT & 0x00FFFFFF) | 0x80000000;
            g.fill(bounds.x() + 2, uy, bounds.x() + 2 + textW + 2, uy + 1, underline);
        }
    }
}