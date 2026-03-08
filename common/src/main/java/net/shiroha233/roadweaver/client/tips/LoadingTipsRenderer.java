package net.shiroha233.roadweaver.client.tips;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 世界加载界面 Tips 渲染器
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

    public static Component getCurrentTip() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return Component.empty();
        }
        if (TIPS.isEmpty()) {
            return Component.empty();
        }

        ModConfig config = ConfigService.get();
        if (config != null && !config.client().loadingTipsEnabled()) {
            return Component.empty();
        }

        long now = System.currentTimeMillis();
        if (lastSwitchTimeMillis == 0L) {
            lastSwitchTimeMillis = now;
        }
        if (now - lastSwitchTimeMillis >= INTERVAL_MILLIS) {
            lastSwitchTimeMillis = now;
            currentIndex = (currentIndex + 1) % TIPS.size();
        }

        return TIPS.get(currentIndex);
    }

    public static void reset() {
        currentIndex = 0;
        lastSwitchTimeMillis = 0L;
    }
}
