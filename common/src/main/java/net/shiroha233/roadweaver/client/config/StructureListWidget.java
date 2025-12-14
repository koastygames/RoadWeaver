package net.shiroha233.roadweaver.client.config;

import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.structure.StructureEntry;
import net.shiroha233.roadweaver.config.structure.StructureTagEntry;

import java.util.*;
import java.util.function.Consumer;

/**
 * 结构列表组件
 * 
 * 继承原版的 ContainerObjectSelectionList，用于显示可滚动的结构列表
 */
public class StructureListWidget extends ContainerObjectSelectionList<StructureListWidget.Entry> {
    // 模组图标缓存，避免每帧重复查询资源
    private static final Map<String, Optional<ResourceLocation>> MOD_ICON_CACHE = new HashMap<>();
    
    public StructureListWidget(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, top + height, 22);
    }
    
    public void clearEntries() {
        super.children().clear();
    }
    
    public int doAddEntry(Entry entry) {
        return super.addEntry(entry);
    }
    
    @Override
    public int getRowWidth() {
        return width - 40;
    }
    
    @Override
    protected int getScrollbarPosition() {
        return width - 10;
    }
    
    static String getLocalizedStructureName(StructureEntry structure) {
        if (structure == null) {
            return "";
        }
        if (structure.isVanilla()) {
            var id = structure.id();
            String key = "structure." + id.getNamespace() + "." + id.getPath();
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return structure.displayName();
    }
    
    /**
     * 根据模组 ID 推导 logo 贴图 ResourceLocation。
     *
     * 规则：
     * 1. 优先使用 Architectury 的 Mod.getLogoFile(32)
     * 2. 支持常见的多种路径格式，并通过资源管理器验证文件是否存在，
     *    避免显示缺失纹理方块
     */
    static ResourceLocation getModIconTexture(String modId) {
        if (modId == null || modId.isEmpty()) {
            return null;
        }
        Optional<ResourceLocation> cached = MOD_ICON_CACHE.get(modId);
        if (cached != null) {
            return cached.orElse(null);
        }
        ResourceLocation resolved = null;
        try {
            var optMod = Platform.getOptionalMod(modId);
            if (optMod.isEmpty()) {
                MOD_ICON_CACHE.put(modId, Optional.empty());
                return null;
            }
            var mod = optMod.get();
            var logoOpt = mod.getLogoFile(32);
            if (logoOpt.isEmpty()) {
                MOD_ICON_CACHE.put(modId, Optional.empty());
                return null;
            }
            String logoPath = logoOpt.get();
            List<ResourceLocation> candidates = new ArrayList<>();
            
            // 1) 完整 assets 路径：assets/modid/...
            if (logoPath.startsWith("assets/")) {
                String rel = logoPath.substring("assets/".length());
                int slash = rel.indexOf('/');
                if (slash >= 0) {
                    String ns = rel.substring(0, slash);
                    String path = rel.substring(slash + 1);
                    candidates.add(new ResourceLocation(ns, path));
                }
            } else if (logoPath.indexOf(':') >= 0) {
                // 2) 标准资源路径：namespace:path
                ResourceLocation rl = ResourceLocation.tryParse(logoPath);
                if (rl != null) {
                    candidates.add(rl);
                }
            } else {
                // 3) 相对路径，尝试几种常见情况：
                //    - 直接视为 assets/modid/<logoPath>
                candidates.add(new ResourceLocation(modId, logoPath));
                //    - 若不含 textures/ 前缀，额外尝试 textures/ 和 textures/gui/ 目录
                if (!logoPath.startsWith("textures/")) {
                    candidates.add(new ResourceLocation(modId, "textures/" + logoPath));
                    candidates.add(new ResourceLocation(modId, "textures/gui/" + logoPath));
                }
            }

            var rm = Minecraft.getInstance().getResourceManager();
            for (ResourceLocation rl : candidates) {
                if (rl == null) continue;
                try {
                    if (rm.getResource(rl).isPresent()) {
                        resolved = rl;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        MOD_ICON_CACHE.put(modId, Optional.ofNullable(resolved));
        return resolved;
    }
    
    // ==================== 条目基类 ====================
    
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        protected final StructureListWidget list;
        
        public Entry(StructureListWidget list) {
            this.list = list;
        }
    }
    
    // ==================== 模组头条目（可折叠 + 显示 logo） ====================
    
    public static class ModHeaderEntry extends Entry {
        private final String modId;
        private final Component title;
        private final boolean expanded;
        private final Consumer<String> onToggle;
        
        public ModHeaderEntry(StructureListWidget list, String modId, Component title,
                              boolean expanded, Consumer<String> onToggle) {
            super(list);
            this.modId = modId;
            this.title = title;
            this.expanded = expanded;
            this.onToggle = onToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 展开/折叠按钮
            String expandIcon = expanded ? "▼" : "▶";
            graphics.drawString(mc.font, expandIcon, left + 5, top + 5, 0xFFFFFF);
            
            int iconX = left + 20;
            int textX = left + 40;
            ResourceLocation icon = getModIconTexture(modId);
            if (icon != null) {
                int iconSize = 16;
                graphics.blit(icon, iconX, top + 3, 0, 0, iconSize, iconSize, iconSize, iconSize);
            } else {
                // 没有图标时，文本稍微左移
                textX = left + 20;
            }
            
            graphics.drawString(mc.font, title, textX, top + 5, 0xFFFFFF);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                int arrowX = left + 5;
                int arrowRight = arrowX + 16;
                // 仅在点击三角区域时切换模组展开状态，避免误触 logo 或文本
                if (mouseX >= arrowX && mouseX <= arrowRight) {
                    onToggle.accept(modId);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 消息条目（用于显示提示信息） ====================
    
    public static class MessageEntry extends Entry {
        private final Component message;
        
        public MessageEntry(StructureListWidget list, Component message) {
            super(list);
            this.message = message;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawCenteredString(Minecraft.getInstance().font, message, 
                    left + width / 2, top + 5, 0xAAAAAA);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 标题条目 ====================
    
    public static class HeaderEntry extends Entry {
        private final Component title;
        
        public HeaderEntry(StructureListWidget list, Component title) {
            super(list);
            this.title = title;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, title, left + 5, top + 5, 0xFFFF00);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 标签条目（可展开） ====================
    
    public static class TagEntry extends Entry {
        private final StructureTagEntry tag;
        private final boolean enabled;
        private final boolean expanded;
        private final Consumer<StructureTagEntry> onToggle;
        private final Consumer<StructureTagEntry> onExpandToggle;
        
        // 复选框区域
        private static final int CHECKBOX_SIZE = 16;
        
        public TagEntry(StructureListWidget list, StructureTagEntry tag, boolean enabled, boolean expanded,
                       Consumer<StructureTagEntry> onToggle, Consumer<StructureTagEntry> onExpandToggle) {
            super(list);
            this.tag = tag;
            this.enabled = enabled;
            this.expanded = expanded;
            this.onToggle = onToggle;
            this.onExpandToggle = onExpandToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 展开/折叠按钮
            String expandIcon = expanded ? "▼" : "▶";
            graphics.drawString(mc.font, expandIcon, left + 5, top + 5, 0xFFFFFF);
            
            // 复选框
            int checkboxX = left + 22;
            int checkboxY = top + 3;
            // 绘制复选框边框
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF666666);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF222222);
            // 如果启用，绘制勾选标记
            if (enabled) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 3, checkboxY + CHECKBOX_SIZE - 3, 0xFF44FF44);
            }
            
            // 标签名称和结构数量
            String displayText = tag.displayName() + " §7(" + tag.structures().size() + ")";
            int textColor = tag.isVanilla() ? 0x55FF55 : 0xFFFF55;
            graphics.drawString(mc.font, displayText, left + 45, top + 5, textColor);
            
            // 标签 ID（较暗）
            graphics.drawString(mc.font, tag.tagId().toString(), left + 45, top + 15, 0x888888);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                // 计算点击区域
                int left = list.getRowLeft();
                int expandX = left + 5;
                int checkboxX = left + 22;
                
                if (mouseX >= expandX && mouseX < expandX + 16) {
                    // 点击展开按钮
                    onExpandToggle.accept(tag);
                    return true;
                } else if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                    // 点击复选框
                    onToggle.accept(tag);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 结构条目（标签下的子结构，有缩进） ====================
    
    public static class StructureChildEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private static final int CHECKBOX_SIZE = 16;
        private static final int INDENT = 30;
        
        public StructureChildEntry(StructureListWidget list, 
                                   StructureEntry structure, 
                                   boolean enabled,
                                   Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.onToggle = onToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 缩进的复选框
            int checkboxX = left + INDENT;
            int checkboxY = top + 3;
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF666666);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF222222);
            if (enabled) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 3, checkboxY + CHECKBOX_SIZE - 3, 0xFF44FF44);
            }
            
            // 结构名称
            int textColor = structure.isVanilla() ? 0xAAAAAA : 0xFFAAAA;
            String name = getLocalizedStructureName(structure);
            graphics.drawString(mc.font, "  " + name, left + INDENT + 20, top + 5, textColor);
            
            // 结构 ID
            graphics.drawString(mc.font, structure.id().toString(), left + INDENT + 20, top + 15, 0x666666);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                int checkboxX = left + INDENT;
                if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 路径文件夹条目（可展开，带全选功能） ====================
    
    public static class PathFolderEntry extends Entry {
        private final StructurePathNode pathNode;
        private final boolean expanded;
        private final boolean allEnabled;      // 该文件夹下所有结构是否全部启用
        private final boolean partialEnabled;  // 该文件夹下是否部分启用
        private final Consumer<StructurePathNode> onExpandToggle;
        private final Consumer<StructurePathNode> onSelectAllToggle;
        private final int baseIndent;          // 基础缩进（来自父级）
        
        private static final int CHECKBOX_SIZE = 16;
        private static final int INDENT_PER_LEVEL = 15;
        
        public PathFolderEntry(StructureListWidget list, 
                               StructurePathNode pathNode,
                               boolean expanded,
                               boolean allEnabled,
                               boolean partialEnabled,
                               int baseIndent,
                               Consumer<StructurePathNode> onExpandToggle,
                               Consumer<StructurePathNode> onSelectAllToggle) {
            super(list);
            this.pathNode = pathNode;
            this.expanded = expanded;
            this.allEnabled = allEnabled;
            this.partialEnabled = partialEnabled;
            this.baseIndent = baseIndent;
            this.onExpandToggle = onExpandToggle;
            this.onSelectAllToggle = onSelectAllToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 计算缩进
            int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;
            
            // 展开/折叠按钮
            String expandIcon = expanded ? "▼" : "▶";
            int expandX = left + indent + 5;
            graphics.drawString(mc.font, expandIcon, expandX, top + 5, 0xFFFFFF);
            
            // 复选框（用于全选/取消全选）
            int checkboxX = left + indent + 22;
            int checkboxY = top + 3;
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF666666);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF222222);
            
            // 绘制选中状态：全选=绿色填充，部分选中=黄色边框
            if (allEnabled) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 3, checkboxY + CHECKBOX_SIZE - 3, 0xFF44FF44);
            } else if (partialEnabled) {
                // 部分选中：绘制黄色边框
                graphics.fill(checkboxX + 2, checkboxY + 2, checkboxX + CHECKBOX_SIZE - 2, checkboxY + 3, 0xFFFFFF00);
                graphics.fill(checkboxX + 2, checkboxY + CHECKBOX_SIZE - 3, checkboxX + CHECKBOX_SIZE - 2, checkboxY + CHECKBOX_SIZE - 2, 0xFFFFFF00);
                graphics.fill(checkboxX + 2, checkboxY + 3, checkboxX + 3, checkboxY + CHECKBOX_SIZE - 3, 0xFFFFFF00);
                graphics.fill(checkboxX + CHECKBOX_SIZE - 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 2, checkboxY + CHECKBOX_SIZE - 3, 0xFFFFFF00);
            }
            
            // 文件夹名称和结构数量
            int textX = left + indent + 45;
            String displayText = pathNode.name() + " §7(" + pathNode.getTotalStructureCount() + ")";
            graphics.drawString(mc.font, displayText, textX, top + 5, 0xFFAA55);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                int indent = baseIndent + (pathNode.depth() - 1) * INDENT_PER_LEVEL;
                int expandX = left + indent + 5;
                int checkboxX = left + indent + 22;
                
                if (mouseX >= expandX && mouseX < expandX + 16) {
                    // 点击展开按钮
                    onExpandToggle.accept(pathNode);
                    return true;
                } else if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                    // 点击复选框 - 全选/取消全选
                    onSelectAllToggle.accept(pathNode);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 路径下的结构条目（带深度缩进） ====================
    
    public static class PathStructureEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private final int baseIndent;
        private final int depth;
        
        private static final int CHECKBOX_SIZE = 16;
        private static final int INDENT_PER_LEVEL = 15;
        
        public PathStructureEntry(StructureListWidget list, 
                                  StructureEntry structure, 
                                  boolean enabled,
                                  int baseIndent,
                                  int depth,
                                  Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.baseIndent = baseIndent;
            this.depth = depth;
            this.onToggle = onToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 计算缩进
            int indent = baseIndent + depth * INDENT_PER_LEVEL;
            
            // 复选框
            int checkboxX = left + indent + 5;
            int checkboxY = top + 3;
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF666666);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF222222);
            if (enabled) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 3, checkboxY + CHECKBOX_SIZE - 3, 0xFF44FF44);
            }
            
            // 结构名称（只显示叶子名称）
            String leafName = StructurePathNode.getLeafName(structure);
            int textColor = structure.isVanilla() ? 0xAAAAAA : 0xFFAAAA;
            graphics.drawString(mc.font, leafName, left + indent + 28, top + 5, textColor);
            
            // 完整 ID（较暗）
            graphics.drawString(mc.font, structure.id().toString(), left + indent + 28, top + 15, 0x666666);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                int indent = baseIndent + depth * INDENT_PER_LEVEL;
                int checkboxX = left + indent + 5;
                if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
    
    // ==================== 独立结构条目（无缩进） ====================
    
    public static class SingleStructureEntry extends Entry {
        private final StructureEntry structure;
        private final boolean enabled;
        private final Consumer<StructureEntry> onToggle;
        private static final int CHECKBOX_SIZE = 16;
        
        public SingleStructureEntry(StructureListWidget list, 
                                   StructureEntry structure,
                                   boolean enabled,
                                   Consumer<StructureEntry> onToggle) {
            super(list);
            this.structure = structure;
            this.enabled = enabled;
            this.onToggle = onToggle;
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            
            // 复选框
            int checkboxX = left + 5;
            int checkboxY = top + 3;
            graphics.fill(checkboxX, checkboxY, checkboxX + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0xFF666666);
            graphics.fill(checkboxX + 1, checkboxY + 1, checkboxX + CHECKBOX_SIZE - 1, checkboxY + CHECKBOX_SIZE - 1, 0xFF222222);
            if (enabled) {
                graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + CHECKBOX_SIZE - 3, checkboxY + CHECKBOX_SIZE - 3, 0xFF44FF44);
            }
            
            // 结构名称
            int textColor = structure.isVanilla() ? 0xFFFFFF : 0xFFAAAA;
            String name = getLocalizedStructureName(structure);
            graphics.drawString(mc.font, name, left + 28, top + 5, textColor);
            
            // 结构 ID
            graphics.drawString(mc.font, structure.id().toString(), left + 28, top + 15, 0x888888);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int left = list.getRowLeft();
                int checkboxX = left + 5;
                if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                    onToggle.accept(structure);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }
}
