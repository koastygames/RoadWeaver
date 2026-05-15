package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构预测维度白名单界面。
 */
public class StructurePredictionDimensionWhitelistScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final Set<Identifier> selected = new LinkedHashSet<>();
    private final List<Identifier> allDimensions = new ArrayList<>();

    private Button doneButton;
    private Button cancelButton;

    public StructurePredictionDimensionWhitelistScreen(Screen parent) {
        super(Component.translatable("config.roadweaver.structure_prediction_dimension_whitelist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        selected.clear();
        allDimensions.clear();

        ModConfig config = ConfigService.get();
        if (config != null && config.structurePredictionDimensionWhitelist() != null) {
            for (String value : config.structurePredictionDimensionWhitelist()) {
                Identifier identifier = Identifier.tryParse(value);
                if (identifier != null) {
                    selected.add(identifier);
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
        for (Identifier identifier : selected) {
            if (!allDimensions.contains(identifier)) {
                allDimensions.add(identifier);
            }
        }

        int top = 32;
        int bottom = this.height - 44;
        MultiDimensionListWidget list = new MultiDimensionListWidget(Minecraft.getInstance(), this.width, bottom - top, top, bottom, selected);
        list.setDimensions(allDimensions);
        addRenderableWidget(list);

        int buttonY = this.height - 28;
        int buttonWidth = 90;
        int spacing = 8;
        int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;
        cancelButton = Button.builder(Component.translatable("gui.cancel"), button -> onCancel()).pos(startX, buttonY).size(buttonWidth, BUTTON_HEIGHT).build();
        doneButton = Button.builder(Component.translatable("gui.done"), button -> onDone()).pos(startX + buttonWidth + spacing, buttonY).size(buttonWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(cancelButton);
        addRenderableWidget(doneButton);
    }

    private void addFallbackDimension(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null && !allDimensions.contains(identifier)) {
            allDimensions.add(identifier);
        }
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onDone() {
        ModConfig config = ConfigService.get();
        if (config != null) {
            List<String> output = new ArrayList<>();
            for (Identifier identifier : selected) {
                output.add(identifier.toString());
            }
            config.setStructurePredictionDimensionWhitelist(output);
        }
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("config.roadweaver.structure_prediction_dimension_whitelist.subtitle"), this.width / 2, 22, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
