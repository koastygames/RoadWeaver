package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 材料预设编辑界面。
 */
public class MaterialPresetEditorScreen extends Screen {
    private static final int MARGIN = 6;
    private static final float ZH_SCALE = 1.35F;
    private static final float EN_SCALE = 0.85F;

    private final Screen parent;
    private final List<UiPreset> presets = new ArrayList<>();
    private final Set<String> originalIds = new HashSet<>();

    private PresetListWidget presetList;
    private EditBox nameBox;
    private Button typeButton;
    private MaterialGridWidget baseMaterialGrid;
    private MaterialGridWidget slabMaterialGrid;
    private BlockCandidateWidget blockCandidateWidget;
    private Button saveButton;
    private Button cancelButton;
    private Button newButton;
    private Button deleteButton;
    private Button dimensionButton;
    private DimensionListWidget dimensionListWidget;

    private Identifier filterDimension = Identifier.parse("minecraft:overworld");
    private PresetService.RoadType filterType = PresetService.RoadType.ARTIFICIAL;
    private boolean pendingCloseDimensionDropdown;

    private int editorLeft;
    private int editorWidth;
    private int editorHeaderY;
    private int activePresetIndex = -1;

    private static class UiPreset {
        String id;
        String name;
        PresetService.RoadType type;
        Set<Identifier> dimensions = new HashSet<>();
        List<String> materials = new ArrayList<>();
        List<String> slabMaterials = new ArrayList<>();
    }

    public MaterialPresetEditorScreen(Screen parent) {
        super(Component.translatable("gui.roadweaver.preset_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.dimensionListWidget = null;
        this.pendingCloseDimensionDropdown = false;

        int topBarY = 24;
        int bottomY = this.height - 24;

        int leftPanelX = MARGIN;
        int leftPanelWidth = Math.max(170, Math.min(240, this.width / 4));
        int rightMinWidth = 160 + 200 + 6;
        int maxLeftWidth = Math.max(120, this.width - (MARGIN * 3) - rightMinWidth);
        leftPanelWidth = Math.min(leftPanelWidth, maxLeftWidth);

        int rightPanelX = leftPanelX + leftPanelWidth + MARGIN;
        int rightPanelWidth = this.width - rightPanelX - MARGIN;

        this.nameBox = new EditBox(font, leftPanelX, topBarY, leftPanelWidth, 18, Component.translatable("gui.roadweaver.preset_editor.name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setResponder(this::onNameChanged);
        this.addRenderableWidget(nameBox);

        int listTop = this.nameBox.getY() + this.nameBox.getHeight() + 4;
        int listHeight = bottomY - listTop - 26;
        this.presetList = new PresetListWidget(minecraft, leftPanelWidth, listHeight, listTop, bottomY - 26, this::selectPresetEntry);
        this.presetList.setLeftPos(leftPanelX);
        this.addRenderableWidget(presetList);

        this.newButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.new"), button -> createNewPreset())
                .bounds(leftPanelX, bottomY - 24, leftPanelWidth / 2 - 2, 20)
                .build();
        this.deleteButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.delete"), button -> deleteActivePreset())
                .bounds(leftPanelX + leftPanelWidth / 2 + 2, bottomY - 24, leftPanelWidth / 2 - 2, 20)
                .build();
        this.addRenderableWidget(newButton);
        this.addRenderableWidget(deleteButton);

        int gap = 6;
        int candidateMinWidth = 160;
        int editorMinWidth = 200;
        int candidateWidth = Math.min(Math.max(candidateMinWidth, rightPanelWidth / 2), 360);
        int maxCandidateWidth = Math.max(candidateMinWidth, rightPanelWidth - editorMinWidth - gap);
        candidateWidth = Math.min(candidateWidth, maxCandidateWidth);
        int editorWidth = rightPanelWidth - candidateWidth - gap;
        if (editorWidth < editorMinWidth) {
            editorWidth = Math.max(editorMinWidth, (rightPanelWidth - gap) / 2);
            candidateWidth = Math.max(candidateMinWidth, rightPanelWidth - gap - editorWidth);
        }

        int editorX = rightPanelX;
        int candidateX = editorX + editorWidth + gap;
        int contentHeight = bottomY - topBarY - 4;
        this.blockCandidateWidget = new BlockCandidateWidget(candidateX, topBarY, candidateWidth, contentHeight, this::onBlockSelectedFromCandidate);
        this.addRenderableWidget(blockCandidateWidget);

        int typeWidth = 76;
        int dimensionWidth = Math.max(90, editorWidth - typeWidth - gap);
        this.dimensionButton = Button.builder(getDimensionButtonText(), button -> toggleDimensionDropdown())
                .bounds(editorX, topBarY, dimensionWidth, 20)
                .build();
        this.addRenderableWidget(dimensionButton);

        this.typeButton = Button.builder(Component.translatable("gui.roadweaver.preset_editor.road_type"), button -> toggleTypeFilter())
                .bounds(editorX + dimensionWidth + gap, topBarY, typeWidth, 20)
                .build();
        this.addRenderableWidget(typeButton);
        updateTypeButton(filterType);

        int topY = topBarY + 30;
        this.editorHeaderY = topY;
        topY += 34;
        int gridCols = Math.min(24, Math.max(1, editorWidth / 18));
        this.baseMaterialGrid = new MaterialGridWidget(editorX, topY, gridCols, 2, Component.translatable("gui.roadweaver.preset_editor.base_materials").getString(), this::removeBaseMaterial, () -> setTargetGrid(true));
        this.addRenderableWidget(baseMaterialGrid);
        topY += 2 * 18 + 20;
        this.slabMaterialGrid = new MaterialGridWidget(editorX, topY, gridCols, 2, Component.translatable("gui.roadweaver.preset_editor.slab_materials").getString(), this::removeSlabMaterial, () -> setTargetGrid(false));
        this.addRenderableWidget(slabMaterialGrid);

        this.editorLeft = editorX;
        this.editorWidth = editorWidth;

        this.saveButton = Button.builder(Component.translatable("gui.roadweaver.common.save"), button -> onSave())
                .bounds(this.width / 2 - 82, this.height - 22, 80, 20)
                .build();
        this.cancelButton = Button.builder(Component.translatable("gui.roadweaver.common.cancel"), button -> onClose())
                .bounds(this.width / 2 + 2, this.height - 22, 80, 20)
                .build();
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);

        if (presets.isEmpty()) {
            loadPresets();
        }
        populateDimensions();
        refreshPresetListUI();
        ensureSelectionMatchesFilter();
    }

    private void populateDimensions() {
        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        List<Identifier> dimensions = new ArrayList<>();
        if (result != null) {
            dimensions.addAll(result.dimensions());
        }
        Identifier overworld = Identifier.parse("minecraft:overworld");
        if (!dimensions.contains(overworld)) {
            dimensions.add(overworld);
        }
        if (filterDimension == null || !dimensions.contains(filterDimension)) {
            filterDimension = dimensions.get(0);
        }
        if (dimensionButton != null) {
            dimensionButton.setMessage(getDimensionButtonText());
            dimensionButton.active = !dimensions.isEmpty();
        }
    }

    private Component getDimensionDisplayName(Identifier dimensionId) {
        String key = "dimension." + dimensionId.getNamespace() + "." + dimensionId.getPath();
        Component translated = Component.translatable(key);
        return !Objects.equals(translated.getString(), key) ? translated : Component.literal(dimensionId.toString());
    }

    private Component getDimensionButtonText() {
        return Component.translatable("config.roadweaver.structure_selection.dimension", getDimensionDisplayName(filterDimension));
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
        for (Identifier dimension : result.dimensions()) {
            Component title = getDimensionDisplayName(dimension);
            Component subtitle = Component.literal(dimension.toString());
            rows.add(new DimensionListWidget.Row(dimension, title, !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }
        if (rows.isEmpty()) {
            return;
        }

        int top = dimensionButton.getY() + dimensionButton.getHeight() + 2;
        int maxHeight = Math.max(height - 24 - top - 4, 44);
        int desiredRows = Math.max(2, Math.min(8, rows.size()));
        int listHeight = Math.min(desiredRows * 22, maxHeight);
        DimensionListWidget list = new DimensionListWidget(minecraft, dimensionButton.getWidth(), listHeight, top, selected -> {
            filterDimension = selected;
            if (dimensionButton != null) {
                dimensionButton.setMessage(getDimensionButtonText());
            }
            pendingCloseDimensionDropdown = true;
            refreshPresetListUI();
            ensureSelectionMatchesFilter();
        });
        list.setLeftPos(dimensionButton.getX());
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
        list.setRows(rows, filterDimension);
        dimensionListWidget = list;
        addRenderableWidget(list);
    }

    private void closeDimensionDropdown() {
        if (dimensionListWidget != null) {
            removeWidget(dimensionListWidget);
            dimensionListWidget = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        DimensionListWidget dropdown = dimensionListWidget;
        if (dropdown != null && dropdown.isMouseOver(mouseX, mouseY)) {
            dropdown.mouseClicked(event, doubleClick);
            return true;
        }
        if (dropdown != null && dimensionButton != null && !dimensionButton.isMouseOver(mouseX, mouseY) && !dropdown.isMouseOver(mouseX, mouseY)) {
            closeDimensionDropdown();
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (pendingCloseDimensionDropdown) {
            pendingCloseDimensionDropdown = false;
            closeDimensionDropdown();
        }
        return handled;
    }

    private void loadPresets() {
        presets.clear();
        originalIds.clear();
        PresetService.reload();
        for (PresetService.PresetDef definition : PresetService.getAllPresets()) {
            UiPreset preset = new UiPreset();
            preset.id = definition.id();
            preset.name = definition.name();
            preset.type = definition.type();
            preset.dimensions = new HashSet<>(definition.dimensions());
            preset.materials = new ArrayList<>(definition.materials());
            preset.slabMaterials = new ArrayList<>(definition.slabMaterials());
            presets.add(preset);
            originalIds.add(preset.id);
        }
    }

    private void selectPresetEntry(PresetListWidget.PresetEntry entry) {
        for (int index = 0; index < presets.size(); index++) {
            if (presets.get(index).id.equals(entry.getId())) {
                selectPreset(index);
                return;
            }
        }
    }

    private void selectPreset(int index) {
        if (index < 0 || index >= presets.size()) {
            return;
        }
        activePresetIndex = index;
        UiPreset preset = presets.get(index);
        nameBox.setValue(preset.name);
        baseMaterialGrid.setMaterials(preset.materials);
        slabMaterialGrid.setMaterials(preset.slabMaterials);
        setTargetGrid(true);
        setEditorActive(true);
        refreshPresetListUI();
    }

    private void setEditorActive(boolean active) {
        nameBox.setEditable(active);
        baseMaterialGrid.active = active;
        slabMaterialGrid.active = active;
        deleteButton.active = active;
        if (!active) {
            nameBox.setValue("");
            baseMaterialGrid.setMaterials(null);
            slabMaterialGrid.setMaterials(null);
        }
    }

    private void createNewPreset() {
        UiPreset preset = new UiPreset();
        preset.id = "custom_" + System.currentTimeMillis();
        preset.name = "New Preset";
        preset.type = filterType;
        preset.dimensions.add(filterDimension);
        if (filterType == PresetService.RoadType.NATURAL) {
            preset.materials.add("minecraft:dirt_path");
        } else {
            preset.materials.add("minecraft:stone_bricks");
            preset.slabMaterials.add("minecraft:stone_brick_slab");
        }
        presets.add(preset);
        selectPreset(presets.size() - 1);
        refreshPresetListUI();
        if (presetList != null) {
            presetList.setScrollAmount(presetList.maxScrollAmount());
        }
    }

    private void deleteActivePreset() {
        if (activePresetIndex >= 0 && activePresetIndex < presets.size()) {
            presets.remove(activePresetIndex);
            activePresetIndex = -1;
            refreshPresetListUI();
            if (!presets.isEmpty()) {
                selectPreset(Math.max(0, presets.size() - 1));
            } else {
                setEditorActive(false);
            }
        }
    }

    private void refreshPresetListUI() {
        if (presetList == null) {
            return;
        }
        presetList.clearPresets();
        for (int index = 0; index < presets.size(); index++) {
            UiPreset preset = presets.get(index);
            if (!matchesFilter(preset)) {
                continue;
            }
            if (preset.type == PresetService.RoadType.NATURAL) {
                Identifier biomeId = tryGetBiomeIdFromPreset(preset);
                if (biomeId != null) {
                    presetList.addPreset(preset.id, getBiomeZhName(biomeId), getBiomeEnName(biomeId), index == activePresetIndex);
                    continue;
                }
            }
            presetList.addPreset(preset.id, preset.name, index == activePresetIndex);
        }
    }

    private Identifier tryGetBiomeIdFromPreset(UiPreset preset) {
        if (preset == null || preset.type != PresetService.RoadType.NATURAL || preset.id == null || !preset.id.startsWith("natural_")) {
            return null;
        }
        String rest = preset.id.substring("natural_".length());
        if (rest.isBlank()) {
            return null;
        }

        Identifier vanilla = Identifier.fromNamespaceAndPath("minecraft", rest);
        int firstUnderscore = rest.indexOf('_');
        if (firstUnderscore > 0 && firstUnderscore < rest.length() - 1) {
            String namespace = rest.substring(0, firstUnderscore);
            String path = rest.substring(firstUnderscore + 1);
            try {
                Identifier candidate = Identifier.fromNamespaceAndPath(namespace, path);
                String key = "biome." + candidate.getNamespace() + "." + candidate.getPath();
                Component translated = Component.translatable(key);
                if (!Objects.equals(translated.getString(), key)) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return vanilla;
    }

    private String getBiomeZhName(Identifier biomeId) {
        String key = "biome." + biomeId.getNamespace() + "." + biomeId.getPath();
        Component translated = Component.translatable(key);
        return Objects.equals(translated.getString(), key) ? biomeId.toString() : translated.getString();
    }

    private String getBiomeEnName(Identifier biomeId) {
        String[] parts = biomeId.getPath().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? biomeId.toString() : builder.toString();
    }

    private boolean matchesFilter(UiPreset preset) {
        return preset != null
                && (filterType == null || preset.type == filterType)
                && (filterDimension == null || (preset.dimensions != null && preset.dimensions.contains(filterDimension)));
    }

    private void ensureSelectionMatchesFilter() {
        if (activePresetIndex >= 0 && activePresetIndex < presets.size() && matchesFilter(presets.get(activePresetIndex))) {
            setEditorActive(true);
            return;
        }
        for (int index = 0; index < presets.size(); index++) {
            if (matchesFilter(presets.get(index))) {
                selectPreset(index);
                return;
            }
        }
        activePresetIndex = -1;
        setEditorActive(false);
        refreshPresetListUI();
    }

    private void onNameChanged(String name) {
        if (activePresetIndex >= 0) {
            presets.get(activePresetIndex).name = name;
            refreshPresetListUI();
        }
    }

    private void toggleTypeFilter() {
        filterType = filterType == PresetService.RoadType.ARTIFICIAL ? PresetService.RoadType.NATURAL : PresetService.RoadType.ARTIFICIAL;
        updateTypeButton(filterType);
        refreshPresetListUI();
        ensureSelectionMatchesFilter();
    }

    private void updateTypeButton(PresetService.RoadType type) {
        if (typeButton != null) {
            typeButton.setMessage(Component.translatable("gui.roadweaver.preset_editor.road_type." + type.name().toLowerCase(Locale.ROOT)));
            typeButton.setTooltip(Tooltip.create(Component.translatable("gui.roadweaver.preset_editor.road_type.tooltip")));
        }
    }

    private void setTargetGrid(boolean base) {
        baseMaterialGrid.setIsTarget(base);
        slabMaterialGrid.setIsTarget(!base);
    }

    private void removeBaseMaterial(int index) {
        if (activePresetIndex >= 0) {
            UiPreset preset = presets.get(activePresetIndex);
            if (index >= 0 && index < preset.materials.size()) {
                preset.materials.remove(index);
                baseMaterialGrid.setMaterials(preset.materials);
            }
        }
    }

    private void removeSlabMaterial(int index) {
        if (activePresetIndex >= 0) {
            UiPreset preset = presets.get(activePresetIndex);
            if (index >= 0 && index < preset.slabMaterials.size()) {
                preset.slabMaterials.remove(index);
                slabMaterialGrid.setMaterials(preset.slabMaterials);
            }
        }
    }

    private void onBlockSelectedFromCandidate(Block block) {
        if (activePresetIndex < 0) {
            return;
        }
        UiPreset preset = presets.get(activePresetIndex);
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return;
        }
        String blockId = id.toString();
        if (baseMaterialGrid.isTarget()) {
            if (preset.materials.size() < 16) {
                preset.materials.add(blockId);
                baseMaterialGrid.setMaterials(preset.materials);
            }
        } else if (preset.slabMaterials.size() < 16) {
            preset.slabMaterials.add(blockId);
            slabMaterialGrid.setMaterials(preset.slabMaterials);
        }
    }

    private void onSave() {
        Set<String> currentIds = presets.stream().map(preset -> preset.id).collect(Collectors.toSet());
        for (String oldId : originalIds) {
            if (!currentIds.contains(oldId)) {
                PresetService.deletePresetFile(oldId);
            }
        }
        for (UiPreset preset : presets) {
            PresetService.saveOrUpdatePresetFile(preset.id, preset.name, preset.type, new ArrayList<>(preset.dimensions), preset.materials, preset.slabMaterials);
        }
        PresetService.reload();
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        UiPreset preset = activePresetIndex >= 0 && activePresetIndex < presets.size() ? presets.get(activePresetIndex) : null;
        if (preset != null && preset.type == PresetService.RoadType.NATURAL) {
            Identifier biomeId = tryGetBiomeIdFromPreset(preset);
            if (biomeId != null) {
                String zh = getBiomeZhName(biomeId);
                String en = getBiomeEnName(biomeId);

                graphics.pose().pushMatrix();
                graphics.pose().translate(editorLeft, editorHeaderY);
                graphics.pose().scale(ZH_SCALE, ZH_SCALE);
                graphics.drawString(font, font.plainSubstrByWidth(zh, (int) (editorWidth / ZH_SCALE)), 0, 0, 0xFFFFFFFF, false);
                graphics.pose().popMatrix();

                graphics.pose().pushMatrix();
                graphics.pose().translate(editorLeft, editorHeaderY + 18);
                graphics.pose().scale(EN_SCALE, EN_SCALE);
                graphics.drawString(font, font.plainSubstrByWidth(en, (int) (editorWidth / EN_SCALE)), 0, 0, 0xFFBBBBBB, false);
                graphics.pose().popMatrix();
            }
        }

        graphics.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
