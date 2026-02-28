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
 * GUI 物品渲染安全包装
 * 职责：对 GuiGraphics#renderFakeItem 做 try/catch，失败时渲染占位符，缓存失败物品避免重复异常
 * 
 * 原理：某些模组在 ItemColor/BlockColor 回调里直接访问 Minecraft.player/level，
 * 主菜单/配置界面打开时这些字段可能为 null 导致崩溃
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
