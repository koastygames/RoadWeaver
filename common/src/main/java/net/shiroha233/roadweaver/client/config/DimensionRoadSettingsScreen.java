package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.DimensionRoadSettings;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 维度道路设置界面。
 */
public class DimensionRoadSettingsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private final Screen parent;
    private final Map<String, DimensionRoadSettings> working = new HashMap<>();
    private final List<Identifier> allDimensions = new ArrayList<>();

    private Identifier selectedDimension;
    private DimensionListWidget dimensionList;
    private List<Identifier> dimensionsForList = new ArrayList<>();

    private Button roadsEnabledBtn;
    private Button bridgeEnabledBtn;
    private Button pathfindingBtn;
    private Button slopeLimitBtn;
    private Button highwayEnabledBtn;
    private Button roadsideStructuresBtn;
    private Button roadSignsBtn;
    private Button resetDimensionBtn;
    private Button cancelBtn;
    private Button doneBtn;

    public DimensionRoadSettingsScreen(Screen parent) {
        super(Component.translatable("gui.roadweaver.dimension_road_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadWorkingCopy();
        buildDimensionList();
        buildRightPanel();
        refreshOptionButtons();
    }

    private void loadWorkingCopy() {
        working.clear();
        allDimensions.clear();

        ModConfig config = ConfigService.get();
        if (config != null && config.dimensionRoadSettings() != null) {
            for (var entry : config.dimensionRoadSettings().entrySet()) {
                if (entry != null && entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                    working.put(entry.getKey(), entry.getValue().copy());
                }
            }
        }

        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result != null && result.dimensions() != null) {
            allDimensions.addAll(result.dimensions());
        }
        addFallbackDimension("minecraft:overworld");
        addFallbackDimension("minecraft:the_nether");
        addFallbackDimension("minecraft:the_end");

        for (String value : working.keySet()) {
            Identifier identifier = Identifier.tryParse(value);
            if (identifier != null && !allDimensions.contains(identifier)) {
                allDimensions.add(identifier);
            }
        }

        if (selectedDimension == null || !allDimensions.contains(selectedDimension)) {
            selectedDimension = allDimensions.isEmpty() ? null : allDimensions.get(0);
        }
    }

    private void addFallbackDimension(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null && !allDimensions.contains(identifier)) {
            allDimensions.add(identifier);
        }
    }

    private void buildDimensionList() {
        int top = 44;
        int bottom = this.height - 44;
        int listWidth = Math.max(140, Math.min(220, this.width / 3));
        int listHeight = Math.max(44, bottom - top);

        this.dimensionsForList = new ArrayList<>(allDimensions);
        DimensionListWidget list = new DimensionListWidget(Minecraft.getInstance(), listWidth, listHeight, top, selected -> {
            selectedDimension = selected;
            rebuildDimensionList();
            refreshOptionButtons();
        });
        list.setLeftPos(10);
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
        list.setRows(buildRows(dimensionsForList), selectedDimension);
        this.dimensionList = list;
        addRenderableWidget(list);
    }

    private void rebuildDimensionList() {
        if (dimensionList != null) {
            dimensionList.setRows(buildRows(dimensionsForList), selectedDimension);
        }
    }

    private List<DimensionListWidget.Row> buildRows(List<Identifier> dimensions) {
        List<DimensionListWidget.Row> rows = new ArrayList<>();
        for (Identifier dimension : dimensions) {
            Component title = getDimensionDisplayName(dimension);
            Component subtitle = Component.literal(dimension.toString());
            rows.add(new DimensionListWidget.Row(dimension, title, !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }
        return rows;
    }

    private void buildRightPanel() {
        int leftWidth = Math.max(140, Math.min(220, this.width / 3));
        int x = 10 + leftWidth + 10;
        int width = this.width - x - 10;
        int y = 56;

        roadsEnabledBtn = Button.builder(Component.empty(), button -> toggleBool("roadsEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        bridgeEnabledBtn = Button.builder(Component.empty(), button -> toggleBool("bridgeEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        pathfindingBtn = Button.builder(Component.empty(), button -> cyclePathfindingAlgorithm()).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        slopeLimitBtn = Button.builder(Component.empty(), button -> toggleBool("slopeLimitEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        highwayEnabledBtn = Button.builder(Component.empty(), button -> toggleBool("highwayEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        roadsideStructuresBtn = Button.builder(Component.empty(), button -> toggleBool("roadsideStructuresEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;
        roadSignsBtn = Button.builder(Component.empty(), button -> toggleBool("roadSignsEnabled")).pos(x, y).size(width, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + 10;
        resetDimensionBtn = Button.builder(Component.translatable("gui.roadweaver.dimension_road_settings.reset_dimension"), button -> resetSelectedDimension()).pos(x, y).size(width, BUTTON_HEIGHT).build();

        int buttonY = this.height - 28;
        int buttonWidth = 90;
        int spacing = 8;
        int startX = x + Math.max(0, (width - (buttonWidth * 2 + spacing)) / 2);
        cancelBtn = Button.builder(Component.translatable("gui.cancel"), button -> onCancel()).pos(startX, buttonY).size(buttonWidth, BUTTON_HEIGHT).build();
        doneBtn = Button.builder(Component.translatable("gui.done"), button -> onDone()).pos(startX + buttonWidth + spacing, buttonY).size(buttonWidth, BUTTON_HEIGHT).build();

        addRenderableWidget(roadsEnabledBtn);
        addRenderableWidget(bridgeEnabledBtn);
        addRenderableWidget(pathfindingBtn);
        addRenderableWidget(slopeLimitBtn);
        addRenderableWidget(highwayEnabledBtn);
        addRenderableWidget(roadsideStructuresBtn);
        addRenderableWidget(roadSignsBtn);
        addRenderableWidget(resetDimensionBtn);
        addRenderableWidget(cancelBtn);
        addRenderableWidget(doneBtn);
    }

    private void refreshOptionButtons() {
        boolean hasSelection = selectedDimension != null;
        roadsEnabledBtn.active = hasSelection;
        bridgeEnabledBtn.active = hasSelection;
        pathfindingBtn.active = hasSelection;
        slopeLimitBtn.active = hasSelection;
        highwayEnabledBtn.active = hasSelection;
        roadsideStructuresBtn.active = hasSelection;
        roadSignsBtn.active = hasSelection;
        resetDimensionBtn.active = hasSelection;

        DimensionRoadSettings settings = getWorkingSettings(selectedDimension);
        roadsEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.roads", settings == null ? null : settings.roadsEnabled()));
        bridgeEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.bridge", settings == null ? null : settings.bridgeEnabled()));
        pathfindingBtn.setMessage(pathfindingLine(settings == null ? null : settings.pathfindingAlgorithm()));
        slopeLimitBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.height_smoothing", settings == null ? null : settings.slopeLimitEnabled()));
        highwayEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.highway", settings == null ? null : settings.highwayEnabled()));
        roadsideStructuresBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.roadside_structures", settings == null ? null : settings.roadsideStructuresEnabled()));
        roadSignsBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.road_signs", settings == null ? null : settings.roadSignsEnabled()));
    }

    private static Component triStateLine(String optionKey, Boolean value) {
        Component state = value == null
                ? Component.translatable("gui.roadweaver.dimension_road_settings.state.inherit")
                : value
                ? Component.translatable("gui.roadweaver.dimension_road_settings.state.enabled")
                : Component.translatable("gui.roadweaver.dimension_road_settings.state.disabled");
        return Component.translatable("gui.roadweaver.dimension_road_settings.option_format", Component.translatable(optionKey), state);
    }

    private static Component pathfindingLine(PathfindingCostConfig.PathfindingAlgorithm algorithm) {
        Component state = algorithm == null
                ? Component.translatable("gui.roadweaver.dimension_road_settings.state.inherit")
                : Component.translatable("config.roadweaver.pathfinding_algorithm.option." + algorithm.name().toLowerCase(Locale.ROOT));
        return Component.translatable("gui.roadweaver.dimension_road_settings.option_format", Component.translatable("gui.roadweaver.dimension_road_settings.option.pathfinding"), state);
    }

    private void toggleBool(String field) {
        if (selectedDimension == null) {
            return;
        }
        String dimensionId = selectedDimension.toString();
        DimensionRoadSettings settings = getOrCreateWorkingSettings(dimensionId);
        switch (field) {
            case "roadsEnabled" -> settings.setRoadsEnabled(nextTriState(settings.roadsEnabled()));
            case "bridgeEnabled" -> settings.setBridgeEnabled(nextTriState(settings.bridgeEnabled()));
            case "slopeLimitEnabled" -> settings.setSlopeLimitEnabled(nextTriState(settings.slopeLimitEnabled()));
            case "highwayEnabled" -> settings.setHighwayEnabled(nextTriState(settings.highwayEnabled()));
            case "roadsideStructuresEnabled" -> settings.setRoadsideStructuresEnabled(nextTriState(settings.roadsideStructuresEnabled()));
            case "roadSignsEnabled" -> settings.setRoadSignsEnabled(nextTriState(settings.roadSignsEnabled()));
            default -> {
                return;
            }
        }
        if (settings.isAllInherit()) {
            working.remove(dimensionId);
        }
        refreshOptionButtons();
    }

    private void cyclePathfindingAlgorithm() {
        if (selectedDimension == null) {
            return;
        }
        String dimensionId = selectedDimension.toString();
        DimensionRoadSettings settings = getOrCreateWorkingSettings(dimensionId);
        settings.setPathfindingAlgorithm(nextPathfinding(settings.pathfindingAlgorithm()));
        if (settings.isAllInherit()) {
            working.remove(dimensionId);
        }
        refreshOptionButtons();
    }

    private void resetSelectedDimension() {
        if (selectedDimension != null) {
            working.remove(selectedDimension.toString());
            refreshOptionButtons();
        }
    }

    private static Boolean nextTriState(Boolean value) {
        if (value == null) {
            return Boolean.TRUE;
        }
        if (Boolean.TRUE.equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static PathfindingCostConfig.PathfindingAlgorithm nextPathfinding(PathfindingCostConfig.PathfindingAlgorithm value) {
        if (value == null) {
            return PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BASIC;
        }
        return switch (value) {
            case ASTAR_BASIC -> PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL;
            case ASTAR_BIDIRECTIONAL -> PathfindingCostConfig.PathfindingAlgorithm.GRADIENT_DESCENT;
            case GRADIENT_DESCENT -> PathfindingCostConfig.PathfindingAlgorithm.POTENTIAL_FIELD;
            case POTENTIAL_FIELD -> null;
        };
    }

    private DimensionRoadSettings getWorkingSettings(Identifier dimensionId) {
        return dimensionId == null ? null : working.get(dimensionId.toString());
    }

    private DimensionRoadSettings getOrCreateWorkingSettings(String dimensionId) {
        return working.computeIfAbsent(dimensionId, ignored -> new DimensionRoadSettings());
    }

    private Component getDimensionDisplayName(Identifier dimensionId) {
        if (dimensionId == null) {
            return Component.literal("-");
        }
        String key = "dimension." + dimensionId.getNamespace() + "." + dimensionId.getPath();
        Component translated = Component.translatable(key);
        return !Objects.equals(translated.getString(), key) ? translated : Component.literal(dimensionId.toString());
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onDone() {
        Map<String, DimensionRoadSettings> cleaned = new HashMap<>(working);
        cleaned.values().removeIf(settings -> settings == null || settings.isAllInherit());
        ModConfig config = ConfigService.get();
        if (config != null) {
            config.setDimensionRoadSettings(cleaned);
        }
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.roadweaver.dimension_road_settings.subtitle"), this.width / 2, 24, 0xFFAAAAAA);
        if (selectedDimension != null) {
            Component currentDimension = Component.translatable("gui.roadweaver.dimension_road_settings.current_dimension", Component.literal(selectedDimension.toString()));
            graphics.drawString(this.font, currentDimension, 10 + Math.max(140, Math.min(220, this.width / 3)) + 10, 40, 0xFFFFFFFF, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
