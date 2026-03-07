package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Set;

/**
 * 多维度选择列表组件
 */
public class MultiDimensionListWidget extends ContainerObjectSelectionList<MultiDimensionListWidget.Entry> {
    private static final int ROW_HEIGHT = 20;
    
    private final Set<ResourceLocation> selectedDimensions;
    private boolean active = true;

    public MultiDimensionListWidget(Minecraft minecraft, int width, int height, int top, int bottom, Set<ResourceLocation> selectedDimensions) {
        super(minecraft, width, height, top, bottom, ROW_HEIGHT);
        this.selectedDimensions = selectedDimensions;
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void setDimensions(List<ResourceLocation> allDimensions) {
        this.clearEntries();
        for (ResourceLocation dim : allDimensions) {
            this.addEntry(new Entry(dim));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowLeft() + this.getRowWidth() + 6;
    }

    public class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final ResourceLocation dimensionId;

        public Entry(ResourceLocation dimensionId) {
            this.dimensionId = dimensionId;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            boolean selected = selectedDimensions.contains(dimensionId);
            
            g.fill(left + 2, top + 2, left + 14, top + 14, 0xFF000000);
            g.renderOutline(left + 2, top + 2, 12, 12, 0xFFAAAAAA);
            
            if (selected) {
                g.drawCenteredString(Minecraft.getInstance().font, "x", left + 8, top + 2, 0xFF55FF55);
            }

            g.drawString(Minecraft.getInstance().font, dimensionId.toString(), left + 20, top + 4, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!MultiDimensionListWidget.this.isActive()) return false;
            if (button == 0) {
                if (selectedDimensions.contains(dimensionId)) {
                    selectedDimensions.remove(dimensionId);
                } else {
                    selectedDimensions.add(dimensionId);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
