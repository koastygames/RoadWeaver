package net.shiroha233.roadweaver.client26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.Locale;

/** Lightweight native Minecraft 26.2 configuration screen for RoadWeaver. */
public final class RoadWeaverConfigScreen26 extends Screen {
    private final Screen parent;

    public RoadWeaverConfigScreen26(Screen parent) {
        super(Component.translatable("config.roadweaver.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig cfg = ConfigService.get();
        int columnWidth = Math.min(230, Math.max(150, (width - 54) / 2));
        int left = Math.max(18, width / 2 - columnWidth - 6);
        int right = width / 2 + 6;
        int y = 50;
        int row = 24;

        addToggle(left, y, columnWidth, "Road generation", cfg.roadAppearance().roadsEnabled(), v -> cfg.roadAppearance().setRoadsEnabled(v));
        addToggle(right, y, columnWidth, "Structure prediction", cfg.structurePrediction().enabled(), v -> cfg.structurePrediction().setEnabled(v));
        y += row;
        addToggle(left, y, columnWidth, "Dynamic planning", cfg.planning().dynamicPlanEnabled(), v -> cfg.planning().setDynamicPlanEnabled(v));
        addToggle(right, y, columnWidth, "Highways", cfg.highway().enabled(), v -> cfg.highway().setEnabled(v));
        y += row;
        addToggle(left, y, columnWidth, "Bridges", cfg.bridge().enabled(), v -> cfg.bridge().setEnabled(v));
        addToggle(right, y, columnWidth, "Roadside structures", cfg.roadsideStructure().enabled(), v -> cfg.roadsideStructure().setEnabled(v));
        y += row;
        addToggle(left, y, columnWidth, "Loading tips", cfg.client().loadingTipsEnabled(), v -> cfg.client().setLoadingTipsEnabled(v));
        addToggle(right, y, columnWidth, "Loading progress", cfg.client().loadingProgressEnabled(), v -> cfg.client().setLoadingProgressEnabled(v));
        y += row + 8;

        int half = (columnWidth - 54) / 2;
        addRenderableWidget(Button.builder(Component.literal("Radius - (" + cfg.structurePrediction().predictRadiusChunks() + ")"), b -> {
            int value = Math.max(1, cfg.structurePrediction().predictRadiusChunks() - 8);
            cfg.structurePrediction().setPredictRadiusChunks(value); save();
            b.setMessage(Component.literal("Radius - (" + value + ")"));
        }).bounds(left, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Radius + (" + cfg.structurePrediction().predictRadiusChunks() + ")"), b -> {
            int value = Math.min(4096, cfg.structurePrediction().predictRadiusChunks() + 8);
            cfg.structurePrediction().setPredictRadiusChunks(value); save();
            b.setMessage(Component.literal("Radius + (" + value + ")"));
        }).bounds(left + half + 22, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Road width - (" + cfg.roadAppearance().roadWidth() + ")"), b -> {
            int value = Math.max(1, cfg.roadAppearance().roadWidth() - 1);
            cfg.roadAppearance().setRoadWidth(value); save();
            b.setMessage(Component.literal("Road width - (" + value + ")"));
        }).bounds(right, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Road width + (" + cfg.roadAppearance().roadWidth() + ")"), b -> {
            int value = Math.min(31, cfg.roadAppearance().roadWidth() + 1);
            cfg.roadAppearance().setRoadWidth(value); save();
            b.setMessage(Component.literal("Road width + (" + value + ")"));
        }).bounds(right + half + 22, y, half + 18, 20).build());
        y += row;

        addRenderableWidget(Button.builder(Component.literal("Hwy width - (" + cfg.highway().roadWidth() + ")"), b -> {
            int value = Math.max(1, cfg.highway().roadWidth() - 1);
            cfg.highway().setRoadWidth(value); save();
            b.setMessage(Component.literal("Hwy width - (" + value + ")"));
        }).bounds(left, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hwy width + (" + cfg.highway().roadWidth() + ")"), b -> {
            int value = Math.min(31, cfg.highway().roadWidth() + 1);
            cfg.highway().setRoadWidth(value); save();
            b.setMessage(Component.literal("Hwy width + (" + value + ")"));
        }).bounds(left + half + 22, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal(curveLabel("Curve -", cfg.highway().routeCurvature())), b -> {
            double value = Math.max(0.0, cfg.highway().routeCurvature() - 0.005);
            cfg.highway().setRouteCurvature(value); save();
            b.setMessage(Component.literal(curveLabel("Curve -", cfg.highway().routeCurvature())));
        }).bounds(right, y, half + 18, 20).build());
        addRenderableWidget(Button.builder(Component.literal(curveLabel("Curve +", cfg.highway().routeCurvature())), b -> {
            double value = Math.min(0.12, cfg.highway().routeCurvature() + 0.005);
            cfg.highway().setRouteCurvature(value); save();
            b.setMessage(Component.literal(curveLabel("Curve +", cfg.highway().routeCurvature())));
        }).bounds(right + half + 22, y, half + 18, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save & Done"), b -> { save(); onClose(); })
                .bounds(Math.max(18, width / 2 - 70), Math.max(y + 34, height - 34), 140, 20).build());
    }

    private void addToggle(int x, int y, int width, String name, boolean initial, BoolSetter setter) {
        final boolean[] state = {initial};
        Button button = Button.builder(toggleLabel(name, state[0]), b -> {
            state[0] = !state[0]; setter.set(state[0]); save(); b.setMessage(toggleLabel(name, state[0]));
        }).bounds(x, y, width, 20).build();
        addRenderableWidget(button);
    }

    private static Component toggleLabel(String name, boolean value) {
        return Component.literal(name + ": " + (value ? "ON" : "OFF"));
    }

    private static String curveLabel(String prefix, double value) {
        return prefix + " (" + String.format(Locale.ROOT, "%.1f%%", value * 100.0) + ")";
    }

    private static void save() {
        ConfigService.get().sanitize();
        ConfigService.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String titleText = getTitle().getString();
        graphics.text(font, titleText, Math.max(18, (width - font.width(titleText)) / 2), 18, 0xFFFFFFFF, true);
        graphics.text(font, "Core RoadWeaver settings — highway curve affects newly generated routes",
                Math.max(18, width / 2 - 205), 34, 0xFFB8C0C8, true);
    }

    @Override
    public void onClose() {
        save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
    @FunctionalInterface private interface BoolSetter { void set(boolean value); }
}
