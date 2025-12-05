package net.shiroha233.roadweaver.client.roadside;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.structures.roadside.model.RoadsideDecorationSpec;
import net.shiroha233.roadweaver.structures.roadside.registry.RoadsideRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 路边结构浏览界面：左侧列表，右侧 3D 预览。
 * 只负责 UI 逻辑，具体渲染委托给 StructurePreviewRenderer。
 */
public class RoadsideStructureBrowserScreen extends Screen {
    private static final int ENTRY_HEIGHT = 18;

    private final Screen parent;
    private final List<RoadsideDecorationSpec> specs = new ArrayList<>();
    private final StructurePreviewRenderer previewRenderer = new StructurePreviewRenderer();

    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public RoadsideStructureBrowserScreen(Screen parent) {
        super(Component.translatable("gui.roadweaver.roadside_browser.title"));
        this.parent = parent;
        for (RoadsideDecorationSpec spec : RoadsideRegistry.all()) {
            this.specs.add(spec);
        }
        this.specs.sort(Comparator.comparing(s -> s.id().toString()));
        if (this.selectedIndex >= this.specs.size()) {
            this.selectedIndex = this.specs.isEmpty() ? -1 : 0;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.cancel"), b -> onClose())
                .bounds(cx - 40, this.height - 30, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        // 主面板区域
        int panelWidth = Math.min(500, this.width - 40);
        int panelHeight = Math.min(320, this.height - 60);
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = 35;

        // 绘制主面板背景
        gfx.fill(panelLeft - 2, panelTop - 2, panelLeft + panelWidth + 2, panelTop + panelHeight + 2, 0xFF333333);
        gfx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xCC000000);

        // 标题
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 左侧列表区域
        int listWidth = (int)(panelWidth * 0.45f);
        int listLeft = panelLeft + 8;
        int listTop = panelTop + 8;
        int listBottom = panelTop + panelHeight - 8;

        // 列表背景
        gfx.fill(listLeft, listTop, listLeft + listWidth, listBottom, 0x80000000);

        renderList(gfx, mouseX, mouseY, listLeft, listTop, listWidth, listBottom);

        // 右侧预览区域
        int previewLeft = listLeft + listWidth + 12;
        int previewWidth = panelWidth - listWidth - 28;
        int previewTop = listTop;
        int previewHeight = listBottom - listTop;

        // 预览背景
        gfx.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, 0x80000000);

        renderPreview(gfx, partialTick, previewLeft, previewTop, previewWidth, previewHeight);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics gfx, int mouseX, int mouseY, int left, int top, int width, int bottom) {
        int visible = Math.max(1, (bottom - top) / ENTRY_HEIGHT);
        int maxOffset = Math.max(0, specs.size() - visible);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        if (scrollOffset < 0) scrollOffset = 0;

        int index = scrollOffset;
        int y = top;
        while (y + ENTRY_HEIGHT <= bottom && index < specs.size()) {
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            boolean selected = index == selectedIndex;
            int bg = selected ? 0x80FFFFFF : (hovered ? 0x40FFFFFF : 0x40000000);
            gfx.fill(left, y, left + width, y + ENTRY_HEIGHT, bg);

            RoadsideDecorationSpec spec = specs.get(index);
            String text = spec.id().toString();
            gfx.drawString(this.font, text, left + 4, y + 4, 0xFFFFFF, false);

            y += ENTRY_HEIGHT;
            index++;
        }

        if (specs.isEmpty()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("gui.roadweaver.roadside_browser.empty"),
                    left + width / 2, top + (bottom - top - this.font.lineHeight) / 2, 0xAAAAAA);
        }
    }

    private void renderPreview(GuiGraphics gfx, float partialTick, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (specs.isEmpty() || selectedIndex < 0 || selectedIndex >= specs.size()) {
            return;
        }

        RoadsideDecorationSpec spec = specs.get(selectedIndex);

        int textY = y;
        gfx.drawString(this.font, spec.id().toString(), x, textY, 0xFFFFFF, false);
        textY += this.font.lineHeight + 2;
        gfx.drawString(this.font, spec.templateId().toString(), x, textY, 0xAAAAAA, false);

        int previewTop = textY + this.font.lineHeight + 4;
        int previewHeight = height - (previewTop - y);
        if (previewHeight <= 0) {
            return;
        }

        boolean ok = previewRenderer.render(spec, gfx, x, previewTop, width, previewHeight, partialTick);
        if (!ok) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("gui.roadweaver.roadside_browser.preview_unavailable"),
                    x + width / 2, previewTop + previewHeight / 2, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = Math.min(500, this.width - 40);
        int panelHeight = Math.min(320, this.height - 60);
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = 35;

        int listWidth = (int)(panelWidth * 0.45f);
        int listLeft = panelLeft + 8;
        int listTop = panelTop + 8;
        int listBottom = panelTop + panelHeight - 8;

        if (mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= listTop && mouseY < listBottom) {
            int relativeY = (int) mouseY - listTop;
            int row = relativeY / ENTRY_HEIGHT;
            int index = scrollOffset + row;
            if (index >= 0 && index < specs.size()) {
                selectedIndex = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelWidth = Math.min(500, this.width - 40);
        int panelHeight = Math.min(320, this.height - 60);
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = 35;

        int listWidth = (int)(panelWidth * 0.45f);
        int listLeft = panelLeft + 8;
        int listTop = panelTop + 8;
        int listBottom = panelTop + panelHeight - 8;

        if (mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= listTop && mouseY < listBottom) {
            scrollOffset += delta < 0 ? 1 : -1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
