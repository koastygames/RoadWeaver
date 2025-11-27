package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.interaction.MapInteraction;
import net.shiroha233.roadweaver.client.map.render.GridRenderer;
import net.shiroha233.roadweaver.client.map.render.MapRenderers;
import net.shiroha233.roadweaver.client.map.render.RenderUtils;
import net.shiroha233.roadweaver.client.map.ui.ContextMenu;
import net.shiroha233.roadweaver.client.map.ui.NoteEditScreen;
import net.shiroha233.roadweaver.client.map.ui.SimpleTextInputScreen;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.network.ClientNetBridge;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 道路地图界面 - 重构版
 * 
 * 设计原理：
 * 1. 协调者模式：Screen 只负责协调各个组件，不包含复杂逻辑
 * 2. 单一职责：渲染、输入、状态管理分别委托给专门的类
 * 3. 可测试性：核心逻辑在独立类中，便于单元测试
 */
public class RoadMapScreen extends Screen implements MapInputHandler.Callbacks {
    private static final ResourceLocation MAP_TEXTURE = new ResourceLocation("roadweaver", "textures/gui/map.png");
    
    // 翻译键
    private static final Component BTN_CONFIG = Component.translatable("gui.roadweaver.config_button");
    private static final Component BTN_MANUAL = Component.translatable("gui.roadweaver.map.manual_connect");
    private static final Component MENU_TELEPORT = Component.translatable("gui.roadweaver.map.menu.teleport");
    private static final Component MENU_SET_ALIAS = Component.translatable("gui.roadweaver.map.menu.set_alias");
    private static final Component MENU_EDIT_NOTE = Component.translatable("gui.roadweaver.map.menu.edit_note");
    private static final Component DIALOG_ALIAS_TITLE = Component.translatable("gui.roadweaver.map.dialog.alias_title");

    // 数据
    private MapSnapshot snapshot = MapSnapshot.empty();
    
    // 组件
    private final MapState state = new MapState();
    private final MapView view = new MapView();
    private final MapInputHandler inputHandler;
    private final ContextMenu contextMenu = new ContextMenu();
    
    // 布局
    private int mapX, mapY, mapW, mapH;

    public RoadMapScreen() {
        super(Component.translatable("gui.roadweaver.map.title"));
        this.inputHandler = new MapInputHandler(state, view, this);
    }

    // ========== 生命周期 ==========
    
    @Override
    protected void init() {
        super.init();
        MapSnapshotCache.cancelClear();
        MapSnapshot cached = MapSnapshotCache.peek();
        if (cached != null) {
            this.snapshot = cached;
        }
        computeMapRect();
        inputHandler.updateLayout(mapX, mapY, mapW, mapH, MapTheme.INNER_PADDING);
        
        int contentW = mapW - MapTheme.INNER_PADDING * 2;
        int contentH = mapH - MapTheme.INNER_PADDING * 2;
        view.resetFromSnapshot(snapshot);
        
        Minecraft mc = this.minecraft;
        if (mc != null && mc.player != null) {
            view.calibrateInitialToPlayer(mc, contentW, contentH, MapTheme.GRID_TARGET_PX);
        }
        onRequestView();
    }

    @Override
    public void removed() {
        super.removed();
        MapSnapshotCache.scheduleClear(1000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 渲染 ==========
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        
        // 地图纹理
        g.blit(MAP_TEXTURE, mapX, mapY, mapW, mapH, 0, 0, 
               MapTheme.TEX_WIDTH, MapTheme.TEX_HEIGHT, MapTheme.TEX_WIDTH, MapTheme.TEX_HEIGHT);

        // 标题
        int titleY = mapY - 8;
        g.drawCenteredString(this.font, this.getTitle(), this.width / 2, Math.max(6, titleY), MapTheme.COLOR_TEXT);

        int contentW = mapW - MapTheme.INNER_PADDING * 2;
        int contentH = mapH - MapTheme.INNER_PADDING * 2;
        view.lockAspect(contentW, contentH);

        int left = mapX + MapTheme.INNER_PADDING;
        int top = mapY + MapTheme.INNER_PADDING;
        int right = mapX + mapW - MapTheme.INNER_PADDING;
        int bottom = mapY + mapH - MapTheme.INNER_PADDING;
        
        g.enableScissor(left, top, right, bottom);
        
        // 网格
        MapRenderers.renderGrid(g, this.font, mapX, mapY, mapW, mapH, MapTheme.INNER_PADDING,
                view.getMinX(), view.getMaxX(), view.getMinZ(), view.getMaxZ(), 
                MapTheme.COLOR_GRID, MapTheme.GRID_TARGET_PX, MapTheme.COLOR_TEXT);

        int thickness = computeThickness();
        
        // 连接线（排除已完成的，因为会用道路折线表示）
        List<Records.StructureConnection> connForLines = new ArrayList<>(snapshot.connections());
        boolean hasDetailedRoads = !snapshot.roadPolylines().isEmpty();
        if (hasDetailedRoads) {
            connForLines.removeIf(c -> c.status() == Records.ConnectionStatus.COMPLETED);
        }
        MapRenderers.renderConnections(g, connForLines,
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW),
                v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH),
                thickness,
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING, 
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                left, top, right, bottom);

        // 道路折线
        int lodStep = GridRenderer.computeGridStep(mapX, mapY, mapW, mapH, MapTheme.INNER_PADDING,
                view.getMinX(), view.getMaxX(), view.getMinZ(), view.getMaxZ(), MapTheme.GRID_TARGET_PX);
        MapRenderers.renderRoadPolylines(g, snapshot.roadPolylines(),
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW),
                v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH),
                thickness, MapTheme.COLOR_COMPLETED,
                left, top, right, bottom, lodStep);

        // 结构点
        MapRenderers.renderStructures(g, snapshot.structures(),
                v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW),
                v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH),
                (x, z) -> view.isInViewWorld(x, z),
                computePointSize(), MapTheme.COLOR_STRUCTURE,
                left, top, right, bottom);

        // 手动连接模式的预览
        renderManualModePreview(g, mouseX, mouseY, contentW, contentH, left, top, right, bottom);

        // 悬停高亮
        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverHighlight(g, snapshot, view, mapX, mapY, mapW, mapH, 
                    MapTheme.INNER_PADDING, mouseX, mouseY);
        }

        // 玩家箭头
        renderPlayer(g, contentW, contentH, left, top, right, bottom);
        
        g.disableScissor();

        // 图例
        int legendRight = mapX + mapW - MapTheme.INNER_PADDING;
        int legendStartY = mapY + MapTheme.INNER_PADDING + 8;
        MapRenderers.renderLegend(g, this.font, legendRight, legendStartY, 8,
                MapTheme.COLOR_TEXT, MapTheme.COLOR_STRUCTURE, 
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING, 
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                snapshot.structuresCount(), snapshot.plannedCount(), 
                snapshot.generatingCount(), snapshot.completedCount(), snapshot.failedCount());

        // 工具栏按钮
        renderToolbarButtons(g, mouseX, mouseY);

        // 悬停提示
        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverTooltip(g, this.font, snapshot, view, mapX, mapY, mapW, mapH,
                    MapTheme.INNER_PADDING, mouseX, mouseY);
        }

        // 缩放防抖检查
        if (state.isZoomDebounceReady()) {
            state.clearZoomDebounce();
            onRequestView();
        }

        // 右键菜单
        contextMenu.render(g, this.font, mouseX, mouseY, this.width, this.height);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderManualModePreview(GuiGraphics g, int mouseX, int mouseY,
                                         int contentW, int contentH,
                                         int left, int top, int right, int bottom) {
        if (!state.isManualMode() || !state.hasSelection()) return;
        
        BlockPos selectedA = state.getSelectedA();
        if (!view.isInViewWorld(selectedA.getX(), selectedA.getZ())) return;
        
        int sxA = view.toScreenX(selectedA.getX(), mapX, MapTheme.INNER_PADDING, contentW);
        int syA = view.toScreenY(selectedA.getZ(), mapY, MapTheme.INNER_PADDING, contentH);
        int selSize = computePointSize() * 2 + 4;
        RenderUtils.drawPoint(g, sxA, syA, selSize, MapTheme.COLOR_SELECTED, left, top, right, bottom);

        if (inputHandler.insideMap(mouseX, mouseY)) {
            int sxB, syB;
            BlockPos hover = inputHandler.findNearestStructure(snapshot, mouseX, mouseY);
            if (hover != null && view.isInViewWorld(hover.getX(), hover.getZ())) {
                sxB = view.toScreenX(hover.getX(), mapX, MapTheme.INNER_PADDING, contentW);
                syB = view.toScreenY(hover.getZ(), mapY, MapTheme.INNER_PADDING, contentH);
            } else {
                sxB = mouseX;
                syB = mouseY;
            }
            RenderUtils.drawThickDashedLine(g, sxA, syA, sxB, syB,
                    MapTheme.COLOR_PREVIEW_LINE, computeThickness(),
                    MapTheme.DASH_LENGTH, MapTheme.DASH_GAP,
                    left, top, right, bottom);
        }
    }

    private void renderPlayer(GuiGraphics g, int contentW, int contentH,
                              int left, int top, int right, int bottom) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        
        double wx = this.minecraft.player.getX();
        double wz = this.minecraft.player.getZ();
        int sx = view.toScreenX((int) Math.round(wx), mapX, MapTheme.INNER_PADDING, contentW);
        int sy = view.toScreenY((int) Math.round(wz), mapY, MapTheme.INNER_PADDING, contentH);
        
        if (!inputHandler.insideMap(sx, sy)) return;
        
        float yaw = this.minecraft.player.getYRot();
        MapRenderers.drawPlayerArrow(g, sx, sy, yaw,
                MapTheme.PLAYER_ARROW_TIP_LEN, MapTheme.PLAYER_ARROW_BASE_LEN, 
                MapTheme.PLAYER_ARROW_HALF_WIDTH, MapTheme.COLOR_PLAYER_ARROW,
                left, top, right, bottom,
                view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH));
    }

    private void renderToolbarButtons(GuiGraphics g, int mouseX, int mouseY) {
        // 配置按钮（左上角）
        int[] configBtn = computeConfigBtnBounds();
        renderTextButton(g, BTN_CONFIG, configBtn, mouseX, mouseY);

        // 手动连接按钮（左下角）
        int[] manualBtn = computeManualBtnBounds();
        Component manualLabel = Component.empty().append(BTN_MANUAL).append(": ")
                .append(state.isManualMode() 
                        ? Component.translatable("gui.roadweaver.common.on") 
                        : Component.translatable("gui.roadweaver.common.off"));
        renderTextButton(g, manualLabel, manualBtn, mouseX, mouseY);
    }

    private void renderTextButton(GuiGraphics g, Component label, int[] bounds, int mouseX, int mouseY) {
        int x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        int ty = y + (h - this.font.lineHeight) / 2;
        g.drawString(this.font, label, x + 3, ty, MapTheme.COLOR_TEXT, false);
        
        if (insideRect(mouseX, mouseY, x, y, w, h)) {
            int textW = this.font.width(label);
            int uy = ty + this.font.lineHeight + 1;
            int underline = (MapTheme.COLOR_TEXT & 0x00FFFFFF) | 0x60000000;
            g.fill(x + 2, uy, x + 2 + textW + 2, uy + 1, underline);
        }
    }

    // ========== 输入处理 ==========
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return inputHandler.mouseScrolled(mouseX, mouseY, delta) 
               || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键菜单点击
        if (contextMenu.isOpen()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        // 工具栏按钮
        if (button == 0) {
            int[] configBtn = computeConfigBtnBounds();
            if (insideRect(mouseX, mouseY, configBtn)) {
                onOpenConfig();
                return true;
            }
            int[] manualBtn = computeManualBtnBounds();
            if (insideRect(mouseX, mouseY, manualBtn)) {
                state.toggleManualMode();
                return true;
            }
        }
        
        // 右键打开菜单
        if (button == 1 && inputHandler.insideMap(mouseX, mouseY)) {
            BlockPos target = inputHandler.findNearestStructure(snapshot, mouseX, mouseY);
            if (target != null) {
                openContextMenuFor(target, (int) mouseX, (int) mouseY);
                return true;
            }
        }
        
        // 委托给输入处理器
        return inputHandler.mouseClicked(mouseX, mouseY, button, snapshot,
                computeConfigBtnBounds()[0], computeConfigBtnBounds()[1], 
                computeConfigBtnBounds()[2], computeConfigBtnBounds()[3],
                computeManualBtnBounds()[0], computeManualBtnBounds()[1],
                computeManualBtnBounds()[2], computeManualBtnBounds()[3])
               || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY)
               || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.mouseReleased(mouseX, mouseY, button)
               || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputHandler.keyPressed(keyCode, scanCode, modifiers, this.minecraft)
               || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ========== 回调实现 ==========
    
    @Override
    public void onCloseScreen() {
        this.onClose();
    }

    @Override
    public void onOpenConfig() {
        if (this.minecraft == null) return;
        try {
            Screen next = net.shiroha233.roadweaver.client.ConfigScreenFactory.createConfigScreen(this);
            if (next != null) {
                this.minecraft.setScreen(next);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRequestView() {
        int minX = (int) Math.floor(Math.min(view.getMinX(), view.getMaxX())) - 32;
        int maxX = (int) Math.ceil(Math.max(view.getMinX(), view.getMaxX())) + 32;
        int minZ = (int) Math.floor(Math.min(view.getMinZ(), view.getMaxZ())) - 32;
        int maxZ = (int) Math.ceil(Math.max(view.getMinZ(), view.getMaxZ())) + 32;

        Minecraft mc = this.minecraft;
        if (mc == null) return;
        
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            // 单人模式：本地构造快照
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                int cx = 0, cz = 0;
                if (mc.player != null) {
                    cx = (int) Math.round(mc.player.getX());
                    cz = (int) Math.round(mc.player.getZ());
                }
                int radiusBlocks = getRadiusBlocks();
                final int fcx = cx, fcz = cz;
                final int fMinX = minX, fMaxX = maxX, fMinZ = minZ, fMaxZ = maxZ;
                final int currentSeq = state.incrementAndGetRequestSeq();
                
                CompletableFuture
                    .supplyAsync(() -> MapDataCollector.build(level, fMinX, fMinZ, fMaxX, fMaxZ, fcx, fcz, radiusBlocks), 
                                 ComputeService.executor())
                    .thenAccept(snap -> mc.execute(() -> {
                        if (state.getCurrentRequestSeq() == currentSeq) {
                            setSnapshot(snap);
                        }
                    }));
            }
        } else {
            // 多人模式：发送网络请求
            state.incrementAndGetRequestSeq();
            ClientNetBridge.requestSnapshot(minX, minZ, maxX, maxZ);
        }
    }

    @Override
    public void onTeleportTo(BlockPos pos) {
        ClientNetBridge.requestTeleport(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void onManualConnect(BlockPos a, BlockPos b) {
        ClientNetBridge.requestManualConnect(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    @Override
    public void onCenterToPlayer() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        int contentW = mapW - MapTheme.INNER_PADDING * 2;
        int contentH = mapH - MapTheme.INNER_PADDING * 2;
        view.calibrateInitialToPlayer(this.minecraft, contentW, contentH, MapTheme.GRID_TARGET_PX);
        onRequestView();
    }

    @Override
    public void onCenterToSpawn() {
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        
        BlockPos spawn = null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                spawn = level.getSharedSpawnPos();
            }
        }
        if (spawn == null && mc.level != null) {
            spawn = mc.level.getSharedSpawnPos();
        }
        if (spawn == null) return;
        
        int contentW = mapW - MapTheme.INNER_PADDING * 2;
        int contentH = mapH - MapTheme.INNER_PADDING * 2;
        view.centerOn(spawn.getX(), spawn.getZ(), contentW, contentH);
        onRequestView();
    }

    // ========== 公共方法 ==========
    
    public void setSnapshot(MapSnapshot snapshot) {
        if (snapshot != null) {
            this.snapshot = snapshot;
            MapSnapshotCache.put(snapshot);
        }
    }

    // ========== 辅助方法 ==========
    
    private void computeMapRect() {
        int availW = this.width - MapTheme.OUTER_PADDING * 2;
        int availH = this.height - MapTheme.OUTER_PADDING * 2;
        float ratio = (float) MapTheme.TEX_WIDTH / MapTheme.TEX_HEIGHT;
        int w = availW;
        int h = Math.round(w / ratio);
        if (h > availH) {
            h = availH;
            w = Math.round(h * ratio);
        }
        mapW = w;
        mapH = h;
        mapX = (this.width - w) / 2;
        mapY = (this.height - h) / 2;
    }

    private int computeThickness() {
        int contentW = mapW - MapTheme.INNER_PADDING * 2;
        int contentH = mapH - MapTheme.INNER_PADDING * 2;
        double ppb = Math.min(view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH));
        int t = (int) Math.round(ppb);
        return Math.max(MapTheme.MIN_THICKNESS, Math.min(t, MapTheme.MAX_THICKNESS));
    }

    private int computePointSize() {
        return MapTheme.BASE_POINT_SIZE + computeThickness();
    }

    private int getRadiusBlocks() {
        try {
            var cfg = net.shiroha233.roadweaver.config.ConfigService.get();
            int radiusChunks = cfg.dynamicPlanEnabled() ? cfg.dynamicPlanRadiusChunks() : cfg.initialPlanRadiusChunks();
            return Math.max(1, radiusChunks) * 16;
        } catch (Throwable t) {
            return 256 * 16;
        }
    }

    private void openContextMenuFor(BlockPos target, int x, int y) {
        contextMenu.clearItems();
        contextMenu.addItem(MENU_TELEPORT, () -> onTeleportTo(target));
        contextMenu.addSeparator();
        contextMenu.addItem(MENU_SET_ALIAS, () -> openAliasDialog(target));
        contextMenu.addItem(MENU_EDIT_NOTE, () -> openNoteEditor(target));
        contextMenu.open(x, y);
    }

    /** 打开别名设置对话框 */
    private void openAliasDialog(BlockPos target) {
        if (this.minecraft == null) return;
        String currentAlias = ClientMapNotes.getAlias(target);
        this.minecraft.setScreen(new SimpleTextInputScreen(
                DIALOG_ALIAS_TITLE,
                currentAlias != null ? currentAlias : "",
                alias -> ClientMapNotes.setAlias(target, alias),
                this  // 返回地图界面
        ));
    }

    /** 打开笔记编辑器（书与笔风格） */
    private void openNoteEditor(BlockPos target) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new NoteEditScreen(target, this));
    }

    private int[] computeConfigBtnBounds() {
        int x = mapX + MapTheme.INNER_PADDING + 4;
        int y = mapY + MapTheme.INNER_PADDING + 4;
        int w = this.font.width(BTN_CONFIG) + 6;
        int h = this.font.lineHeight + 4;
        return new int[]{x, y, w, h};
    }

    private int[] computeManualBtnBounds() {
        Component lbl = Component.empty().append(BTN_MANUAL).append(": ")
                .append(Component.translatable("gui.roadweaver.common.on"));
        int w = this.font.width(lbl) + 6;
        int h = this.font.lineHeight + 4;
        int x = mapX + MapTheme.INNER_PADDING + 4;
        int y = mapY + mapH - MapTheme.INNER_PADDING - 4 - h;
        return new int[]{x, y, w, h};
    }

    private boolean insideRect(double x, double y, int[] bounds) {
        return x >= bounds[0] && x <= bounds[0] + bounds[2] 
            && y >= bounds[1] && y <= bounds[1] + bounds[3];
    }

    private boolean insideRect(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
}
