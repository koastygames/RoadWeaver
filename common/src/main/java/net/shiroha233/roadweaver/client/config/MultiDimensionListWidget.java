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
public class MultiDimensionListWidget extends RoadWeaverSelectionList<MultiDimensionListWidget.DimensionEntry> {
    private static final int ROW_HEIGHT = 30;
    
    private final Set<ResourceLocation> selectedDimensions;
    private boolean active = true;

    public MultiDimensionListWidget(Minecraft minecraft, int width, int height, int top, Set<ResourceLocation> selectedDimensions) {
        super(minecraft, width, height, top, ROW_HEIGHT, 20);
        this.selectedDimensions = selectedDimensions;
        setBackgroundColor(0xAA14181D);
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void setDimensions(List<ResourceLocation> allDimensions) {
        super.clearEntries();
        for (ResourceLocation dim : allDimensions) {
            this.addEntry(new DimensionEntry(dim));
        }
    }

    public class DimensionEntry extends ContainerObjectSelectionList.Entry<DimensionEntry> {
        private final ResourceLocation dimensionId;

        public DimensionEntry(ResourceLocation dimensionId) {
            this.dimensionId = dimensionId;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            boolean selected = selectedDimensions.contains(dimensionId);
            if (selected) {
                g.fill(left, top, left + width, top + height, 0xD2333C47);
                g.renderOutline(left, top, width, height, 0xFFCDD7E4);
            } else if (isHovered) {
                g.fill(left, top, left + width, top + height, 0xAA222830);
            }
            
            g.fill(left + 4, top + 8, left + 16, top + 20, 0xFF000000);
            g.renderOutline(left + 4, top + 8, 12, 12, 0xFFAAB2BE);
            
            if (selected) {
                g.drawCenteredString(Minecraft.getInstance().font, "x", left + 10, top + 8, 0xFF55FF55);
            }

            Minecraft mc = Minecraft.getInstance();
            String title = DimensionUiHelper.getDisplayName(dimensionId).getString();
            String subtitle = dimensionId.toString();
            g.drawString(mc.font, mc.font.plainSubstrByWidth(title, Math.max(10, width - 40)), left + 24, top + 5, 0xFFFFFF, false);
            if (!title.equals(subtitle)) {
                g.drawString(mc.font, mc.font.plainSubstrByWidth(subtitle, Math.max(10, width - 40)), left + 24, top + 16, 0x98A3AF, false);
            }
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
