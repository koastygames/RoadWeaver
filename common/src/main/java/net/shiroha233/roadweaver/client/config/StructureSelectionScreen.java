package net.shiroha233.roadweaver.client.config;

import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.config.structure.StructureEntry;
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig;
import net.shiroha233.roadweaver.config.structure.StructureTagEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结构选择界面。
 */
public class StructureSelectionScreen extends Screen {
    private static final int BASE_INDENT_TAG = 30;
    private static final int BASE_INDENT_ORPHAN = 10;
    private static final int INDENT_STEP = 15;
    private static final int HEADER_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final Set<String> expandedTags = new HashSet<>();
    private final Set<String> expandedMods = new HashSet<>();
    private final Set<String> expandedPaths = new HashSet<>();

    private StructureListWidget listWidget;
    private EditBox searchBox;
    private String searchFilter = "";

    private Identifier currentDimension;
    private Button dimensionButton;
    private DimensionListWidget dimensionListWidget;
    private boolean pendingCloseDimensionDropdown;

    public StructureSelectionScreen(Screen parent) {
        super(Component.translatable("config.roadweaver.structure_selection.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(font, width / 2 - 100, 22, 200, 18, Component.translatable("config.roadweaver.structure_selection.search"));
        this.searchBox.setHint(Component.translatable("config.roadweaver.structure_selection.search.hint"));
        this.searchBox.setResponder(text -> {
            searchFilter = text.toLowerCase(Locale.ROOT);
            rebuildList();
        });
        this.addRenderableWidget(searchBox);

        int dimensionButtonWidth = 220;
        int dimensionButtonX = width / 2 - dimensionButtonWidth / 2;
        this.dimensionButton = Button.builder(getDimensionButtonText(), button -> toggleDimensionDropdown())
                .pos(dimensionButtonX, 45)
                .size(dimensionButtonWidth, 18)
                .build();
        this.addRenderableWidget(dimensionButton);

        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;
        this.listWidget = new StructureListWidget(minecraft, width, listBottom - listTop, listTop);
        this.addRenderableWidget(listWidget);
        rebuildList();

        int buttonY = height - 28;
        int buttonWidth = 80;
        int spacing = 5;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = (width - totalWidth) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("config.roadweaver.structure_selection.select_all"), button -> {
                    StructureSelectionConfig.get().enableAll();
                    rebuildList();
                }).pos(startX, buttonY).size(buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.roadweaver.structure_selection.deselect_all"), button -> {
                    StructureSelectionConfig.get().clearAll();
                    rebuildList();
                }).pos(startX + buttonWidth + spacing, buttonY).size(buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.roadweaver.structure_selection.default"), button -> {
                    StructureSelectionConfig.get().clearAll();
                    StructureSelectionConfig.get().enableDefaultVillages();
                    rebuildList();
                }).pos(startX + (buttonWidth + spacing) * 2, buttonY).size(buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .pos(startX + (buttonWidth + spacing) * 3, buttonY)
                .size(buttonWidth, 20)
                .build());
    }

    private void rebuildList() {
        if (listWidget == null) {
            return;
        }
        listWidget.clearEntries();

        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (dimensionButton != null) {
            dimensionButton.active = result != null;
            updateDimensionButtonText();
        }
        if (result == null) {
            closeDimensionDropdown();
            listWidget.doAddEntry(new StructureListWidget.MessageEntry(listWidget, Component.translatable("config.roadweaver.structure_selection.no_data")));
            return;
        }

        StructureSelectionConfig config = StructureSelectionConfig.get();
        Set<String> addedStructures = new HashSet<>();
        Set<String> modIds = new HashSet<>();
        for (StructureTagEntry tag : result.tags()) {
            modIds.add(tag.namespace());
        }
        for (StructureEntry structure : result.allStructures()) {
            modIds.add(structure.namespace());
        }

        Map<String, List<StructureTagEntry>> tagsByMod = new HashMap<>();
        for (StructureTagEntry tag : result.tags()) {
            tagsByMod.computeIfAbsent(tag.namespace(), ignored -> new ArrayList<>()).add(tag);
        }
        for (List<StructureTagEntry> entries : tagsByMod.values()) {
            entries.sort(Comparator.comparing(tag -> tag.displayName().toLowerCase(Locale.ROOT)));
        }

        List<String> sortedModIds = new ArrayList<>(modIds);
        sortedModIds.sort((left, right) -> {
            if (Objects.equals(left, right)) {
                return 0;
            }
            if ("minecraft".equals(left)) {
                return -1;
            }
            if ("minecraft".equals(right)) {
                return 1;
            }
            if ("roadweaver".equals(left)) {
                return -1;
            }
            if ("roadweaver".equals(right)) {
                return 1;
            }
            int compare = getModDisplayName(left).toLowerCase(Locale.ROOT).compareTo(getModDisplayName(right).toLowerCase(Locale.ROOT));
            return compare != 0 ? compare : left.compareTo(right);
        });

        boolean hasSearch = !searchFilter.isEmpty();
        for (String modId : sortedModIds) {
            List<StructureListWidget.Entry> modEntries = new ArrayList<>();
            boolean modExpanded = hasSearch || expandedMods.contains(modId);
            boolean modMatchesFilter = hasSearch && (matchesFilter(getModDisplayName(modId)) || matchesFilter(modId));
            boolean hasAnyForMod = false;

            List<StructureTagEntry> modTags = tagsByMod.getOrDefault(modId, Collections.emptyList());
            for (StructureTagEntry tag : modTags) {
                List<StructureEntry> visibleStructures = new ArrayList<>();
                for (StructureEntry structure : tag.structures()) {
                    if (matchesDimension(structure)) {
                        visibleStructures.add(structure);
                    }
                }
                if (visibleStructures.isEmpty()) {
                    continue;
                }

                boolean tagMatchesFilter = matchesFilter(tag.displayName()) || matchesFilter(tag.tagId().toString());
                List<StructureEntry> matchingStructures = new ArrayList<>();
                for (StructureEntry structure : visibleStructures) {
                    if (matchesFilter(structure.displayName()) || matchesFilter(structure.id().toString())) {
                        matchingStructures.add(structure);
                    }
                }

                boolean shouldShowTag = modMatchesFilter || tagMatchesFilter || !matchingStructures.isEmpty() || searchFilter.isEmpty();
                if (!shouldShowTag) {
                    continue;
                }
                hasAnyForMod = true;
                boolean tagEnabled = config.isTagEnabled(tag.tagId().toString());
                boolean tagExpanded = expandedTags.contains(tag.tagId().toString());

                if (modExpanded) {
                    modEntries.add(new StructureListWidget.TagEntry(listWidget, tag, tagEnabled, tagExpanded, this::onTagToggle, this::onTagExpandToggle));
                    if (tagExpanded) {
                        List<StructureEntry> baseList = !hasSearch || modMatchesFilter || tagMatchesFilter
                                ? new ArrayList<>(visibleStructures)
                                : new ArrayList<>(matchingStructures);
                        StructurePathNode pathTree = StructurePathNode.buildTree(baseList, tag.namespace());
                        addPathNodeEntries(modEntries, pathTree, config, BASE_INDENT_TAG);
                    }
                }
                for (StructureEntry structure : visibleStructures) {
                    addedStructures.add(structure.id().toString());
                }
            }

            List<StructureEntry> orphanStructures = new ArrayList<>();
            for (StructureEntry structure : result.allStructures()) {
                if (!modId.equals(structure.namespace()) || !matchesDimension(structure) || addedStructures.contains(structure.id().toString())) {
                    continue;
                }
                if (!modMatchesFilter && !matchesFilter(structure.displayName()) && !matchesFilter(structure.id().toString()) && !searchFilter.isEmpty()) {
                    continue;
                }
                orphanStructures.add(structure);
            }

            if (!orphanStructures.isEmpty()) {
                hasAnyForMod = true;
                if (modExpanded) {
                    modEntries.add(new StructureListWidget.HeaderEntry(listWidget, Component.translatable("config.roadweaver.structure_selection.other_structures")));
                    addPathNodeEntries(modEntries, StructurePathNode.buildTree(orphanStructures, modId), config, BASE_INDENT_ORPHAN);
                }
            }

            if (hasAnyForMod) {
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
                boolean allEnabled = !structuresInMod.isEmpty() && enabledCount == structuresInMod.size();
                boolean partialEnabled = enabledCount > 0 && enabledCount < structuresInMod.size();
                Component headerText = Component.literal(getModDisplayName(modId) + " [" + modId + "]");
                listWidget.doAddEntry(new StructureListWidget.ModHeaderEntry(listWidget, modId, headerText, modExpanded, allEnabled, partialEnabled, this::onModHeaderToggle, ignored -> onModSelectAll(structuresInMod)));
                if (modExpanded) {
                    for (StructureListWidget.Entry entry : modEntries) {
                        listWidget.doAddEntry(entry);
                    }
                }
            }
        }
    }

    private void addPathNodeEntries(List<StructureListWidget.Entry> entries, StructurePathNode node, StructureSelectionConfig config, int currentIndent) {
        boolean hasSearch = !searchFilter.isEmpty();
        for (StructurePathNode child : node.children()) {
            if (child.shouldShowAsFolder()) {
                boolean expanded = hasSearch || expandedPaths.contains(child.fullPath());
                Set<String> allIds = child.getAllStructureIds();
                int enabledCount = 0;
                for (String id : allIds) {
                    if (config.isStructureEnabled(id)) {
                        enabledCount++;
                    }
                }
                boolean allEnabled = enabledCount == allIds.size() && !allIds.isEmpty();
                boolean partialEnabled = enabledCount > 0 && enabledCount < allIds.size();
                entries.add(new StructureListWidget.PathFolderEntry(listWidget, child, expanded, allEnabled, partialEnabled, currentIndent, this::onPathExpandToggle, this::onPathSelectAllToggle));
                if (expanded) {
                    addPathNodeEntries(entries, child, config, currentIndent + INDENT_STEP);
                }
            } else {
                addPathNodeEntries(entries, child, config, currentIndent);
            }
        }

        for (StructureEntry structure : node.structures()) {
            if (hasSearch && !matchesFilter(structure.displayName()) && !matchesFilter(structure.id().toString())) {
                continue;
            }
            entries.add(new StructureListWidget.PathStructureEntry(listWidget, structure, config.isStructureEnabled(structure.id().toString()), currentIndent, node.depth(), this::onStructureToggle));
        }
    }

    private boolean matchesDimension(StructureEntry entry) {
        return currentDimension == null || entry.dimensions().contains(currentDimension);
    }

    private Component getDimensionButtonText() {
        Component name = currentDimension == null
                ? Component.translatable("config.roadweaver.structure_selection.dimension.all")
                : getDimensionDisplayName(currentDimension);
        return Component.translatable("config.roadweaver.structure_selection.dimension", name);
    }

    private void updateDimensionButtonText() {
        if (dimensionButton != null) {
            dimensionButton.setMessage(getDimensionButtonText());
        }
    }

    private Component getDimensionDisplayName(Identifier dimensionId) {
        String key = "dimension." + dimensionId.getNamespace() + "." + dimensionId.getPath();
        Component translated = Component.translatable(key);
        return !Objects.equals(translated.getString(), key) ? translated : Component.literal(dimensionId.toString());
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
        if (result == null || dimensionButton == null) {
            return;
        }
        List<DimensionListWidget.Row> rows = new ArrayList<>();
        rows.add(new DimensionListWidget.Row(null, Component.translatable("config.roadweaver.structure_selection.dimension.all"), null));
        for (Identifier dimension : result.dimensions()) {
            Component title = getDimensionDisplayName(dimension);
            Component subtitle = Component.literal(dimension.toString());
            rows.add(new DimensionListWidget.Row(dimension, title, !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }

        int top = dimensionButton.getY() + dimensionButton.getHeight() + 2;
        int maxHeight = Math.max(height - FOOTER_HEIGHT - top - 4, 44);
        int desiredRows = Math.max(2, Math.min(8, rows.size()));
        int listHeight = Math.min(desiredRows * 22, maxHeight);

        DimensionListWidget list = new DimensionListWidget(minecraft, dimensionButton.getWidth(), listHeight, top, selected -> {
            currentDimension = selected;
            updateDimensionButtonText();
            pendingCloseDimensionDropdown = true;
            rebuildList();
        });
        list.setLeftPos(dimensionButton.getX());
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
        list.setRows(rows, currentDimension);
        dimensionListWidget = list;
        addRenderableWidget(list);
    }

    private void closeDimensionDropdown() {
        if (dimensionListWidget != null) {
            removeWidget(dimensionListWidget);
            dimensionListWidget = null;
        }
    }

    private void onModSelectAll(List<String> structures) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        boolean allEnabled = true;
        for (String id : structures) {
            if (!config.isStructureEnabled(id)) {
                allEnabled = false;
                break;
            }
        }
        for (String id : structures) {
            if (allEnabled) {
                config.disableStructure(id);
            } else {
                config.enableStructure(id);
            }
        }
        rebuildList();
    }

    private void onPathExpandToggle(StructurePathNode pathNode) {
        String path = pathNode.fullPath();
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path);
        } else {
            expandedPaths.add(path);
        }
        rebuildList();
    }

    private void onPathSelectAllToggle(StructurePathNode pathNode) {
        StructureSelectionConfig config = StructureSelectionConfig.get();
        Set<String> allIds = pathNode.getAllStructureIds();
        boolean allEnabled = true;
        for (String id : allIds) {
            if (!config.isStructureEnabled(id)) {
                allEnabled = false;
                break;
            }
        }
        for (String id : allIds) {
            if (allEnabled) {
                config.disableStructure(id);
            } else {
                config.enableStructure(id);
            }
        }
        rebuildList();
    }

    private boolean matchesFilter(String text) {
        return searchFilter.isEmpty() || text.toLowerCase(Locale.ROOT).contains(searchFilter);
    }

    private void onModHeaderToggle(String modId) {
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
        return Platform.getOptionalMod(modId).map(mod -> mod.getName()).orElse(modId);
    }

    private void onTagToggle(StructureTagEntry tag) {
        StructureSelectionConfig.get().toggleTag(tag.tagId().toString());
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
        StructureSelectionConfig.get().toggleStructure(structure.id().toString());
        rebuildList();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        DimensionListWidget dropdown = dimensionListWidget;
        Button button = dimensionButton;
        if (dropdown != null && dropdown.isMouseOver(mouseX, mouseY)) {
            dropdown.mouseClicked(event, doubleClick);
            return true;
        }
        if (dropdown != null && button != null && !button.isMouseOver(mouseX, mouseY) && !dropdown.isMouseOver(mouseX, mouseY)) {
            closeDimensionDropdown();
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (pendingCloseDimensionDropdown) {
            pendingCloseDimensionDropdown = false;
            closeDimensionDropdown();
        }
        return handled;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        StructureSelectionConfig selection = StructureSelectionConfig.get();
        selection.save();
        List<String> whitelist = selection.toWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            whitelist = List.of("#minecraft:village");
        }
        ConfigService.get().setStructureWhitelist(whitelist);
        ConfigService.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }
}
