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
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构预测维度白名单界面
 */
public class StructurePredictionDimensionWhitelistScreen extends RoadWeaverScreen {
    private static final int BUTTON_HEIGHT = 20;
    
    private final Screen parent;
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private final List<ResourceLocation> allDimensions = new ArrayList<>();
    
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

        ModConfig cfg = ConfigService.get();
        if (cfg != null && cfg.structurePredictionDimensionWhitelist() != null) {
            for (String s : cfg.structurePredictionDimensionWhitelist()) {
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl != null) {
                    selected.add(rl);
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

        for (ResourceLocation rl : selected) {
            if (!allDimensions.contains(rl)) {
                allDimensions.add(rl);
            }
        }

        int top = 32;
        int bottom = this.height - 44;

        MultiDimensionListWidget list = new MultiDimensionListWidget(Minecraft.getInstance(), this.width, bottom - top, top, bottom, selected);
        list.setDimensions(allDimensions);
        addRenderableWidget(list);

        int btnY = this.height - 28;
        int btnW = 90;
        int spacing = 8;
        int totalW = btnW * 2 + spacing;
        int startX = (this.width - totalW) / 2;

        cancelButton = Button.builder(Component.translatable("gui.cancel"), b -> onCancel())
                .pos(startX, btnY).size(btnW, BUTTON_HEIGHT).build();
        doneButton = Button.builder(Component.translatable("gui.done"), b -> onDone())
                .pos(startX + btnW + spacing, btnY).size(btnW, BUTTON_HEIGHT).build();

        addRenderableWidget(cancelButton);
        addRenderableWidget(doneButton);
    }

    private void addFallbackDimension(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        if (!allDimensions.contains(rl)) {
            allDimensions.add(rl);
        }
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onDone() {
        ModConfig cfg = ConfigService.get();
        if (cfg != null) {
            List<String> out = new ArrayList<>();
            for (ResourceLocation rl : selected) {
                out.add(rl.toString());
            }
            cfg.setStructurePredictionDimensionWhitelist(out);
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
                Component.translatable("config.roadweaver.structure_prediction_dimension_whitelist.subtitle"),
                this.width / 2, 22, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
