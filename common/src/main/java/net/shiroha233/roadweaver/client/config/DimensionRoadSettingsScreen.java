package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.render.RoadWeaverScreen;
import net.shiroha233.roadweaver.client.render.ScreenBackgrounds;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.DimensionRoadSettings;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;

import java.util.*;

/**
 * 维度道路设置界面
 */
public class DimensionRoadSettingsScreen extends RoadWeaverScreen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    
    private final Screen parent;
    private final Map<String, DimensionRoadSettings> working = new HashMap<>();
    private final List<ResourceLocation> allDimensions = new ArrayList<>();
    
    private ResourceLocation selectedDimension;
    private DimensionListWidget dimensionList;
    private List<ResourceLocation> dimensionsForList = new ArrayList<>();
    
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

        ModConfig cfg = ConfigService.get();
        if (cfg != null && cfg.dimensionRoadSettings() != null) {
            for (var e : cfg.dimensionRoadSettings().entrySet()) {
                if (e == null || e.getKey() == null || e.getKey().isEmpty() || e.getValue() == null)
                    continue;
                working.put(e.getKey(), e.getValue().copy());
            }
        }

        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result != null && result.dimensions() != null) {
            allDimensions.addAll(result.dimensions());
        }

        addFallbackDimension("minecraft:overworld");
        addFallbackDimension("minecraft:the_nether");
        addFallbackDimension("minecraft:the_end");

        for (String s : working.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null && !allDimensions.contains(rl)) {
                allDimensions.add(rl);
            }
        }

        if (selectedDimension == null || !allDimensions.contains(selectedDimension)) {
            selectedDimension = allDimensions.isEmpty() ? null : allDimensions.get(0);
        }
    }

    private void addFallbackDimension(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        if (!allDimensions.contains(rl)) {
            allDimensions.add(rl);
        }
    }

    private void buildDimensionList() {
        int top = 44;
        int bottom = this.height - 44;
        int listW = Math.max(140, Math.min(220, this.width / 3));
        int listH = Math.max(44, bottom - top);

        this.dimensionsForList = new ArrayList<>(allDimensions);

        DimensionListWidget list = new DimensionListWidget(Minecraft.getInstance(), listW, listH, top, selected -> {
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
        if (dimensionList == null) return;
        dimensionList.setRows(buildRows(dimensionsForList), selectedDimension);
    }

    private List<DimensionListWidget.Row> buildRows(List<ResourceLocation> dims) {
        List<DimensionListWidget.Row> rows = new ArrayList<>();
        for (ResourceLocation dimId : dims) {
            Component title = getDimensionDisplayName(dimId);
            Component subtitle = Component.literal(dimId.toString());
            rows.add(new DimensionListWidget.Row(dimId, title,
                    !Objects.equals(title.getString(), subtitle.getString()) ? subtitle : null));
        }
        return rows;
    }

    private void buildRightPanel() {
        int leftW = Math.max(140, Math.min(220, this.width / 3));
        int x = 10 + leftW + 10;
        int w = this.width - x - 10;
        int y = 56;

        roadsEnabledBtn = Button.builder(Component.empty(), b -> toggleBool("roadsEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        bridgeEnabledBtn = Button.builder(Component.empty(), b -> toggleBool("bridgeEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        pathfindingBtn = Button.builder(Component.empty(), b -> cyclePathfindingAlgorithm())
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        slopeLimitBtn = Button.builder(Component.empty(), b -> toggleBool("slopeLimitEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        highwayEnabledBtn = Button.builder(Component.empty(), b -> toggleBool("highwayEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        roadsideStructuresBtn = Button.builder(Component.empty(), b -> toggleBool("roadsideStructuresEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + BUTTON_GAP;

        roadSignsBtn = Button.builder(Component.empty(), b -> toggleBool("roadSignsEnabled"))
                .pos(x, y).size(w, BUTTON_HEIGHT).build();
        y += BUTTON_HEIGHT + 10;

        resetDimensionBtn = Button.builder(Component.translatable("gui.roadweaver.dimension_road_settings.reset_dimension"),
                        b -> resetSelectedDimension())
                .pos(x, y).size(w, BUTTON_HEIGHT).build();

        int btnY = this.height - 28;
        int btnW = 90;
        int spacing = 8;
        int totalW = btnW * 2 + spacing;
        int startX = x + Math.max(0, (w - totalW) / 2);

        cancelBtn = Button.builder(Component.translatable("gui.cancel"), b -> onCancel())
                .pos(startX, btnY).size(btnW, BUTTON_HEIGHT).build();
        doneBtn = Button.builder(Component.translatable("gui.done"), b -> onDone())
                .pos(startX + btnW + spacing, btnY).size(btnW, BUTTON_HEIGHT).build();

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

        DimensionRoadSettings s = getWorkingSettings(selectedDimension);

        roadsEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.roads",
                s == null ? null : s.roadsEnabled()));
        bridgeEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.bridge",
                s == null ? null : s.bridgeEnabled()));
        pathfindingBtn.setMessage(pathfindingLine(s == null ? null : s.pathfindingAlgorithm()));
        slopeLimitBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.height_smoothing",
                s == null ? null : s.slopeLimitEnabled()));
        highwayEnabledBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.highway",
                s == null ? null : s.highwayEnabled()));
        roadsideStructuresBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.roadside_structures",
                        s == null ? null : s.roadsideStructuresEnabled()));
        roadSignsBtn.setMessage(triStateLine("gui.roadweaver.dimension_road_settings.option.road_signs",
                s == null ? null : s.roadSignsEnabled()));
    }

    private static Component triStateLine(String optionKey, Boolean v) {
        Component state = (v == null)
                ? Component.translatable("gui.roadweaver.dimension_road_settings.state.inherit")
                : (v ? Component.translatable("gui.roadweaver.dimension_road_settings.state.enabled")
                        : Component.translatable("gui.roadweaver.dimension_road_settings.state.disabled"));
        return Component.translatable("gui.roadweaver.dimension_road_settings.option_format",
                Component.translatable(optionKey), state);
    }

    private static Component pathfindingLine(PathfindingCostConfig.PathfindingAlgorithm algo) {
        Component state;
        if (algo == null) {
            state = Component.translatable("gui.roadweaver.dimension_road_settings.state.inherit");
        } else {
            state = Component.translatable(
                    "config.roadweaver.pathfinding_algorithm.option." + algo.name().toLowerCase(Locale.ROOT));
        }
        return Component.translatable("gui.roadweaver.dimension_road_settings.option_format",
                Component.translatable("gui.roadweaver.dimension_road_settings.option.pathfinding"), state);
    }

    private void toggleBool(String field) {
        if (selectedDimension == null) return;
        String dimId = selectedDimension.toString();
        DimensionRoadSettings s = getOrCreateWorkingSettings(dimId);

        Boolean next;
        switch (field) {
            case "roadsEnabled" -> {
                next = nextTriState(s.roadsEnabled());
                s.setRoadsEnabled(next);
            }
            case "bridgeEnabled" -> {
                next = nextTriState(s.bridgeEnabled());
                s.setBridgeEnabled(next);
            }
            case "slopeLimitEnabled" -> {
                next = nextTriState(s.slopeLimitEnabled());
                s.setSlopeLimitEnabled(next);
            }
            case "highwayEnabled" -> {
                next = nextTriState(s.highwayEnabled());
                s.setHighwayEnabled(next);
            }
            case "roadsideStructuresEnabled" -> {
                next = nextTriState(s.roadsideStructuresEnabled());
                s.setRoadsideStructuresEnabled(next);
            }
            case "roadSignsEnabled" -> {
                next = nextTriState(s.roadSignsEnabled());
                s.setRoadSignsEnabled(next);
            }
            default -> {
                return;
            }
        }

        if (s.isAllInherit()) {
            working.remove(dimId);
        }
        refreshOptionButtons();
    }

    private void cyclePathfindingAlgorithm() {
        if (selectedDimension == null) return;
        String dimId = selectedDimension.toString();

        DimensionRoadSettings s = getOrCreateWorkingSettings(dimId);
        PathfindingCostConfig.PathfindingAlgorithm next = nextPathfinding(s.pathfindingAlgorithm());
        s.setPathfindingAlgorithm(next);

        if (s.isAllInherit()) {
            working.remove(dimId);
        }
        refreshOptionButtons();
    }

    private void resetSelectedDimension() {
        if (selectedDimension == null) return;
        working.remove(selectedDimension.toString());
        refreshOptionButtons();
    }

    private static Boolean nextTriState(Boolean v) {
        if (v == null) return Boolean.TRUE;
        if (Boolean.TRUE.equals(v)) return Boolean.FALSE;
        return null;
    }

    private static PathfindingCostConfig.PathfindingAlgorithm nextPathfinding(PathfindingCostConfig.PathfindingAlgorithm v) {
        if (v == null) return PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BASIC;
        return switch (v) {
            case ASTAR_BASIC -> PathfindingCostConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL;
            case ASTAR_BIDIRECTIONAL -> PathfindingCostConfig.PathfindingAlgorithm.GRADIENT_DESCENT;
            case GRADIENT_DESCENT -> PathfindingCostConfig.PathfindingAlgorithm.POTENTIAL_FIELD;
            case POTENTIAL_FIELD -> null;
        };
    }

    private DimensionRoadSettings getWorkingSettings(ResourceLocation dimId) {
        if (dimId == null) return null;
        return working.get(dimId.toString());
    }

    private DimensionRoadSettings getOrCreateWorkingSettings(String dimId) {
        DimensionRoadSettings s = working.get(dimId);
        if (s == null) {
            s = new DimensionRoadSettings();
            working.put(dimId, s);
        }
        return s;
    }

    private Component getDimensionDisplayName(ResourceLocation dimId) {
        if (dimId == null) return Component.literal("-");
        String key = "dimension." + dimId.getNamespace() + "." + dimId.getPath();
        Component translated = Component.translatable(key);
        if (!Objects.equals(translated.getString(), key)) {
            return translated;
        }
        return Component.literal(dimId.toString());
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onDone() {
        Map<String, DimensionRoadSettings> cleaned = new HashMap<>(working);
        try {
            cleaned.values().removeIf(s -> s == null || s.isAllInherit());
        } catch (Throwable ignored) {}

        ModConfig cfg = ConfigService.get();
        if (cfg != null) {
            cfg.setDimensionRoadSettings(cleaned);
        }

        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ScreenBackgrounds.render(g, this.width, this.height);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.roadweaver.dimension_road_settings.subtitle"),
                this.width / 2, 24, 0xAAAAAA);

        if (selectedDimension != null) {
            Component dimLine = Component.translatable(
                    "gui.roadweaver.dimension_road_settings.current_dimension",
                    Component.literal(selectedDimension.toString()));
            g.drawString(this.font, dimLine, 10 + Math.max(140, Math.min(220, this.width / 3)) + 10, 40, 0xFFFFFF, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
