package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 维度列表组件
 */
public class DimensionListWidget extends ContainerObjectSelectionList<DimensionListWidget.Entry> {
    private static final int ROW_HEIGHT = 22;
    private static final int BG_COLOR = 0xAA0A0A0A;
    private static final int ACTIVE_BG = 0xFF3A3A3A;
    private static final int HOVER_BG = 0xAA2A2A2A;
    
    private final Consumer<ResourceLocation> onSelect;
    private boolean renderBackground = true;
    private boolean renderTopAndBottom = true;

    public DimensionListWidget(Minecraft minecraft, int width, int height, int top, Consumer<ResourceLocation> onSelect) {
        super(minecraft, width, height, top, top + height);
        this.onSelect = onSelect;
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {
        if (!renderBackground) {
            return;
        }
        graphics.fill(getX(), getY(), getRight(), getBottom(), BG_COLOR);
    }

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {
        if (renderTopAndBottom) {
            super.renderListSeparators(graphics);
        }
    }

    public void setLeftPos(int left) {
        setX(left);
    }

    public void setRenderBackground(boolean renderBackground) {
        this.renderBackground = renderBackground;
    }

    public void setRenderTopAndBottom(boolean renderTopAndBottom) {
        this.renderTopAndBottom = renderTopAndBottom;
    }

    public void setRows(List<Row> rows, ResourceLocation active) {
        clearRows();
        for (Row row : rows) {
            addEntry(new RowEntry(this, row, Objects.equals(row.dimensionId(), active), onSelect));
        }
    }

    public void clearRows() {
        super.clearEntries();
    }

    @Override
    public int getRowWidth() {
        return width - 20;
    }

    @Override
    protected int getScrollbarPosition() {
        return getRowLeft() + getRowWidth() + 6;
    }

    public record Row(ResourceLocation dimensionId, Component title, Component subtitle) {}

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {}

    private static final class RowEntry extends Entry {
        private final Row row;
        private final boolean active;
        private final Consumer<ResourceLocation> onSelect;

        private RowEntry(DimensionListWidget list, Row row, boolean active, Consumer<ResourceLocation> onSelect) {
            this.row = row;
            this.active = active;
            this.onSelect = onSelect;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int bg = active ? ACTIVE_BG : (hovering ? HOVER_BG : 0);
            if (bg != 0) {
                graphics.fill(left, top, left + width, top + height, bg);
            }

            Minecraft mc = Minecraft.getInstance();
            int maxTextWidth = Math.max(width - 24, 10);

            if (active) {
                graphics.drawString(mc.font, "✓", left + 6, top + 3, 0x55FF55, false);
            }

            int textX = left + 16;
            String titleStr = row.title().getString();
            int titleMax = Math.max(maxTextWidth - mc.font.width("…"), 0);
            String titleCut = mc.font.plainSubstrByWidth(titleStr, titleMax);
            String titleFinal = mc.font.width(titleStr) > maxTextWidth ? (titleCut + "…") : titleStr;
            graphics.drawString(mc.font, titleFinal, textX, top + 3, 0xFFFFFF, false);

            if (row.subtitle() != null) {
                String subStr = row.subtitle().getString();
                int subMax = Math.max(maxTextWidth - mc.font.width("…"), 0);
                String subCut = mc.font.plainSubstrByWidth(subStr, subMax);
                String subFinal = mc.font.width(subStr) > maxTextWidth ? (subCut + "…") : subStr;
                graphics.drawString(mc.font, subFinal, textX, top + 12, 0x888888, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                onSelect.accept(row.dimensionId());
                return true;
            }
            return false;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
