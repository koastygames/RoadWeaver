package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotStore;
import net.shiroha233.roadweaver.client.map.interaction.MapInteraction;
import net.shiroha233.roadweaver.client.map.render.MapHudRenderer;
import net.shiroha233.roadweaver.client.map.render.MapOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapRenderers;
import net.shiroha233.roadweaver.client.map.render.RenderUtils;
import net.shiroha233.roadweaver.client.map.tile.SingleplayerTerrainTileManager;
import net.shiroha233.roadweaver.client.map.ui.ContextMenu;
import net.shiroha233.roadweaver.client.map.ui.NoteEditScreen;
import net.shiroha233.roadweaver.client.map.ui.SimpleTextInputScreen;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.ClientNetBridge;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 道路地图界面
 */
public class RoadMapScreen extends Screen implements MapInputHandler.Callbacks {
    
    // 翻译键
    private static final Component MENU_TELEPORT = Component.translatable("gui.roadweaver.map.menu.teleport");
    private static final Component MENU_SET_ALIAS = Component.translatable("gui.roadweaver.map.menu.set_alias");
    private static final Component MENU_EDIT_NOTE = Component.translatable("gui.roadweaver.map.menu.edit_note");
    private static final Component DIALOG_ALIAS_TITLE = Component.translatable("gui.roadweaver.map.dialog.alias_title");

    // 数据
    private MapSnapshot snapshot = MapSnapshot.empty();
    private ResourceLocation currentDimensionId;
    private MapSnapshotStore snapshotStore = new MapSnapshotStore();
    private MapLoadSession loadSession;
    
    // 组件
    private final MapState state = new MapState();
    private final MapView view = new MapView();
    private final MapInputHandler inputHandler;
    private final ContextMenu contextMenu = new ContextMenu();
    private final SingleplayerTerrainTileManager terrainTiles = new SingleplayerTerrainTileManager();
    
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
        if (!hasMapOpenPermission()) {
            denyMapAccessAndClose();
            return;
        }
        MapSnapshotCache.cancelClear();
        Minecraft mc = this.minecraft;
        if (mc != null && mc.level != null) {
            currentDimensionId = mc.level.dimension().location();
            snapshotStore = MapSnapshotCache.store(currentDimensionId);
            this.snapshot = snapshotStore.snapshot();
        }
        computeMapRect();
        inputHandler.updateLayout(mapX, mapY, mapW, mapH, 0);
        
        int contentW = mapW;
        int contentH = mapH;
        view.resetFromSnapshot(snapshot);
        
        if (mc != null && mc.player != null) {
            view.calibrateInitialToPlayer(mc, contentW, contentH, MapTheme.GRID_TARGET_PX);
        }
        onRequestView();
    }

    @Override
    public void removed() {
        super.removed();
        terrainTiles.clear();
        loadSession = null;
        MapSnapshotCache.putStoreSnapshot(currentDimensionId, snapshotStore);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 渲染 ==========
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int contentW = mapW;
        int contentH = mapH;
        view.lockAspect(contentW, contentH);

        int left = 0;
        int top = 0;
        int right = this.width;
        int bottom = this.height;

        Minecraft mc = this.minecraft;
        MapViewportController.requestTerrainTiles(mc, terrainTiles, view, contentW, contentH);

        g.fill(0, 0, this.width, this.height, MapTheme.COLOR_BACKGROUND);
        g.enableScissor(left, top, right, bottom);

        // 渲染低精度地形瓦片
        terrainTiles.render(g, this.minecraft, view, left, top, contentW, contentH);
        MapRenderers.renderGrid(g, this.font, 0, 0, mapW, mapH, 0,
                view.getMinX(), view.getMaxX(), view.getMinZ(), view.getMaxZ(),
                MapTheme.COLOR_GRID, MapTheme.GRID_TARGET_PX, MapTheme.COLOR_TEXT);

        int thickness = computeThickness();
        MapRenderers.renderRoadPolylines(g, snapshot.roadPolylines(),
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness),
                MapTheme.COLOR_COMPLETED,
                left, top, right, bottom,
                Math.max(1, Math.round(16 / Math.max(0.1f, (float) view.pxPerBlockX(contentW)))));
        MapRenderers.renderStructures(g, this.font,
                snapshot.structures(),
                snapshot::structureName,
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                (x, z) -> view.isInViewWorld(x, z),
                Math.max(MapTheme.STRUCTURE_MARKER_SIZE, computePointSize()),
                left, top, right, bottom);
        List<StructureConnection> connForLines = new ArrayList<>(snapshot.connections());
        boolean hasDetailedRoadPolylines = !snapshot.roadPolylines().isEmpty();
        connForLines.removeIf(c -> hasDetailedRoadPolylines && c.status() == ConnectionStatus.COMPLETED);
        MapRenderers.renderConnections(g, connForLines,
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness - 1),
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING,
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                left, top, right, bottom);

        MapOverlayRenderer.renderManualModePreview(g, snapshot, view, inputHandler,
                state.hasSelection() ? state.getSelectedA() : null,
                mouseX, mouseY, contentW, contentH,
                left, top, right, bottom,
                computePointSize() * 2 + 4,
                computeThickness());

        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverHighlight(g, snapshot, view, 0, 0, mapW, mapH,
                    0, mouseX, mouseY);
        }

        MapOverlayRenderer.renderPlayer(g, this.minecraft, inputHandler, view,
                contentW, contentH, left, top, right, bottom);

        g.disableScissor();

        int legendRight = mapW - 8;
        int legendStartY = 8;
        MapHudRenderer.renderLegendWithBackground(g, this.font, legendRight, legendStartY, snapshot);

        MapHudRenderer.ToolbarLayout toolbar = MapHudRenderer.buildToolbar(this.font, mapH, state.isManualMode());
        MapHudRenderer.renderToolbarButtons(g, this.font, toolbar, mouseX, mouseY);
        MapHudRenderer.renderLoadingStatus(g, this.font, toolbar, loadSession);

        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverTooltip(g, this.font, snapshot, view, 0, 0, mapW, mapH,
                    0, mouseX, mouseY);
        }

        if (state.isZoomDebounceReady()) {
            state.clearZoomDebounce();
            onRequestView();
        }

        contextMenu.render(g, this.font, mouseX, mouseY, this.width, this.height);
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
        
        MapHudRenderer.ToolbarLayout toolbar = MapHudRenderer.buildToolbar(this.font, mapH, state.isManualMode());

        // 工具栏按钮
        if (button == 0) {
            if (toolbar.configButton().contains(mouseX, mouseY)) {
                onOpenConfig();
                return true;
            }
            if (toolbar.manualButton().contains(mouseX, mouseY)) {
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
                toolbar.configButton().x(), toolbar.configButton().y(),
                toolbar.configButton().width(), toolbar.configButton().height(),
                toolbar.manualButton().x(), toolbar.manualButton().y(),
                toolbar.manualButton().width(), toolbar.manualButton().height())
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
        var p = this.minecraft.player;
        if (p != null && this.minecraft.getSingleplayerServer() == null) {
            // 多人游戏：配置修改应限制为 OP（服务端权限等级 2+）
            if (!p.hasPermissions(2)) {
                p.displayClientMessage(Component.translatable("gui.roadweaver.map.config.denied"), true);
                return;
            }
        }
        try {
            Screen next = net.shiroha233.roadweaver.client.ConfigScreenFactory.createConfigScreen(this);
            if (next != null) {
                this.minecraft.setScreen(next);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRequestView() {
        if (!hasMapOpenPermission()) {
            denyMapAccessAndClose();
            return;
        }

        Minecraft mc = this.minecraft;
        if (mc == null) return;

        MapViewportController.RequestRect requestRect = MapViewportController.currentRequestRect(view);
        currentDimensionId = MapViewportController.syncDimensionAndRestoreCache(mc, currentDimensionId, cached -> {
            snapshotStore = MapSnapshotCache.store(mc.level.dimension().location());
            this.snapshot = snapshotStore.snapshot();
        });

        ResourceLocation did = (mc.level != null) ? mc.level.dimension().location() : currentDimensionId;
        if (did == null) {
            did = new ResourceLocation("minecraft", "overworld");
        }
        if (currentDimensionId == null || !did.equals(currentDimensionId)) {
            snapshotStore = MapSnapshotCache.store(did);
            snapshot = snapshotStore.snapshot();
        }
        currentDimensionId = did;

        List<MapViewportController.RequestRect> structureRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.STRUCTURES));
        List<MapViewportController.RequestRect> roadRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.ROADS));
        List<MapViewportController.RequestRect> connectionRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.CONNECTIONS));
        List<MapLoadSession.ResponseRequest> requests;
        if (!structureRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(structureRects, List.of(), List.of());
        } else if (!roadRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), roadRects, List.of());
        } else if (!connectionRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), List.of(), connectionRects);
        } else {
            loadSession = null;
            snapshot = snapshotStore.snapshot();
            MapSnapshotCache.put(did, snapshot);
            return;
        }

        int requestSeq = state.incrementAndGetRequestSeq();
        loadSession = new MapLoadSession(requestSeq, did, requests);
        dispatchRemoteSnapshotLoad(requestSeq, did, requests);
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
        int contentW = mapW;
        int contentH = mapH;
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
        
        int contentW = mapW;
        int contentH = mapH;
        view.centerOn(spawn.getX(), spawn.getZ(), contentW, contentH);
        onRequestView();
    }

    public void acceptSnapshot(int requestSeq, ResourceLocation dimensionId, MapSnapshot snapshot) {
        acceptSnapshotPart(requestSeq, dimensionId, MapLoadPhase.CONNECTIONS, 0, snapshot);
    }

    public void acceptPatch(ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (dimensionId == null || patch == null) return;
        if (currentDimensionId != null && !dimensionId.equals(currentDimensionId)) return;
        snapshotStore.apply(patch);
        this.snapshot = snapshotStore.snapshot();
        MapSnapshotCache.put(dimensionId, this.snapshot);
    }

    public void acceptSnapshotPart(int requestSeq,
                                   ResourceLocation dimensionId,
                                   MapLoadPhase phase,
                                   int responseIndex,
                                   MapSnapshot snapshot) {
        if (dimensionId == null || snapshot == null) return;
        if (requestSeq != state.getCurrentRequestSeq()) return;
        if (currentDimensionId != null && !dimensionId.equals(currentDimensionId)) return;
        if (loadSession == null || !loadSession.accepts(requestSeq, dimensionId)) return;

        MapLoadSession.ResponseRequest request = loadSession.requestAt(responseIndex);
        MapViewportController.RequestRect loadedRect = request != null ? request.rect() : null;
        MapLoadPhase loadedPhase = request != null ? request.phase() : phase;

        snapshotStore.merge(loadedPhase, loadedRect, snapshot);
        loadSession.markReceived(loadedPhase, responseIndex, snapshot);
        this.snapshot = snapshotStore.snapshot();
        MapSnapshotCache.put(dimensionId, this.snapshot);
        if (loadSession.isComplete()) {
            loadSession = null;
            onRequestView();
        }
    }

    private void scheduleSingleplayerSnapshotLoad(Minecraft mc,
                                                  ServerLevel level,
                                                  int requestSeq,
                                                  List<MapLoadSession.ResponseRequest> requests) {
        ResourceLocation dimensionId = level.dimension().location();
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            MapViewportController.RequestRect rect = request.rect();
            scheduleSingleplayerPhase(mc, requestSeq, dimensionId, request.phase(), responseIndex,
                    () -> buildSnapshotForPhase(level, request.phase(), rect));
        }
    }

    private MapSnapshot buildSnapshotForPhase(ServerLevel level,
                                             MapLoadPhase phase,
                                             MapViewportController.RequestRect rect) {
        return switch (phase) {
            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        };
    }

    private int scheduleSingleplayerPhase(Minecraft mc,
                                          int requestSeq,
                                          ResourceLocation dimensionId,
                                          MapLoadPhase phase,
                                          int responseIndex,
                                          java.util.function.Supplier<MapSnapshot> supplier) {
        final int currentIndex = responseIndex;
        CompletableFuture
                .supplyAsync(supplier, ComputeService.mapExecutor())
                .thenAccept(snap -> mc.execute(() -> acceptSnapshotPart(requestSeq, dimensionId, phase, currentIndex, snap)));
        return responseIndex + 1;
    }

    private void dispatchRemoteSnapshotLoad(int requestSeq,
                                            ResourceLocation dimensionId,
                                            List<MapLoadSession.ResponseRequest> requests) {
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            MapViewportController.RequestRect rect = request.rect();
            ClientNetBridge.requestSnapshot(requestSeq, dimensionId, request.phase(), responseIndex,
                    rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        }
    }

    // ========== 辅助方法 ==========
    
    private void computeMapRect() {
        mapX = 0;
        mapY = 0;
        mapW = this.width;
        mapH = this.height;
    }

    private int computeThickness() {
        int contentW = mapW;
        int contentH = mapH;
        double ppb = Math.min(view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH));
        int t = (int) Math.round(ppb);
        return Math.max(MapTheme.MIN_THICKNESS, Math.min(t, MapTheme.MAX_THICKNESS));
    }

    private int computePointSize() {
        return MapTheme.BASE_POINT_SIZE + computeThickness();
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

    private boolean hasMapOpenPermission() {
        Minecraft mc = this.minecraft;
        if (mc == null) {
            return true;
        }
        if (!ClientMapAccessGuard.isAllowedOrUnknown()) {
            return false;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return true;
        }
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        return serverPlayer == null || MapAccessService.canOpenMap(serverPlayer);
    }

    private void denyMapAccessAndClose() {
        ClientMapAccessGuard.notifyDenied(this.minecraft);
        MapSnapshotCache.clearNow();
        this.onClose();
    }
}
