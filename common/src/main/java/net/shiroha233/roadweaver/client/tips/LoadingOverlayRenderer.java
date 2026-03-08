package net.shiroha233.roadweaver.client.tips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats;

public final class LoadingOverlayRenderer {
    private static final int PANEL_BG = 0xB0141822;
    private static final int PANEL_BG_ALT = 0xCC0D1017;
    private static final int PANEL_BORDER = 0x90E4C46A;
    private static final int PANEL_SHADOW = 0x50000000;
    private static final int TEXT_TITLE = 0xFFF6E7B0;
    private static final int TEXT_PRIMARY = 0xFFF3F4F6;
    private static final int TEXT_MUTED = 0xFFB7C0D1;
    private static final int TEXT_SUCCESS = 0xFF9BE38B;
    private static final int TEXT_DEBUG = 0xFF8BC5FF;
    private static final int BAR_BG = 0x70000000;
    private static final int BAR_FILL = 0xFF70C971;
    private static final int MARGIN = 12;
    private static final int PADDING = 8;
    private static final int LINE_GAP = 3;
    private static final int PANEL_GAP = 8;
    private static final int MIN_PROGRESS_WIDTH = 220;

    private LoadingOverlayRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        ModConfig config = ConfigService.get();
        boolean tipsEnabled = config == null || config.client().loadingTipsEnabled();
        boolean progressEnabled = config == null || config.loadingProgressEnabled();

        if (tipsEnabled) {
            renderTipPanel(graphics, mc);
        }

        if (progressEnabled && InitialGenManager.isActive()) {
            renderProgressPanel(graphics, mc);
        }
    }

    private static void renderTipPanel(GuiGraphics graphics, Minecraft mc) {
        Font font = mc.font;
        Component tip = LoadingTipsRenderer.getCurrentTip();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int width = Math.min(sw - MARGIN * 2, Math.max(190, font.width(tip) + PADDING * 2));
        int height = font.lineHeight * 2 + PADDING * 2 + LINE_GAP;
        int x = sw - width - MARGIN;
        int y = sh - height - MARGIN;

        drawPanel(graphics, x, y, width, height);
        graphics.drawString(font, Component.translatable("gui.roadweaver.initgen.title"), x + PADDING, y + PADDING, TEXT_TITLE, false);
        graphics.drawString(font, tip, x + PADDING, y + PADDING + font.lineHeight + LINE_GAP, TEXT_PRIMARY, false);
    }

    private static void renderProgressPanel(GuiGraphics graphics, Minecraft mc) {
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();

        int total = InitialGenManager.getTotal();
        int done = InitialGenManager.getDone();
        int generating = InitialGenManager.getGenerating();
        int failed = InitialGenManager.getFailed();
        int percent = total <= 0 ? 0 : (int) Math.round(100.0D * done / Math.max(1, total));

        Component title = Component.translatable("gui.roadweaver.initgen.title");
        Component summary = Component.translatable("gui.roadweaver.initgen.summary", total, generating, done, failed);
        Component progress = Component.translatable("gui.roadweaver.initgen.progress", done, total, percent);
        Component debug = Component.translatable(
                "gui.roadweaver.initgen.debug",
                TerrainSamplingStats.getCacheHitRatePercent(),
                String.format("%.0f", TerrainSamplingStats.updateAndGetSamplesPerSecond()),
                TerrainSamplingStats.getTotalNoiseSamples());
        Component debug2 = Component.translatable(
                "gui.roadweaver.initgen.debug2",
                AccurateSamplingStats.getCacheHitRatePercent(),
                String.format("%.0f", AccurateSamplingStats.updateAndGetSamplesPerSecond()),
                AccurateSamplingStats.getTotalBaseHeightSamples());

        int width = Math.max(
                MIN_PROGRESS_WIDTH,
                Math.max(
                        font.width(title),
                        Math.max(
                                font.width(summary),
                                Math.max(font.width(progress), Math.max(font.width(debug), font.width(debug2))))))
                + PADDING * 2;
        width = Math.min(width, sw - MARGIN * 2);

        int barHeight = 8;
        int textLines = 5;
        int height = PADDING * 2 + textLines * font.lineHeight + (textLines - 1) * LINE_GAP + barHeight + PANEL_GAP;
        int x = sw - width - MARGIN;
        int y = MARGIN;

        drawPanel(graphics, x, y, width, height);

        int textX = x + PADDING;
        int lineY = y + PADDING;
        graphics.drawString(font, title, textX, lineY, TEXT_TITLE, false);
        lineY += font.lineHeight + LINE_GAP;
        graphics.drawString(font, summary, textX, lineY, TEXT_MUTED, false);
        lineY += font.lineHeight + LINE_GAP;
        graphics.drawString(font, progress, textX, lineY, TEXT_SUCCESS, false);
        lineY += font.lineHeight + LINE_GAP;

        int barY = lineY;
        int barX = textX;
        int barWidth = width - PADDING * 2;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BG);
        graphics.fill(barX, barY, barX + Math.round(barWidth * (percent / 100.0F)), barY + barHeight, BAR_FILL);
        graphics.renderOutline(barX, barY, barWidth, barHeight, PANEL_BORDER);

        lineY = barY + barHeight + PANEL_GAP;
        graphics.drawString(font, debug, textX, lineY, TEXT_DEBUG, false);
        lineY += font.lineHeight + LINE_GAP;
        graphics.drawString(font, debug2, textX, lineY, TEXT_DEBUG, false);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, PANEL_SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_BG_ALT);
        graphics.renderOutline(x, y, width, height, PANEL_BORDER);
    }
}
