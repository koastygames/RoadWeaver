package net.shiroha233.roadweaver.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 物品渲染安全包装。
 *
 * 原理：
 * - 某些模组会在 ItemColor/BlockColor 回调里直接访问 Minecraft.player / Minecraft.level。
 * - 当我们在主菜单/配置界面打开 Screen 时，这些字段可能为 null，从而在渲染 ItemStack 时崩溃。
 *
 * 解决：
 * - 对 {@link GuiGraphics#renderFakeItem(ItemStack, int, int)} 做 try/catch，失败时渲染占位符。
 * - 为避免每帧重复抛异常，分别缓存“无世界上下文会失败”的物品与“任何情况下都失败”的物品。
 */
public final class SafeGuiItemRenderer {

    private SafeGuiItemRenderer() {
    }

    private static final ItemStack PLACEHOLDER = new ItemStack(Items.BARRIER);

    private static final Set<ResourceLocation> BROKEN_WITHOUT_WORLD =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Set<ResourceLocation> BROKEN_ALWAYS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static boolean renderFakeItemSafe(GuiGraphics g, ItemStack stack, int x, int y) {
        if (g == null || stack == null || stack.isEmpty()) return false;

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            // 极少数情况下 item key 取不到，这里直接兜底。
            try {
                g.renderFakeItem(stack, x, y);
                return true;
            } catch (Throwable ignored) {
                g.renderFakeItem(PLACEHOLDER, x, y);
                return false;
            }
        }

        if (BROKEN_ALWAYS.contains(key)) {
            g.renderFakeItem(PLACEHOLDER, x, y);
            return false;
        }

        boolean hasWorldContext = hasWorldContext();
        if (!hasWorldContext && BROKEN_WITHOUT_WORLD.contains(key)) {
            g.renderFakeItem(PLACEHOLDER, x, y);
            return false;
        }

        try {
            g.renderFakeItem(stack, x, y);
            return true;
        } catch (Throwable t) {
            // 注意：不要把“仅在无世界时失败”的物品永久拉黑，否则会影响在游戏内正常显示。
            if (hasWorldContext) {
                BROKEN_ALWAYS.add(key);
            } else {
                BROKEN_WITHOUT_WORLD.add(key);
            }
            g.renderFakeItem(PLACEHOLDER, x, y);
            return false;
        }
    }

    public static void renderTooltipSafe(GuiGraphics g, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (g == null || font == null || stack == null || stack.isEmpty()) return;
        try {
            g.renderTooltip(font, stack, mouseX, mouseY);
        } catch (Throwable ignored) {
            g.renderTooltip(font, Component.literal("<render error>"), mouseX, mouseY);
        }
    }

    private static boolean hasWorldContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return mc.player != null && mc.level != null;
    }
}
