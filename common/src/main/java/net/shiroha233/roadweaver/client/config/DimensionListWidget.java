package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 维度列表组件。
 */
public class DimensionListWidget extends ContainerObjectSelectionList<DimensionListWidget.Entry> {
    private static final int ROW_HEIGHT = 22;
    private static final int BG_COLOR = 0xAA0A0A0A;
    private static final int ACTIVE_BG = 0xFF3A3A3A;
    private static final int HOVER_BG = 0xAA2A2A2A;
    private static final String TRUNCATION_SUFFIX = "...";

    private final Consumer<Identifier> onSelect;
    private boolean renderBackground = true;
    private boolean renderTopAndBottom = true;

    public DimensionListWidget(Minecraft minecraft, int width, int height, int top, Consumer<Identifier> onSelect) {
        super(minecraft, width, height, top, ROW_HEIGHT);
        this.centerListVertically = false;
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

    public void setRows(List<Row> rows, Identifier active) {
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
    protected int scrollBarX() {
        return getRowLeft() + getRowWidth() + 6;
    }

    public record Row(Identifier dimensionId, Component title, Component subtitle) {}

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {}

    private static final class RowEntry extends Entry {
        private final DimensionListWidget owner;
        private final Row row;
        private final boolean active;
        private final Consumer<Identifier> onSelect;

        private RowEntry(DimensionListWidget list, Row row, boolean active, Consumer<Identifier> onSelect) {
            this.owner = list;
            this.row = row;
            this.active = active;
            this.onSelect = onSelect;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovering, float partialTick) {
            top = this.getY();
            left = this.getX();
            int bg = active ? ACTIVE_BG : (hovering ? HOVER_BG : 0);
            int width = owner.getRowWidth();
            if (bg != 0) {
                graphics.fill(left, top, left + width, top + ROW_HEIGHT, bg);
            }

            Minecraft mc = Minecraft.getInstance();
            int maxTextWidth = Math.max(width - 24, 10);

            if (active) {
                graphics.drawString(mc.font, "*", left + 6, top + 3, 0xFF55FF55, false);
            }

            int textX = left + 16;
            graphics.drawString(mc.font, truncate(mc, row.title().getString(), maxTextWidth), textX, top + 3, 0xFFFFFFFF, false);

            if (row.subtitle() != null) {
                graphics.drawString(mc.font, truncate(mc, row.subtitle().getString(), maxTextWidth), textX, top + 12, 0xFF888888, false);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                onSelect.accept(row.dimensionId());
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        private static String truncate(Minecraft minecraft, String text, int maxWidth) {
            if (minecraft.font.width(text) <= maxWidth) {
                return text;
            }
            int suffixWidth = minecraft.font.width(TRUNCATION_SUFFIX);
            String prefix = minecraft.font.plainSubstrByWidth(text, Math.max(0, maxWidth - suffixWidth));
            return prefix + TRUNCATION_SUFFIX;
        }
    }
}
