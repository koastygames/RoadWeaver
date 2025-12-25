package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.render.SafeGuiItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MaterialGridWidget extends AbstractWidget {
    private final List<String> materialIds = new ArrayList<>();
    private final int slotSize = 18;
    private final int cols;
    private final int rows;
    private final Consumer<Integer> onRemove;
    private final Runnable onSelect;
    private boolean isTarget = false;
    private final String label;

    public MaterialGridWidget(int x, int y, int cols, int rows, String label, Consumer<Integer> onRemove, Runnable onSelect) {
        super(x, y, cols * 18, rows * 18 + 12, Component.literal(label));
        this.cols = cols;
        this.rows = rows;
        this.label = label;
        this.onRemove = onRemove;
        this.onSelect = onSelect;
    }

    public void setMaterials(List<String> materials) {
        this.materialIds.clear();
        if (materials != null) {
            this.materialIds.addAll(materials);
        }
    }

    public void setIsTarget(boolean target) {
        this.isTarget = target;
    }

    public boolean isTarget() {
        return isTarget;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Draw Label
        g.drawString(Minecraft.getInstance().font, this.label, this.getX(), this.getY(), isTarget ? 0xFFFF00 : 0xFFFFFF, false);

        int gridY = this.getY() + 12;
        int index = 0;
        int bgColor = isTarget ? 0xC0000000 : 0x80000000;
        int borderColor = isTarget ? 0xFFFFFF00 : 0xFF888888;

        // Draw Border
        g.fill(this.getX() - 1, gridY - 1, this.getX() + width + 1, gridY + rows * slotSize + 1, borderColor);
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = this.getX() + col * slotSize;
                int y = gridY + row * slotSize;
                
                g.fill(x, y, x + slotSize, y + slotSize, bgColor);
                
                if (index < materialIds.size()) {
                    Block b = blockFromId(materialIds.get(index));
                    if (b != null && b != Blocks.AIR) {
                        ItemStack stack = new ItemStack(b);
                        SafeGuiItemRenderer.renderFakeItemSafe(g, stack, x + 1, y + 1);
                        
                        // Highlight hover
                        if (mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize) {
                             g.fill(x, y, x + slotSize, y + slotSize, 0x80FFFFFF);
                             SafeGuiItemRenderer.renderTooltipSafe(g, Minecraft.getInstance().font, stack, mouseX, mouseY);
                        }
                    }
                }
                index++;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) return false;
        
        int gridY = this.getY() + 12;
        // Check if clicked inside grid area to select as target
        if (mouseX >= this.getX() && mouseX < this.getX() + width &&
            mouseY >= gridY && mouseY < gridY + rows * slotSize) {
            
            this.onSelect.run();
            
            // Check for item removal
            int index = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int x = this.getX() + col * slotSize;
                    int y = gridY + row * slotSize;
                    if (mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize) {
                        if (button == 0 && index < materialIds.size()) { // Left click to remove
                            playDownSound(Minecraft.getInstance().getSoundManager());
                            onRemove.accept(index);
                            return true;
                        }
                    }
                    index++;
                }
            }
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    private Block blockFromId(String id) {
        try {
            ResourceLocation rl = new ResourceLocation(id);
            return BuiltInRegistries.BLOCK.get(rl);
        } catch (Exception e) {
            return Blocks.AIR;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // No-op
    }
}
