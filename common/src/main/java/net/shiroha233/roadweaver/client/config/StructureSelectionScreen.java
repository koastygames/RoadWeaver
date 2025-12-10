package net.shiroha233.roadweaver.client.config;

import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.config.structure.StructureEntry;
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig;
import net.shiroha233.roadweaver.config.structure.StructureTagEntry;

import java.util.*;

/**
 * 结构选择界面
 * 
 * 功能：
 * - 树形展示所有结构标签和结构
 * - 勾选启用/禁用
 * - 搜索过滤
 * - 全选/取消全选
 */
public class StructureSelectionScreen extends Screen {
    private final Screen parent;
    private StructureListWidget listWidget;
    private EditBox searchBox;
    private String searchFilter = "";
    
    // 标签展开状态
    private final Set<String> expandedTags = new HashSet<>();
    // 模组展开状态（按 namespace），默认展开；搜索时强制展开
    private final Set<String> expandedMods = new HashSet<>();
    
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 40;
    
    public StructureSelectionScreen(Screen parent) {
        super(Component.translatable("config.roadweaver.structure_selection.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // 搜索框
        searchBox = new EditBox(font, width / 2 - 100, 22, 200, 18, 
                Component.translatable("config.roadweaver.structure_selection.search"));
        searchBox.setHint(Component.translatable("config.roadweaver.structure_selection.search.hint"));
        searchBox.setResponder(text -> {
            searchFilter = text.toLowerCase(Locale.ROOT);
            rebuildList();
        });
        addRenderableWidget(searchBox);
        
        // 列表组件
        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;
        listWidget = new StructureListWidget(minecraft, width, listBottom - listTop, listTop);
        addRenderableWidget(listWidget);
        rebuildList();
        
        // 底部按钮
        int buttonY = height - 28;
        int buttonWidth = 80;
        int spacing = 5;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = (width - totalWidth) / 2;
        
        // 全选按钮
        addRenderableWidget(Button.builder(
                Component.translatable("config.roadweaver.structure_selection.select_all"),
                btn -> {
                    StructureSelectionConfig.get().enableAll();
                    rebuildList();
                })
                .pos(startX, buttonY)
                .size(buttonWidth, 20)
                .build());
        
        // 取消全选按钮
        addRenderableWidget(Button.builder(
                Component.translatable("config.roadweaver.structure_selection.deselect_all"),
                btn -> {
                    StructureSelectionConfig.get().clearAll();
                    rebuildList();
                })
                .pos(startX + buttonWidth + spacing, buttonY)
                .size(buttonWidth, 20)
                .build());
        
        // 默认（仅村庄）按钮
        addRenderableWidget(Button.builder(
                Component.translatable("config.roadweaver.structure_selection.default"),
                btn -> {
                    StructureSelectionConfig.get().clearAll();
                    StructureSelectionConfig.get().enableDefaultVillages();
                    rebuildList();
                })
                .pos(startX + (buttonWidth + spacing) * 2, buttonY)
                .size(buttonWidth, 20)
                .build());
        
        // 完成按钮
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> onClose())
                .pos(startX + (buttonWidth + spacing) * 3, buttonY)
                .size(buttonWidth, 20)
                .build());
    }
    
    private void rebuildList() {
        if (listWidget == null) return;
        listWidget.clearEntries();
        
        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result == null) {
            // 没有发现数据，显示提示
            listWidget.doAddEntry(new StructureListWidget.MessageEntry(
                    listWidget,
                    Component.translatable("config.roadweaver.structure_selection.no_data")
            ));
            return;
        }
        
        StructureSelectionConfig config = StructureSelectionConfig.get();
        Set<String> addedStructures = new HashSet<>();
        
        // 计算所有涉及到的模组 ID（按命名空间）
        Set<String> modIds = new HashSet<>();
        for (StructureTagEntry tag : result.tags()) {
            modIds.add(tag.namespace());
        }
        for (StructureEntry structure : result.allStructures()) {
            modIds.add(structure.namespace());
        }
        
        // 标签按模组分组
        Map<String, List<StructureTagEntry>> tagsByMod = new HashMap<>();
        for (StructureTagEntry tag : result.tags()) {
            tagsByMod.computeIfAbsent(tag.namespace(), k -> new ArrayList<>()).add(tag);
        }
        // 每个模组内部，标签按显示名首字母排序
        for (List<StructureTagEntry> list : tagsByMod.values()) {
            list.sort(Comparator.comparing(t -> t.displayName().toLowerCase(Locale.ROOT)));
        }
        
        // 结构排序：按本地化名称首字母
        Comparator<StructureEntry> structureSorter =
                Comparator.comparing(s -> StructureListWidget.getLocalizedStructureName(s).toLowerCase(Locale.ROOT));
        
        // 模组 ID 排序：先原版和本模组，其余按模组名首字母
        List<String> sortedModIds = new ArrayList<>(modIds);
        sortedModIds.sort((a, b) -> {
            if (Objects.equals(a, b)) return 0;
            if ("minecraft".equals(a)) return -1;
            if ("minecraft".equals(b)) return 1;
            if ("roadweaver".equals(a)) return -1;
            if ("roadweaver".equals(b)) return 1;
            String na = getModDisplayName(a).toLowerCase(Locale.ROOT);
            String nb = getModDisplayName(b).toLowerCase(Locale.ROOT);
            int cmp = na.compareTo(nb);
            if (cmp != 0) return cmp;
            return a.compareTo(b);
        });
        
        boolean hasSearch = !searchFilter.isEmpty();
        
        // 按模组分组渲染
        for (String modId : sortedModIds) {
            List<StructureListWidget.Entry> modEntries = new ArrayList<>();
            boolean isModExpanded = hasSearch || expandedMods.contains(modId);
            // 搜索时，若关键字匹配模组显示名或 ID，则视为命中该模组
            boolean modMatchesFilter = hasSearch && (matchesFilter(getModDisplayName(modId)) || matchesFilter(modId));
            boolean hasAnyForMod = false;
            
            // 1. 本模组下的标签及其结构
            List<StructureTagEntry> modTags = tagsByMod.getOrDefault(modId, Collections.emptyList());
            for (StructureTagEntry tag : modTags) {
                // 搜索过滤（先按标签名和 ID），再考虑模组是否匹配
                boolean tagMatchesFilter = matchesFilter(tag.displayName()) || matchesFilter(tag.tagId().toString());
                
                // 计算匹配搜索的结构列表
                List<StructureEntry> matchingStructures = new ArrayList<>();
                for (StructureEntry structure : tag.structures()) {
                    if (matchesFilter(structure.displayName()) || matchesFilter(structure.id().toString())) {
                        matchingStructures.add(structure);
                    }
                }
                
                // 若搜索命中模组本身，则整组模组都应显示
                boolean shouldShowTag = modMatchesFilter || tagMatchesFilter || !matchingStructures.isEmpty() || searchFilter.isEmpty();
                if (!shouldShowTag) {
                    continue;
                }
                hasAnyForMod = true;
                
                boolean isTagEnabled = config.isTagEnabled(tag.tagId().toString());
                boolean isExpanded = expandedTags.contains(tag.tagId().toString());
                
                if (isModExpanded) {
                    modEntries.add(new StructureListWidget.TagEntry(
                            listWidget, tag, isTagEnabled, isExpanded,
                            this::onTagToggle, this::onTagExpandToggle
                    ));
                    
                    // 如果展开，添加子结构（按名称首字母排序）
                    if (isExpanded) {
                        // 若搜索命中模组，则该模组下的所有结构都显示；否则保持原有“按搜索结果过滤结构”的行为
                        List<StructureEntry> baseList;
                        if (!hasSearch || modMatchesFilter) {
                            baseList = tag.structures();
                        } else {
                            baseList = matchingStructures;
                        }
                        List<StructureEntry> toShow = new ArrayList<>(baseList);
                        toShow.sort(structureSorter);
                        for (StructureEntry structure : toShow) {
                            boolean isEnabled = config.isStructureEnabled(structure.id().toString());
                            modEntries.add(new StructureListWidget.StructureChildEntry(
                                    listWidget, structure, isEnabled, this::onStructureToggle
                            ));
                            addedStructures.add(structure.id().toString());
                        }
                    } else {
                        // 即使折叠也记录结构，避免后面被当作孤立结构重复显示
                        for (StructureEntry structure : tag.structures()) {
                            addedStructures.add(structure.id().toString());
                        }
                    }
                } else {
                    // 模组折叠时仍然需要记录结构，防止被当作孤立结构重复显示
                    for (StructureEntry structure : tag.structures()) {
                        addedStructures.add(structure.id().toString());
                    }
                }
            }
            
            // 2. 本模组下不属于任何标签的独立结构
            List<StructureEntry> orphanStructures = new ArrayList<>();
            for (StructureEntry structure : result.allStructures()) {
                if (!modId.equals(structure.namespace())) continue;
                if (addedStructures.contains(structure.id().toString())) continue;
                
                // 搜索命中模组时，显示该模组下所有孤立结构；
                // 否则保持原有“仅显示与搜索关键字匹配的结构”的行为
                if (!modMatchesFilter
                        && !matchesFilter(structure.displayName())
                        && !matchesFilter(structure.id().toString())) {
                    if (!searchFilter.isEmpty()) continue;
                }
                orphanStructures.add(structure);
            }
            if (!orphanStructures.isEmpty()) {
                hasAnyForMod = true;
                if (isModExpanded) {
                    orphanStructures.sort(structureSorter);
                    modEntries.add(new StructureListWidget.HeaderEntry(
                            listWidget,
                            Component.translatable("config.roadweaver.structure_selection.other_structures")
                    ));
                    for (StructureEntry structure : orphanStructures) {
                        boolean isEnabled = config.isStructureEnabled(structure.id().toString());
                        modEntries.add(new StructureListWidget.SingleStructureEntry(
                                listWidget, structure, isEnabled, this::onStructureToggle
                        ));
                    }
                }
            }
            
            // 3. 若该模组下有任何条目，则添加模组头（带 logo 与折叠按钮），展开时再添加条目
            if (hasAnyForMod) {
                Component modHeaderText = Component.literal(getModDisplayName(modId) + " [" + modId + "]");
                listWidget.doAddEntry(new StructureListWidget.ModHeaderEntry(
                        listWidget, modId, modHeaderText, isModExpanded, this::onModHeaderToggle));
                if (isModExpanded) {
                    for (StructureListWidget.Entry entry : modEntries) {
                        listWidget.doAddEntry(entry);
                    }
                }
            }
        }
    }
    
    private boolean matchesFilter(String text) {
        if (searchFilter.isEmpty()) return true;
        return text.toLowerCase(Locale.ROOT).contains(searchFilter);
    }
    
    private void onModHeaderToggle(String modId) {
        if (modId == null) return;
        if (expandedMods.contains(modId)) {
            expandedMods.remove(modId);
        } else {
            expandedMods.add(modId);
        }
        rebuildList();
    }
    
    private String getModDisplayName(String modId) {
        if (modId == null || modId.isEmpty()) {
            return "unknown";
        }
        if ("minecraft".equals(modId)) {
            return "Minecraft";
        }
        if ("roadweaver".equals(modId)) {
            return "RoadWeaver";
        }
        return Platform.getOptionalMod(modId)
                .map(mod -> mod.getName())
                .orElse(modId);
    }
    
    private void onTagToggle(StructureTagEntry tag) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        config.toggleTag(tag.tagId().toString());
        rebuildList();
    }
    
    private void onTagExpandToggle(StructureTagEntry tag) {
        String tagId = tag.tagId().toString();
        if (expandedTags.contains(tagId)) {
            expandedTags.remove(tagId);
        } else {
            expandedTags.add(tagId);
        }
        rebuildList();
    }
    
    private void onStructureToggle(StructureEntry structure) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        config.toggleStructure(structure.id().toString());
        rebuildList();
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // 标题
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
    }
    
    @Override
    public void onClose() {
        // 保存配置
        StructureSelectionConfig.get().save();
        minecraft.setScreen(parent);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }
}
