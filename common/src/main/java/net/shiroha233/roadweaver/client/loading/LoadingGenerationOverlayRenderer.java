package net.shiroha233.roadweaver.client.loading;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.tips.LoadingTipsRenderer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressSnapshot;

/**
 * 世界创建阶段的加载进度卡片。
 */
public final class LoadingGenerationOverlayRenderer {
    private static final int MIN_CARD_WIDTH = 360;
    private static final int MAX_CARD_WIDTH = 560;
    private static final int CARD_PADDING = 10;
    private static final int CARD_BG = 0xB010131A;
    private static final int CARD_BORDER = 0x80404B5A;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFB7C0CC;
    private static final int TEXT_MUTED = 0xFF8D97A6;
    private static final int TEXT_ACCENT = 0xFF8CE0FF;
    private static final int BAR_BG = 0x60242D38;
    private static final int BAR_OVERALL = 0xFF4CC2FF;
    private static final int BAR_STAGE = 0xFF8BE28B;

    private LoadingGenerationOverlayRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        LoadingTipsRenderer.render(graphics);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        ModConfig config = ConfigService.get();
        if (config != null && !config.loadingProgressEnabled()) {
            return;
        }
        if (!InitialGenManager.isActive()) {
            return;
        }

        InitialGenerationProgressSnapshot snapshot = InitialGenManager.getProgressSnapshot();
        if (!snapshot.active()) {
            return;
        }

        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        boolean showFallback = snapshot.fallbackReason() != null && !snapshot.fallbackReason().isBlank();
        boolean showDebug = snapshot.stage() == net.shiroha233.roadweaver.generation.progress.InitialGenerationStage.ROAD_GENERATION
                || snapshot.stage() == net.shiroha233.roadweaver.generation.progress.InitialGenerationStage.POST_PROCESSING;
        int contentLines = 8 // title+overall+stage+summary+device+tiles+throughput+padding bottom
                + (showDebug ? 2 : 0)
                + (showFallback ? 1 : 0);
        int cardHeight = 10 + contentLines * 12 + 10; // padding top + lines * lineHeight + padding bottom
        int cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(280, sw - 40));
        int left = (sw - cardWidth) / 2;
        int top = Math.max(26, sh - cardHeight - 28);
        int right = left + cardWidth;
        int bottom = top + cardHeight;

        graphics.fill(left, top, right, bottom, CARD_BG);
        graphics.fill(left, top, right, top + 1, CARD_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, CARD_BORDER);
        graphics.fill(left, top, left + 1, bottom, CARD_BORDER);
        graphics.fill(right - 1, top, right, bottom, CARD_BORDER);

        int x = left + CARD_PADDING;
        int y = top + CARD_PADDING;
        int innerWidth = cardWidth - CARD_PADDING * 2;

        Component title = Component.translatable("gui.roadweaver.initgen.title");
        Component stage = Component.translatable(snapshot.stageLabel());
        graphics.drawString(font, title, x, y, TEXT_PRIMARY, false);
        graphics.drawString(font, stage, right - CARD_PADDING - font.width(stage), y, TEXT_ACCENT, false);

        y += 14;
        graphics.drawString(font,
                Component.translatable("gui.roadweaver.initgen.overall", snapshot.overallPercent()),
                x, y, TEXT_SECONDARY, false);
        y += 10;
        drawBar(graphics, x, y, innerWidth, 8, snapshot.overallPercent(), BAR_OVERALL);

        y += 14;
        graphics.drawString(font,
                Component.translatable("gui.roadweaver.initgen.stage_progress", snapshot.stagePercent()),
                x, y, TEXT_SECONDARY, false);
        y += 10;
        drawBar(graphics, x, y, innerWidth, 6, snapshot.stagePercent(), BAR_STAGE);

        y += 12;
        Component summary = Component.translatable(
                "gui.roadweaver.initgen.summary",
                snapshot.connectionsTotal(),
                snapshot.connectionsGenerating(),
                snapshot.connectionsDone(),
                snapshot.connectionsFailed());
        drawFittedString(graphics, font, summary, x, y, innerWidth, TEXT_SECONDARY);

        y += 12;
        Component device = Component.translatable(
                "gui.roadweaver.initgen.device",
                printable(snapshot.backend()),
                printableDevice(snapshot),
                printable(snapshot.devicePreference()));
        drawFittedString(graphics, font, device, x, y, innerWidth, TEXT_MUTED);

        y += 12;
        Component tiles = Component.translatable(
                "gui.roadweaver.initgen.tiles",
                snapshot.tilesLoaded(),
                snapshot.tilesTotal(),
                snapshot.tilesFromMemory(),
                snapshot.tilesFromDisk(),
                snapshot.tilesSampled());
        drawFittedString(graphics, font, tiles, x, y, innerWidth, TEXT_MUTED);

        y += 12;
        Component throughput = Component.translatable(
                "gui.roadweaver.initgen.throughput",
                snapshot.lastBatchSamples(),
                String.format(java.util.Locale.ROOT, "%.0f", snapshot.samplesPerSecond()),
                snapshot.initialThreads(),
                snapshot.activeWorkers());
        drawFittedString(graphics, font, throughput, x, y, innerWidth, TEXT_MUTED);

        if (showDebug) {
            y += 12;
            Component debug1 = Component.translatable(
                    "gui.roadweaver.initgen.debug",
                    snapshot.terrainCacheHitPercent(),
                    String.format(java.util.Locale.ROOT, "%.0f", snapshot.terrainNoiseSamplesPerSec()),
                    snapshot.terrainNoiseSamplesTotal());
            drawFittedString(graphics, font, debug1, x, y, innerWidth, TEXT_MUTED);

            y += 12;
            Component debug2 = Component.translatable(
                    "gui.roadweaver.initgen.debug2",
                    snapshot.accurateCacheHitPercent(),
                    String.format(java.util.Locale.ROOT, "%.0f", snapshot.accurateBaseHeightPerSec()),
                    snapshot.accurateBaseHeightTotal());
            drawFittedString(graphics, font, debug2, x, y, innerWidth, TEXT_MUTED);
        }

        if (showFallback) {
            y += 12;
            Component fallback = Component.translatable("gui.roadweaver.initgen.fallback", snapshot.fallbackReason());
            drawFittedString(graphics, font, fallback, x, y, innerWidth, 0xFFFFB86B);
        }
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, int height, int percent, int fillColor) {
        int clamped = clamp(percent, 0, 100);
        int filled = Math.max(0, Math.min(width, Math.round(width * (clamped / 100.0f))));
        graphics.fill(x, y, x + width, y + height, BAR_BG);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, fillColor);
        }
    }

    private static void drawFittedString(GuiGraphics graphics,
                                         Font font,
                                         Component text,
                                         int x,
                                         int y,
                                         int maxWidth,
                                         int color) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) {
            graphics.drawString(font, raw, x, y, color, false);
            return;
        }
        int suffixWidth = font.width("...");
        String clipped = font.plainSubstrByWidth(raw, Math.max(0, maxWidth - suffixWidth));
        graphics.drawString(font, clipped + "...", x, y, color, false);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String printable(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String printableDevice(InitialGenerationProgressSnapshot snapshot) {
        String deviceName = printable(snapshot.deviceName());
        if (!"-".equals(deviceName)) {
            return deviceName;
        }
        return printable(snapshot.backend());
    }
}