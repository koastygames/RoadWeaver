package net.shiroha233.roadweaver.client.tips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 世界加载界面 Tips 渲染器
 * 职责：管理提示文本列表、控制切换时间间隔、在右下角绘制当前提示文本
 */
public final class LoadingTipsRenderer {

    private static final long INTERVAL_MILLIS = 3000L;

    private static final List<Component> TIPS = List.of(
            Component.translatable("tip.roadweaver.loading.1"),
            Component.translatable("tip.roadweaver.loading.2"),
            Component.translatable("tip.roadweaver.loading.3"),
            Component.translatable("tip.roadweaver.loading.4"),
            Component.translatable("tip.roadweaver.loading.5"));

    private static int currentIndex = 0;
    private static long lastSwitchTimeMillis = 0L;

    private LoadingTipsRenderer() {
    }

    /**
     * 在世界加载界面右下角渲染一条提示文本
     */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (TIPS.isEmpty()) {
            return;
        }

        ModConfig config = ConfigService.get();
        if (config != null && !config.client().loadingTipsEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastSwitchTimeMillis == 0L) {
            lastSwitchTimeMillis = now;
        }
        if (now - lastSwitchTimeMillis >= INTERVAL_MILLIS) {
            lastSwitchTimeMillis = now;
            currentIndex = (currentIndex + 1) % TIPS.size();
        }

        Component tip = TIPS.get(currentIndex);

        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int marginX = 6;
        int marginY = 6;
        int x = sw - font.width(tip) - marginX;
        int y = sh - font.lineHeight - marginY;

        graphics.drawString(font, tip, x, y, 0xFFFFFF, false);
    }

    public static void reset() {
        currentIndex = 0;
        lastSwitchTimeMillis = 0L;
    }
}
