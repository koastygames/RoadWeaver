package net.shiroha233.roadweaver.client.config;

import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.ConfigService;
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

    private ResourceLocation currentDimension = null;
    private Button dimensionButton;
    private DimensionListWidget dimensionListWidget;
    private boolean pendingCloseDimensionDropdown = false;

    // 标签展开状态
    private final Set<String> expandedTags = new HashSet<>();
    // 模组展开状态（按 namespace），默认展开；搜索时强制展开
    private final Set<String> expandedMods = new HashSet<>();
    // 路径文件夹展开状态（fullPath 作为 key）
    private final Set<String> expandedPaths = new HashSet<>();

    // 基础缩进常量
    private static final int BASE_INDENT_TAG = 30;     // 标签下结构的基础缩进
    private static final int BASE_INDENT_ORPHAN = 10;  // 孤立结构的基础缩进

    private static final int INDENT_STEP = 15;

    private static final int HEADER_HEIGHT = 72;
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

        // 维度选择（动态下拉）
        int dimBtnY = 45;
        int dimBtnW = 220;
        int dimBtnH = 18;
        int dimBtnX = width / 2 - dimBtnW / 2;
        dimensionButton = Button.builder(getDimensionButtonText(), btn -> toggleDimensionDropdown())
                .pos(dimBtnX, dimBtnY)
                .size(dimBtnW, dimBtnH)
                .build();
        addRenderableWidget(dimensionButton);

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
        if (dimensionButton != null) {
            dimensionButton.active = result != null;
            updateDimensionButtonText();
        }
        if (result == null) {
            closeDimensionDropdown();
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
                // 维度过滤
                List<StructureEntry> visibleStructures = new ArrayList<>();
                for (StructureEntry s : tag.structures()) {
                    if (matchesDimension(s)) {
                        visibleStructures.add(s);
                    }
                }
                if (visibleStructures.isEmpty()) {
                    continue;
                }

                // 搜索过滤（先按标签名和 ID），再考虑模组是否匹配
                boolean tagMatchesFilter = matchesFilter(tag.displayName()) || matchesFilter(tag.tagId().toString());

                // 计算匹配搜索的结构列表
                List<StructureEntry> matchingStructures = new ArrayList<>();
                for (StructureEntry structure : visibleStructures) {
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

                    // 如果展开，添加子结构（使用路径树组织）
                    if (isExpanded) {
                        // 若搜索命中模组，则该模组下的所有结构都显示；否则保持原有"按搜索结果过滤结构"的行为
                        List<StructureEntry> baseList;
                        if (!hasSearch || modMatchesFilter || tagMatchesFilter) {
                            baseList = new ArrayList<>(visibleStructures);
                        } else {
                            baseList = new ArrayList<>(matchingStructures);
                        }

                        // 使用路径树来组织标签下的结构
                        StructurePathNode pathTree = StructurePathNode.buildTree(baseList, tag.namespace());
                        addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_TAG);

                        // 记录已添加的结构（仅记录可见结构，避免维度切换时误判孤立结构）
                        for (StructureEntry structure : visibleStructures) {
                            addedStructures.add(structure.id().toString());
                        }
                    } else {
                        // 即使折叠也记录结构，避免后面被当作孤立结构重复显示
                        for (StructureEntry structure : visibleStructures) {
                            addedStructures.add(structure.id().toString());
                        }
                    }
                } else {
                    // 模组折叠时仍然需要记录结构，防止被当作孤立结构重复显示
                    for (StructureEntry structure : visibleStructures) {
                        addedStructures.add(structure.id().toString());
                    }
                }
            }

            // 2. 本模组下不属于任何标签的独立结构
            List<StructureEntry> orphanStructures = new ArrayList<>();
            for (StructureEntry structure : result.allStructures()) {
                if (!modId.equals(structure.namespace())) continue;
                if (!matchesDimension(structure)) continue;
                if (addedStructures.contains(structure.id().toString())) continue;

                // 搜索命中模组时，显示该模组下所有孤立结构；
                // 否则保持原有"仅显示与搜索关键字匹配的结构"的行为
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
                    modEntries.add(new StructureListWidget.HeaderEntry(
                            listWidget,
                            Component.translatable("config.roadweaver.structure_selection.other_structures")
                    ));

                    // 使用路径树来组织孤立结构
                    StructurePathNode pathTree = StructurePathNode.buildTree(orphanStructures, modId);
                    addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_ORPHAN);
                }
            }

            // 3. 若该模组下有任何条目，则添加模组头（带 logo 与折叠按钮），展开时再添加条目
            if (hasAnyForMod) {
                // 计算 Mod 全选状态（只统计当前维度可见的结构）
                List<String> structuresInMod = new ArrayList<>();
                for (StructureEntry structure : result.allStructures()) {
                    if (modId.equals(structure.namespace()) && matchesDimension(structure)) {
                        structuresInMod.add(structure.id().toString());
                    }
                }
                int enabledCount = 0;
                for (String id : structuresInMod) {
                    if (config.isStructureEnabled(id)) {
                        enabledCount++;
                    }
                }
                boolean modAllEnabled = !structuresInMod.isEmpty() && enabledCount == structuresInMod.size();
                boolean modPartialEnabled = enabledCount > 0 && enabledCount < structuresInMod.size();

                Component modHeaderText = Component.literal(getModDisplayName(modId) + " [" + modId + "]");
                listWidget.doAddEntry(new StructureListWidget.ModHeaderEntry(
                        listWidget, modId, modHeaderText, isModExpanded,
                        modAllEnabled, modPartialEnabled,
                        this::onModHeaderToggle,
                        m -> onModSelectAll(m, structuresInMod)
                ));
                if (isModExpanded) {
                    for (StructureListWidget.Entry entry : modEntries) {
                        listWidget.doAddEntry(entry);
                    }
                }
            }
        }
    }

    private boolean matchesDimension(StructureEntry entry) {
        if (currentDimension == null) return true;
        return entry.dimensions().contains(currentDimension);
    }

    private Component getDimensionButtonText() {
        Component name;
        if (currentDimension == null) {
            name = Component.translatable("config.roadweaver.structure_selection.dimension.all");
        } else {
            name = getDimensionDisplayName(currentDimension);
        }
        return Component.translatable("config.roadweaver.structure_selection.dimension", name);
    }

    private void updateDimensionButtonText() {
        if (dimensionButton != null) {
            dimensionButton.setMessage(getDimensionButtonText());
        }
    }

    private Component getDimensionDisplayName(ResourceLocation dimId) {
        String key = "dimension." + dimId.getNamespace() + "." + dimId.getPath();
        Component translated = Component.translatable(key);
        if (!Objects.equals(translated.getString(), key)) {
            return translated;
        }
        return Component.literal(dimId.toString());
    }

    private void toggleDimensionDropdown() {
        if (dimensionListWidget != null) {
            closeDimensionDropdown();
        } else {
            openDimensionDropdown();
        }
    }

    private void openDimensionDropdown() {
        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result == null) return;
        if (dimensionButton == null) return;

        List<DimensionListWidget.Row> rows = new ArrayList<>();
        rows.add(new DimensionListWidget.Row(null,
                Component.translatable("config.roadweaver.structure_selection.dimension.all"),
                null));

        for (ResourceLocation dimId : result.dimensions()) {
            Component title = getDimensionDisplayName(dimId);
            Component subtitle = Component.literal(dimId.toString());
            rows.add(new DimensionListWidget.Row(dimId, title,
                    !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }

        int top = dimensionButton.getY() + dimensionButton.getHeight() + 2;
        int maxH = Math.max(height - FOOTER_HEIGHT - top - 4, 44);
        int desiredRows = Math.max(2, Math.min(8, rows.size()));
        int listH = Math.min(desiredRows * 22, maxH);

        DimensionListWidget list = new DimensionListWidget(minecraft, dimensionButton.getWidth(), listH, top, selected -> {
            currentDimension = selected;
            updateDimensionButtonText();
            pendingCloseDimensionDropdown = true;
            rebuildList();
        });
        list.setX(dimensionButton.getX());
        list.setRows(rows, currentDimension);
        dimensionListWidget = list;
        addRenderableWidget(list);
    }

    private void closeDimensionDropdown() {
        if (dimensionListWidget == null) return;
        removeWidget(dimensionListWidget);
        dimensionListWidget = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DimensionListWidget dd = dimensionListWidget;
        Button btn = dimensionButton;

        if (dd != null && dd.isMouseOver(mouseX, mouseY)) {
            dd.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (dd != null && btn != null) {
            boolean clickedButton = btn.isMouseOver(mouseX, mouseY);
            boolean clickedDropdown = dd.isMouseOver(mouseX, mouseY);
            if (!clickedButton && !clickedDropdown) {
                closeDimensionDropdown();
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (pendingCloseDimensionDropdown) {
            pendingCloseDimensionDropdown = false;
            closeDimensionDropdown();
        }
        return handled;
    }

    private void onModSelectAll(String modId, List<String> structures) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        boolean allEnabled = true;
        for (String id : structures) {
            if (!config.isStructureEnabled(id)) {
                allEnabled = false;
                break;
            }
        }

        if (allEnabled) {
            for (String id : structures) {
                config.disableStructure(id);
            }
        } else {
            for (String id : structures) {
                config.enableStructure(id);
            }
        }
        rebuildList();
    }

    /**
     * 递归添加路径节点条目
     */
    private void addPathNodeEntries(List<StructureListWidget.Entry> entries,
                                    StructurePathNode node,
                                    StructureSelectionConfig config,
                                    int currentIndent) {
        boolean hasSearch = !searchFilter.isEmpty();

        // 处理子文件夹
        for (StructurePathNode child : node.children()) {
            if (child.shouldShowAsFolder()) {
                // 作为可折叠文件夹显示
                boolean isExpanded = hasSearch || expandedPaths.contains(child.fullPath());

                // 计算该文件夹下的选中状态
                Set<String> allIds = child.getAllStructureIds();
                int enabledCount = 0;
                for (String id : allIds) {
                    if (config.isStructureEnabled(id)) {
                        enabledCount++;
                    }
                }
                boolean allEnabled = enabledCount == allIds.size() && !allIds.isEmpty();
                boolean partialEnabled = enabledCount > 0 && enabledCount < allIds.size();

                entries.add(new StructureListWidget.PathFolderEntry(
                        listWidget, child, isExpanded, allEnabled, partialEnabled,
                        currentIndent, this::onPathExpandToggle, this::onPathSelectAllToggle
                ));

                if (isExpanded) {
                    // 递归添加子内容
                    addPathNodeEntries(entries, child, config, currentIndent + INDENT_STEP);
                }
            } else {
                // 结构数量少，直接展开显示
                addPathNodeEntries(entries, child, config, currentIndent);
            }
        }

        // 处理当前节点直接挂载的结构
        for (StructureEntry structure : node.structures()) {
            // 搜索过滤
            if (hasSearch && !matchesFilter(structure.displayName())
                    && !matchesFilter(structure.id().toString())) {
                continue;
            }

            boolean isEnabled = config.isStructureEnabled(structure.id().toString());
            entries.add(new StructureListWidget.PathStructureEntry(
                    listWidget, structure, isEnabled, currentIndent, node.depth(),
                    this::onStructureToggle
            ));
        }
    }

    /**
     * 路径文件夹展开/折叠切换
     */
    private void onPathExpandToggle(StructurePathNode pathNode) {
        String path = pathNode.fullPath();
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path);
        } else {
            expandedPaths.add(path);
        }
        rebuildList();
    }
    
    /**
     * 路径文件夹全选/取消全选
     */
    private void onPathSelectAllToggle(StructurePathNode pathNode) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        Set<String> allIds = pathNode.getAllStructureIds();
        
        // 检查是否全部已启用
        boolean allEnabled = true;
        for (String id : allIds) {
            if (!config.isStructureEnabled(id)) {
                allEnabled = false;
                break;
            }
        }
        
        // 如果全部已启用，则全部禁用；否则全部启用
        if (allEnabled) {
            for (String id : allIds) {
                config.disableStructure(id);
            }
        } else {
            for (String id : allIds) {
                config.enableStructure(id);
            }
        }
        rebuildList();
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
        // 保存结构选择配置（structure_selection.json）
        StructureSelectionConfig selection = StructureSelectionConfig.get();
        selection.save();

        // 关键：把 GUI 选择结果同步到“结构搜寻器”实际使用的白名单（roadweaver.json -> structureWhitelist）。
        // 原理：StructurePredictor/StructureIndexService 只读取 ModConfig.structureWhitelist，
        // 之前 GUI 只写入 structure_selection.json，导致搜寻仍然只剩默认的 #minecraft:village。
        List<String> whitelist = selection.toWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            whitelist = List.of("#minecraft:village");
        }
        ConfigService.get().setStructureWhitelist(whitelist);
        ConfigService.save();
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
