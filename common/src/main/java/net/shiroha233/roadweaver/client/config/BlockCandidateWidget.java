package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.client.render.SafeGuiItemRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BlockCandidateWidget extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int slotSize = 18;
    
    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filteredBlocks = new ArrayList<>();
    private final EditBox searchBox;
    private int scrollOffset = 0;
    private final Consumer<Block> onBlockSelected;

    // 滚动条拖拽状态（右侧拖条之前只是“提示”，没有交互逻辑）
    private boolean draggingScrollbar = false;
    
    private int visibleRows;
    private int visibleCols;

    public BlockCandidateWidget(int x, int y, int width, int height, Consumer<Block> onBlockSelected) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onBlockSelected = onBlockSelected;

        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, x, y, width, 16, Component.translatable("gui.roadweaver.preset_editor.search"));
        this.searchBox.setResponder(this::onSearchChanged);

        this.visibleCols = Math.max(1, width / slotSize);
        this.visibleRows = Math.max(1, (height - 20) / slotSize);
        
        buildCandidateBlocks();
        rebuildFilteredList();
    }

    private void buildCandidateBlocks() {
        allBlocks.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.level() == null) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR) continue;
                allBlocks.add(b);
            }
            return;
        }

        FeatureFlagSet features = mc.player.connection.enabledFeatures();
        boolean hasPermissions = mc.player.canUseGameMasterBlocks();
        HolderLookup.Provider registries = mc.player.level().registryAccess();
        CreativeModeTabs.tryRebuildTabContents(features, hasPermissions, registries);

        Set<Block> unique = new LinkedHashSet<>();
        addBlocksFromTab(unique, "building_blocks");
        addBlocksFromTab(unique, "natural_blocks");
        addBlocksFromTab(unique, "functional_blocks");

        if (unique.isEmpty()) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR) continue;
                unique.add(b);
            }
        }
        allBlocks.addAll(unique);
    }

    private void addBlocksFromTab(Set<Block> out, String tabId) {
        ResourceLocation rl = ResourceLocation.parse(tabId);
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, rl);
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
        if (tab == null) return;
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block != Blocks.AIR) out.add(block);
            }
        }
    }

    private void onSearchChanged(String text) {
        scrollOffset = 0;
        rebuildFilteredList();
    }

    private void rebuildFilteredList() {
        filteredBlocks.clear();
        String q = searchBox.getValue().trim().toLowerCase();
        for (Block b : allBlocks) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (id == null) continue;
            if (q.isEmpty() || id.toString().toLowerCase().contains(q)) {
                filteredBlocks.add(b);
            }
        }
    }

    private int getMaxOffset() {
        int totalRows = (filteredBlocks.size() + visibleCols - 1) / visibleCols;
        return Math.max(0, totalRows - visibleRows);
    }

    private int getScrollbarStartY() {
        return y + 20;
    }

    private int getScrollbarHeight() {
        return height - 20;
    }

    private int getScrollbarX() {
        return x + width - 6;
    }

    private int getScrollbarWidth() {
        return 6;
    }

    private int getThumbHeight(int maxOffset) {
        int barHeight = getScrollbarHeight();
        int totalRows = (filteredBlocks.size() + visibleCols - 1) / visibleCols;
        if (totalRows <= 0) return barHeight;
        return Math.max(10, barHeight * visibleRows / totalRows);
    }

    private int getThumbY(int maxOffset, int thumbHeight) {
        int startY = getScrollbarStartY();
        int barHeight = getScrollbarHeight();
        if (maxOffset <= 0) return startY;
        return startY + (barHeight - thumbHeight) * scrollOffset / maxOffset;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.searchBox.render(g, mouseX, mouseY, partialTick);

        int startY = y + 20;
        int maxOffset = getMaxOffset();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        int startIndex = scrollOffset * visibleCols;
        int endIndex = Math.min(startIndex + visibleRows * visibleCols, filteredBlocks.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int r = relIndex / visibleCols;
            int c = relIndex % visibleCols;
            
            int bx = x + c * slotSize;
            int by = startY + r * slotSize;
            
            g.fill(bx, by, bx + slotSize, by + slotSize, 0x80000000);
            
            Block b = filteredBlocks.get(i);
            ItemStack stack = new ItemStack(b);
            SafeGuiItemRenderer.renderFakeItemSafe(g, stack, bx + 1, by + 1);
            
            if (mouseX >= bx && mouseX < bx + slotSize && mouseY >= by && mouseY < by + slotSize) {
                g.fill(bx, by, bx + slotSize, by + slotSize, 0x80FFFFFF);
                SafeGuiItemRenderer.renderTooltipSafe(g, Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }
        
        // Scrollbar hint
        if (maxOffset > 0) {
            int barHeight = getScrollbarHeight();
            int thumbHeight = getThumbHeight(maxOffset);
            int thumbY = getThumbY(maxOffset, thumbHeight);
            int barX = getScrollbarX();
            int barW = getScrollbarWidth();
            g.fill(barX, startY, barX + barW, startY + barHeight, 0x40000000);
            g.fill(barX, thumbY, barX + barW, thumbY + thumbHeight, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            int maxOffset = getMaxOffset();
            if (maxOffset > 0) {
                int barX = getScrollbarX();
                int barW = getScrollbarWidth();
                int startY = getScrollbarStartY();
                int barH = getScrollbarHeight();
                int thumbH = getThumbHeight(maxOffset);
                int thumbY = getThumbY(maxOffset, thumbH);

                boolean inBarX = mouseX >= barX && mouseX < barX + barW;
                boolean inBarY = mouseY >= startY && mouseY < startY + barH;
                if (inBarX && inBarY) {
                    // 点击/拖拽滚动条
                    if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                        draggingScrollbar = true;
                    } else {
                        // 点击轨道：跳转到对应位置（按 thumb 中心对齐）
                        int track = Math.max(1, barH - thumbH);
                        double rel = (mouseY - startY - thumbH / 2.0) / track;
                        int target = (int) Math.round(rel * maxOffset);
                        scrollOffset = Math.max(0, Math.min(target, maxOffset));
                    }
                    return true;
                }
            }
        }
        
        int startY = y + 20;
        if (mouseY >= startY && mouseY < startY + visibleRows * slotSize && mouseX >= x && mouseX < x + visibleCols * slotSize) {
            int c = (int)(mouseX - x) / slotSize;
            int r = (int)(mouseY - startY) / slotSize;
            int index = (scrollOffset + r) * visibleCols + c;
            if (index >= 0 && index < filteredBlocks.size()) {
                if (button == 0) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    onBlockSelected.accept(filteredBlocks.get(index));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            int maxOffset = getMaxOffset();
            if (maxOffset <= 0) return true;

            int startY = getScrollbarStartY();
            int barH = getScrollbarHeight();
            int thumbH = getThumbHeight(maxOffset);
            int track = Math.max(1, barH - thumbH);

            double rel = (mouseY - startY - thumbH / 2.0) / track;
            int target = (int) Math.round(rel * maxOffset);
            scrollOffset = Math.max(0, Math.min(target, maxOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            if (scrollY > 0) scrollOffset--;
            else if (scrollY < 0) scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(searchBox);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        this.searchBox.updateNarration(narrationElementOutput);
    }
}
