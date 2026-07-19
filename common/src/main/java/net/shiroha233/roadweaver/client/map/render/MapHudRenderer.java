/* 文件职责：绘制道路地图工具栏、图例、加载与采样会话状态。 */
package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapLoadSession;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.ui.Rect;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.Locale;

/**
 * 地图 HUD 绘制与布局。
 */
public final class MapHudRenderer {
    private static final Component BTN_CONFIG = Component.translatable("gui.roadweaver.config_button");
    private static final Component BTN_MANUAL = Component.translatable("gui.roadweaver.map.manual_connect");
    private static final Component BTN_SAMPLE_MAP = Component.translatable("gui.roadweaver.map.sample.current_view");

    private MapHudRenderer() {}

    public record ToolbarLayout(Rect configButton,
                                Rect sampleButton,
                                Rect manualButton,
                                Component configLabel,
                                Component sampleLabel,
                                Component manualLabel,
                                boolean sampleAvailable,
                                boolean sampleRunning) {
        public boolean contains(double x, double y) {
            return configButton.contains(x, y)
                    || sampleButton.contains(x, y)
                    || manualButton.contains(x, y);
        }
    }

    public static ToolbarLayout buildToolbar(Font font,
                                             int mapHeight,
                                             boolean manualMode,
                                             boolean sampleAvailable,
                                             MapSamplingSnapshot samplingSnapshot) {
        Component configLabel = BTN_CONFIG;
        Rect configButton = new Rect(4, 4, font.width(configLabel) + 6, font.lineHeight + 4);

        boolean sampleRunning = samplingSnapshot != null && samplingSnapshot.active();
        int percent = samplingSnapshot == null ? 0 : samplingSnapshot.percent();
        Component sampleLabel = sampleRunning
                ? Component.translatable("gui.roadweaver.map.sample.progress", percent)
                : BTN_SAMPLE_MAP;
        int sampleWidth = Math.max(
                font.width(BTN_SAMPLE_MAP),
                font.width(Component.translatable("gui.roadweaver.map.sample.progress", 100))) + 6;
        Rect sampleButton = new Rect(
                configButton.right() + MapTheme.TOOLBAR_BUTTON_GAP,
                configButton.y(),
                sampleWidth,
                configButton.height());

        Component manualLabel = Component.empty().append(BTN_MANUAL).append(": ")
                .append(manualMode
                        ? Component.translatable("gui.roadweaver.common.on")
                        : Component.translatable("gui.roadweaver.common.off"));
        Rect manualButton = new Rect(4, mapHeight - 4 - (font.lineHeight + 4), font.width(manualLabel) + 6, font.lineHeight + 4);
        return new ToolbarLayout(
                configButton,
                sampleButton,
                manualButton,
                configLabel,
                sampleLabel,
                manualLabel,
                sampleAvailable,
                sampleRunning);
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

    public static void renderLoadingStatus(GuiGraphics g, Font font, ToolbarLayout toolbar, MapLoadSession loadSession) {
        if (loadSession == null) return;
        String phaseText;
        if (loadSession.lastPhase() == null) {
            phaseText = "starting";
        } else {
            phaseText = loadSession.lastPhase().name().toLowerCase();
        }
        Component line1 = Component.literal("加载中 " + loadSession.completedResponses() + "/" + loadSession.totalResponses());
        Component line2 = Component.literal("阶段 " + phaseText + " · " + loadSession.elapsedMs() + "ms");
        int x = toolbar.sampleButton().right() + 8;
        int y = toolbar.configButton().y() + 1;
        int width = Math.max(font.width(line1), font.width(line2)) + 8;
        int height = font.lineHeight * 2 + 6;
        g.fill(x - 3, y - 2, x - 3 + width, y - 2 + height, MapTheme.LEGEND_BG);
        g.drawString(font, line1, x, y, MapTheme.COLOR_TEXT, false);
        g.drawString(font, line2, x, y + font.lineHeight + 2, MapTheme.COLOR_TEXT, false);
    }

    public static void renderSamplingStatus(GuiGraphics graphics,
                                            Font font,
                                            ToolbarLayout toolbar,
                                            TerrainSamplingSessionSnapshot session,
                                            boolean loadingVisible) {
        if (session == null) return;
        String backend = session.backend().isBlank() ? "-" : session.backend();
        String device = session.device().isBlank() ? "" : " / " + session.device();
        Component modes = Component.translatable(
                "gui.roadweaver.map.sampling_modes",
                samplingModeLabel(session.configuredMode()),
                samplingModeLabel(session.effectiveMode()));
        Component runtime = Component.translatable(
                "gui.roadweaver.map.sampling_backend",
                backend + device);
        Component fallback = session.hasFallbackReason()
                ? Component.translatable("gui.roadweaver.map.sampling_fallback", session.fallbackReason())
                : null;
        int x = toolbar.sampleButton().right() + 8;
        int y = toolbar.configButton().y() + 1 + (loadingVisible ? font.lineHeight * 2 + 10 : 0);
        int maxWidth = 280;
        String modesText = font.plainSubstrByWidth(modes.getString(), maxWidth);
        String runtimeText = font.plainSubstrByWidth(runtime.getString(), maxWidth);
        String fallbackText = fallback == null ? null : font.plainSubstrByWidth(fallback.getString(), maxWidth);
        int lines = fallbackText == null ? 2 : 3;
        int width = Math.max(font.width(modesText), font.width(runtimeText));
        if (fallbackText != null) width = Math.max(width, font.width(fallbackText));
        int height = font.lineHeight * lines + 6 + (lines - 1) * 2;
        graphics.fill(x - 3, y - 2, x + width + 5, y - 2 + height, MapTheme.LEGEND_BG);
        graphics.drawString(font, modesText, x, y, MapTheme.COLOR_TEXT, false);
        graphics.drawString(font, runtimeText, x, y + font.lineHeight + 2, MapTheme.COLOR_TEXT, false);
        if (fallbackText != null) {
            graphics.drawString(font, fallbackText, x, y + (font.lineHeight + 2) * 2, MapTheme.COLOR_FAILED, false);
        }
    }

    private static Component samplingModeLabel(TerrainSamplingMode mode) {
        return Component.translatable(
                "config.roadweaver.terrain_sampling_mode.option."
                        + mode.name().toLowerCase(Locale.ROOT));
    }

    public static void renderToolbarButtons(GuiGraphics g, Font font, ToolbarLayout toolbar, int mouseX, int mouseY) {
        renderTextButton(g, font, toolbar.configLabel(), toolbar.configButton(), mouseX, mouseY, true);
        renderTextButton(g, font, toolbar.sampleLabel(), toolbar.sampleButton(), mouseX, mouseY,
                toolbar.sampleAvailable() && !toolbar.sampleRunning());
        renderTextButton(g, font, toolbar.manualLabel(), toolbar.manualButton(), mouseX, mouseY, true);
    }

    public static void renderToolbarTooltip(GuiGraphics graphics,
                                            Font font,
                                            ToolbarLayout toolbar,
                                            int mouseX,
                                            int mouseY) {
        if (!toolbar.sampleButton().contains(mouseX, mouseY)) return;
        Component tooltip;
        if (toolbar.sampleRunning()) {
            tooltip = Component.translatable("gui.roadweaver.map.sample.running");
        } else if (!toolbar.sampleAvailable()) {
            tooltip = Component.translatable("gui.roadweaver.map.sample.unavailable");
        } else {
            tooltip = Component.translatable("gui.roadweaver.map.sample.tooltip");
        }
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static void renderTextButton(GuiGraphics g,
                                         Font font,
                                         Component label,
                                         Rect bounds,
                                         int mouseX,
                                         int mouseY,
                                         boolean enabled) {
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), enabled
                ? MapTheme.TOOLBAR_BUTTON_BG
                : MapTheme.TOOLBAR_BUTTON_DISABLED_BG);
        int ty = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        g.drawString(font, label, bounds.x() + 3, ty, enabled
                ? MapTheme.COLOR_TEXT
                : MapTheme.TOOLBAR_BUTTON_DISABLED_TEXT, false);

        if (enabled && bounds.contains(mouseX, mouseY)) {
            int textW = font.width(label);
            int uy = ty + font.lineHeight + 1;
            int underline = (MapTheme.COLOR_TEXT & 0x00FFFFFF) | 0x80000000;
            g.fill(bounds.x() + 2, uy, bounds.x() + 2 + textW + 2, uy + 1, underline);
        }
    }
}
