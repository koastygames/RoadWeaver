/* 文件职责：在道路地图内部显示短暂的主动采样反馈文本。 */
package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.shiroha233.roadweaver.client.map.MapTheme;

import java.util.List;

/**
 * 主动采样操作的地图内反馈层。
 */
public final class MapSamplingNoticeOverlayRenderer {
    private static final int HORIZONTAL_PADDING = 7;
    private static final int VERTICAL_PADDING = 5;
    private static final int OUTER_MARGIN = 6;
    private static final int MAX_TEXT_WIDTH = 360;

    private MapSamplingNoticeOverlayRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              Component message,
                              int left,
                              int top,
                              int right,
                              int bottom) {
        if (message == null || right <= left || bottom <= top) return;

        int availableWidth = Math.max(1, right - left - OUTER_MARGIN * 2);
        int maxTextWidth = Math.min(MAX_TEXT_WIDTH,
                Math.max(1, availableWidth - HORIZONTAL_PADDING * 2));
        List<FormattedCharSequence> lines = font.split(message, maxTextWidth);
        if (lines.isEmpty()) return;

        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = Math.min(availableWidth, textWidth + HORIZONTAL_PADDING * 2);
        int boxHeight = lines.size() * font.lineHeight + VERTICAL_PADDING * 2;
        int minimumY = top + MapTheme.TOOLBAR_BUTTON_HEIGHT + OUTER_MARGIN + 8;
        int maximumY = Math.max(top + OUTER_MARGIN, bottom - boxHeight - OUTER_MARGIN);
        int boxX = left + (right - left - boxWidth) / 2;
        int boxY = Math.min(Math.max(top + OUTER_MARGIN, minimumY), maximumY);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, MapTheme.MAP_SAMPLING_LABEL_BG);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font,
                    lines.get(index),
                    boxX + HORIZONTAL_PADDING,
                    boxY + VERTICAL_PADDING + index * font.lineHeight,
                    MapTheme.COLOR_MAP_SAMPLING_TEXT,
                    false);
        }
    }
}
