package net.shiroha233.roadweaver.client.map

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache
import net.shiroha233.roadweaver.client.map.interaction.MapInteraction
import net.shiroha233.roadweaver.client.map.render.GridRenderer
import net.shiroha233.roadweaver.client.map.render.MapRenderers
import net.shiroha233.roadweaver.client.map.render.RenderUtils
import net.shiroha233.roadweaver.client.map.ui.ContextMenu
import net.shiroha233.roadweaver.client.map.ui.NoteEditScreen
import net.shiroha233.roadweaver.client.map.ui.SimpleTextInputScreen
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.network.ClientNetBridge
import net.shiroha233.roadweaver.util.ComputeService
import java.util.ArrayList
import java.util.concurrent.CompletableFuture

/**
 * 道路地图界面 - 重构版
 *
 * 设计原理：
 * 1. 协调者模式：Screen 只负责协调各个组件，不包含复杂逻辑
 * 2. 单一职责：渲染、输入、状态管理分别委托给专门的类
 * 3. 可测试性：核心逻辑在独立类中，便于单元测试
 */
class RoadMapScreen : Screen(Component.translatable("gui.roadweaver.map.title")), MapInputHandler.Callbacks {

    companion object {
        private val MAP_TEXTURE: ResourceLocation = ResourceLocation("roadweaver", "textures/gui/map.png")

        // 翻译键
        private val BTN_CONFIG: Component = Component.translatable("gui.roadweaver.config_button")
        private val BTN_MANUAL: Component = Component.translatable("gui.roadweaver.map.manual_connect")
        private val MENU_TELEPORT: Component = Component.translatable("gui.roadweaver.map.menu.teleport")
        private val MENU_SET_ALIAS: Component = Component.translatable("gui.roadweaver.map.menu.set_alias")
        private val MENU_EDIT_NOTE: Component = Component.translatable("gui.roadweaver.map.menu.edit_note")
        private val DIALOG_ALIAS_TITLE: Component = Component.translatable("gui.roadweaver.map.dialog.alias_title")
    }

    // 数据
    private var snapshot: MapSnapshot = MapSnapshot.empty()

    // 组件
    private val state = MapState()
    private val view = MapView()
    private val inputHandler: MapInputHandler = MapInputHandler(state, view, this)
    private val contextMenu = ContextMenu()

    // 布局
    private var mapX: Int = 0
    private var mapY: Int = 0
    private var mapW: Int = 0
    private var mapH: Int = 0

    // ========== 生命周期 ==========

    override fun init() {
        super.init()
        MapSnapshotCache.cancelClear()
        val cached = MapSnapshotCache.peek()
        if (cached !== null) {
            snapshot = cached
        }
        computeMapRect()
        inputHandler.updateLayout(mapX, mapY, mapW, mapH, MapTheme.INNER_PADDING)

        val contentW = mapW - MapTheme.INNER_PADDING * 2
        val contentH = mapH - MapTheme.INNER_PADDING * 2
        view.resetFromSnapshot(snapshot)

        val mc = this.minecraft
        if (mc !== null && mc.player !== null) {
            view.calibrateInitialToPlayer(mc, contentW, contentH, MapTheme.GRID_TARGET_PX)
        }
        onRequestView()
    }

    override fun removed() {
        super.removed()
        MapSnapshotCache.scheduleClear(1000)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun renderBackground(g: GuiGraphics) {
        // 完全禁用默认背景模糊，只绘制半透明黑色
        g.fill(0, 0, this.width, this.height, 0x90000000.toInt())
    }

    // ========== 渲染 ==========

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 调用自定义背景渲染（无模糊）
        this.renderBackground(g)

        // 地图纹理
        g.blit(
            MAP_TEXTURE,
            mapX,
            mapY,
            mapW,
            mapH,
            0f,
            0f,
            MapTheme.TEX_WIDTH,
            MapTheme.TEX_HEIGHT,
            MapTheme.TEX_WIDTH,
            MapTheme.TEX_HEIGHT
        )

        // 标题
        val titleY = mapY - 8
        g.drawCenteredString(this.font, this.title, this.width / 2, kotlin.math.max(6, titleY), MapTheme.COLOR_TEXT)

        val contentW = mapW - MapTheme.INNER_PADDING * 2
        val contentH = mapH - MapTheme.INNER_PADDING * 2
        view.lockAspect(contentW, contentH)

        val left = mapX + MapTheme.INNER_PADDING
        val top = mapY + MapTheme.INNER_PADDING
        val right = mapX + mapW - MapTheme.INNER_PADDING
        val bottom = mapY + mapH - MapTheme.INNER_PADDING

        g.enableScissor(left, top, right, bottom)

        // 网格
        MapRenderers.renderGrid(
            g,
            this.font,
            mapX,
            mapY,
            mapW,
            mapH,
            MapTheme.INNER_PADDING,
            view.getMinX(),
            view.getMaxX(),
            view.getMinZ(),
            view.getMaxZ(),
            MapTheme.COLOR_GRID,
            MapTheme.GRID_TARGET_PX,
            MapTheme.COLOR_TEXT
        )

        val thickness = computeThickness()

        // 连接线（排除已完成的，因为会用道路折线表示）
        val connForLines: MutableList<Records.StructureConnection> = ArrayList(snapshot.connections())
        val hasDetailedRoads = snapshot.roadPolylines().isNotEmpty()
        if (hasDetailedRoads) {
            connForLines.removeIf { c -> c.status == Records.ConnectionStatus.COMPLETED }
        }

        MapRenderers.renderConnections(
            g,
            connForLines,
            { x1, z1, x2, z2 -> view.segmentInViewWorld(x1, z1, x2, z2) },
            java.util.function.IntUnaryOperator { v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW) },
            java.util.function.IntUnaryOperator { v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH) },
            thickness,
            MapTheme.COLOR_PLANNED,
            MapTheme.COLOR_GENERATING,
            MapTheme.COLOR_COMPLETED,
            MapTheme.COLOR_FAILED,
            left,
            top,
            right,
            bottom
        )

        // 道路折线
        val lodStep = GridRenderer.computeGridStep(
            mapX,
            mapY,
            mapW,
            mapH,
            MapTheme.INNER_PADDING,
            view.getMinX(),
            view.getMaxX(),
            view.getMinZ(),
            view.getMaxZ(),
            MapTheme.GRID_TARGET_PX
        )

        MapRenderers.renderRoadPolylines(
            g,
            snapshot.roadPolylines(),
            { x1, z1, x2, z2 -> view.segmentInViewWorld(x1, z1, x2, z2) },
            java.util.function.IntUnaryOperator { v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW) },
            java.util.function.IntUnaryOperator { v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH) },
            thickness,
            MapTheme.COLOR_COMPLETED,
            left,
            top,
            right,
            bottom,
            lodStep
        )

        // 结构点
        MapRenderers.renderStructures(
            g,
            snapshot.structures(),
            java.util.function.IntUnaryOperator { v -> view.toScreenX(v, mapX, MapTheme.INNER_PADDING, contentW) },
            java.util.function.IntUnaryOperator { v -> view.toScreenY(v, mapY, MapTheme.INNER_PADDING, contentH) },
            java.util.function.BiPredicate { x: Int, z: Int -> view.isInViewWorld(x, z) },
            computePointSize(),
            MapTheme.COLOR_STRUCTURE,
            left,
            top,
            right,
            bottom
        )

        // 手动连接模式的预览
        renderManualModePreview(g, mouseX, mouseY, contentW, contentH, left, top, right, bottom)

        // 悬停高亮
        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverHighlight(
                g,
                snapshot,
                view,
                mapX,
                mapY,
                mapW,
                mapH,
                MapTheme.INNER_PADDING,
                mouseX.toDouble(),
                mouseY.toDouble()
            )
        }

        // 玩家箭头
        renderPlayer(g, contentW, contentH, left, top, right, bottom)

        g.disableScissor()

        // 图例
        val legendRight = mapX + mapW - MapTheme.INNER_PADDING
        val legendStartY = mapY + MapTheme.INNER_PADDING + 8
        MapRenderers.renderLegend(
            g,
            this.font,
            legendRight,
            legendStartY,
            8,
            MapTheme.COLOR_TEXT,
            MapTheme.COLOR_STRUCTURE,
            MapTheme.COLOR_PLANNED,
            MapTheme.COLOR_GENERATING,
            MapTheme.COLOR_COMPLETED,
            MapTheme.COLOR_FAILED,
            snapshot.structuresCount(),
            snapshot.plannedCount(),
            snapshot.generatingCount(),
            snapshot.completedCount(),
            snapshot.failedCount()
        )

        // 工具栏按钮
        renderToolbarButtons(g, mouseX, mouseY)

        // 悬停提示
        if (!contextMenu.isOpen()) {
            MapInteraction.renderHoverTooltip(
                g,
                this.font,
                snapshot,
                view,
                mapX,
                mapY,
                mapW,
                mapH,
                MapTheme.INNER_PADDING,
                mouseX.toDouble(),
                mouseY.toDouble()
            )
        }

        // 缩放防抖检查
        if (state.isZoomDebounceReady()) {
            state.clearZoomDebounce()
            onRequestView()
        }

        // 右键菜单
        contextMenu.render(g, this.font, mouseX, mouseY, this.width, this.height)
    }

    private fun renderManualModePreview(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        contentW: Int,
        contentH: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (!state.isManualMode() || !state.hasSelection()) return

        val selectedA = state.getSelectedA() ?: return
        if (!view.isInViewWorld(selectedA.x, selectedA.z)) return

        val sxA = view.toScreenX(selectedA.x, mapX, MapTheme.INNER_PADDING, contentW)
        val syA = view.toScreenY(selectedA.z, mapY, MapTheme.INNER_PADDING, contentH)
        val selSize = computePointSize() * 2 + 4
        RenderUtils.drawPoint(g, sxA, syA, selSize, MapTheme.COLOR_SELECTED, left, top, right, bottom)

        if (inputHandler.insideMap(mouseX.toDouble(), mouseY.toDouble())) {
            val hover = inputHandler.findNearestStructure(snapshot, mouseX.toDouble(), mouseY.toDouble())
            val (sxB, syB) = if (hover !== null && view.isInViewWorld(hover.x, hover.z)) {
                view.toScreenX(hover.x, mapX, MapTheme.INNER_PADDING, contentW) to
                    view.toScreenY(hover.z, mapY, MapTheme.INNER_PADDING, contentH)
            } else {
                mouseX to mouseY
            }

            RenderUtils.drawThickDashedLine(
                g,
                sxA,
                syA,
                sxB,
                syB,
                MapTheme.COLOR_PREVIEW_LINE,
                computeThickness(),
                MapTheme.DASH_LENGTH,
                MapTheme.DASH_GAP,
                left,
                top,
                right,
                bottom
            )
        }
    }

    private fun renderPlayer(g: GuiGraphics, contentW: Int, contentH: Int, left: Int, top: Int, right: Int, bottom: Int) {
        val mc = this.minecraft ?: return
        val player = mc.player ?: return

        val wx = player.x
        val wz = player.z
        val sx = view.toScreenX(kotlin.math.round(wx).toInt(), mapX, MapTheme.INNER_PADDING, contentW)
        val sy = view.toScreenY(kotlin.math.round(wz).toInt(), mapY, MapTheme.INNER_PADDING, contentH)

        if (!inputHandler.insideMap(sx.toDouble(), sy.toDouble())) return

        val yaw = player.yRot
        MapRenderers.drawPlayerArrow(
            g,
            sx,
            sy,
            yaw,
            MapTheme.PLAYER_ARROW_TIP_LEN,
            MapTheme.PLAYER_ARROW_BASE_LEN,
            MapTheme.PLAYER_ARROW_HALF_WIDTH,
            MapTheme.COLOR_PLAYER_ARROW,
            left,
            top,
            right,
            bottom,
            view.pxPerBlockX(contentW),
            view.pxPerBlockZ(contentH)
        )
    }

    private fun renderToolbarButtons(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        // 配置按钮（左上角）
        val configBtn = computeConfigBtnBounds()
        renderTextButton(g, BTN_CONFIG, configBtn, mouseX, mouseY)

        // 手动连接按钮（左下角）
        val manualBtn = computeManualBtnBounds()
        val manualLabel = Component.empty().append(BTN_MANUAL).append(": ")
            .append(if (state.isManualMode()) Component.translatable("gui.roadweaver.common.on") else Component.translatable("gui.roadweaver.common.off"))
        renderTextButton(g, manualLabel, manualBtn, mouseX, mouseY)
    }

    private fun renderTextButton(g: GuiGraphics, label: Component, bounds: IntArray, mouseX: Int, mouseY: Int) {
        val x = bounds[0]
        val y = bounds[1]
        val w = bounds[2]
        val h = bounds[3]
        val ty = y + (h - this.font.lineHeight) / 2
        g.drawString(this.font, label, x + 3, ty, MapTheme.COLOR_TEXT, false)

        if (insideRect(mouseX.toDouble(), mouseY.toDouble(), x, y, w, h)) {
            val textW = this.font.width(label)
            val uy = ty + this.font.lineHeight + 1
            val underline = (MapTheme.COLOR_TEXT and 0x00FFFFFF) or 0x60000000
            g.fill(x + 2, uy, x + 2 + textW + 2, uy + 1, underline)
        }
    }

    // ========== 输入处理 ==========

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        return inputHandler.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 右键菜单点击
        if (contextMenu.isOpen()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }

        // 工具栏按钮
        if (button == 0) {
            val configBtn = computeConfigBtnBounds()
            if (insideRect(mouseX, mouseY, configBtn)) {
                onOpenConfig()
                return true
            }
            val manualBtn = computeManualBtnBounds()
            if (insideRect(mouseX, mouseY, manualBtn)) {
                state.toggleManualMode()
                return true
            }
        }

        // 右键打开菜单
        if (button == 1 && inputHandler.insideMap(mouseX, mouseY)) {
            val target = inputHandler.findNearestStructure(snapshot, mouseX, mouseY)
            if (target !== null) {
                openContextMenuFor(target, mouseX.toInt(), mouseY.toInt())
                return true
            }
        }

        // 委托给输入处理器
        val cfg = computeConfigBtnBounds()
        val man = computeManualBtnBounds()
        return inputHandler.mouseClicked(
            mouseX,
            mouseY,
            button,
            snapshot,
            cfg[0],
            cfg[1],
            cfg[2],
            cfg[3],
            man[0],
            man[1],
            man[2],
            man[3]
        ) || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return inputHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return inputHandler.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return inputHandler.keyPressed(keyCode, scanCode, modifiers, this.minecraft) || super.keyPressed(keyCode, scanCode, modifiers)
    }

    // ========== 回调实现 ==========

    override fun onCloseScreen() {
        this.onClose()
    }

    override fun onOpenConfig() {
        val mc = this.minecraft ?: return
        try {
            val next = net.shiroha233.roadweaver.client.ConfigScreenFactory.createConfigScreen(this)
            if (next != null) {
                mc.setScreen(next)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onRequestView() {
        val minX = kotlin.math.floor(kotlin.math.min(view.getMinX(), view.getMaxX())).toInt() - 32
        val maxX = kotlin.math.ceil(kotlin.math.max(view.getMinX(), view.getMaxX())).toInt() + 32
        val minZ = kotlin.math.floor(kotlin.math.min(view.getMinZ(), view.getMaxZ())).toInt() - 32
        val maxZ = kotlin.math.ceil(kotlin.math.max(view.getMinZ(), view.getMaxZ())).toInt() + 32

        val mc = this.minecraft ?: return

        val server: MinecraftServer? = mc.singleplayerServer
        if (server !== null) {
            // 单人模式：本地构造快照
            val level: ServerLevel? = server.getLevel(Level.OVERWORLD)
            if (level !== null) {
                var cx = 0
                var cz = 0
                if (mc.player !== null) {
                    cx = kotlin.math.round(mc.player!!.x).toInt()
                    cz = kotlin.math.round(mc.player!!.z).toInt()
                }
                val radiusBlocks = getRadiusBlocks()
                val fcx = cx
                val fcz = cz
                val fMinX = minX
                val fMaxX = maxX
                val fMinZ = minZ
                val fMaxZ = maxZ
                val currentSeq = state.incrementAndGetRequestSeq()

                CompletableFuture
                    .supplyAsync(
                        { MapDataCollector.build(level, fMinX, fMinZ, fMaxX, fMaxZ, fcx, fcz, radiusBlocks) },
                        ComputeService.executor()
                    )
                    .thenAccept { snap ->
                        mc.execute {
                            if (state.getCurrentRequestSeq() == currentSeq) {
                                setSnapshot(snap)
                            }
                        }
                    }
            }
        } else {
            // 多人模式：发送网络请求
            state.incrementAndGetRequestSeq()
            ClientNetBridge.requestSnapshot(minX, minZ, maxX, maxZ)
        }
    }

    override fun onTeleportTo(pos: BlockPos) {
        ClientNetBridge.requestTeleport(pos.x, pos.y, pos.z)
    }

    override fun onManualConnect(a: BlockPos, b: BlockPos) {
        ClientNetBridge.requestManualConnect(a.x, a.z, b.x, b.z)
    }

    override fun onCenterToPlayer() {
        val mc = this.minecraft
        if (mc === null || mc.player === null) return
        val contentW = mapW - MapTheme.INNER_PADDING * 2
        val contentH = mapH - MapTheme.INNER_PADDING * 2
        view.calibrateInitialToPlayer(mc, contentW, contentH, MapTheme.GRID_TARGET_PX)
        onRequestView()
    }

    override fun onCenterToSpawn() {
        val mc = this.minecraft ?: return

        var spawn: BlockPos? = null
        val server = mc.singleplayerServer
        if (server !== null) {
            val level: ServerLevel? = server.getLevel(Level.OVERWORLD)
            if (level !== null) {
                spawn = level.sharedSpawnPos
            }
        }
        if (spawn === null && mc.level !== null) {
            spawn = mc.level!!.sharedSpawnPos
        }
        if (spawn === null) return

        val contentW = mapW - MapTheme.INNER_PADDING * 2
        val contentH = mapH - MapTheme.INNER_PADDING * 2
        view.centerOn(spawn.x.toDouble(), spawn.z.toDouble(), contentW, contentH)
        onRequestView()
    }

    // ========== 公共方法 ==========

    fun setSnapshot(snapshot: MapSnapshot?) {
        if (snapshot !== null) {
            this.snapshot = snapshot
            MapSnapshotCache.put(snapshot)
        }
    }

    fun onMapSnapshotReceived(s: MapSnapshot, seq: Int) {
        setSnapshot(s)
    }

    // ========== 辅助方法 ==========

    private fun computeMapRect() {
        val availW = this.width - MapTheme.OUTER_PADDING * 2
        val availH = this.height - MapTheme.OUTER_PADDING * 2
        val ratio = MapTheme.TEX_WIDTH.toFloat() / MapTheme.TEX_HEIGHT
        var w = availW
        var h = Math.round(w / ratio)
        if (h > availH) {
            h = availH
            w = Math.round(h * ratio)
        }
        mapW = w
        mapH = h
        mapX = (this.width - w) / 2
        mapY = (this.height - h) / 2
    }

    private fun computeThickness(): Int {
        val contentW = mapW - MapTheme.INNER_PADDING * 2
        val contentH = mapH - MapTheme.INNER_PADDING * 2
        val ppb = kotlin.math.min(view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH))
        val t = kotlin.math.round(ppb).toInt()
        return kotlin.math.max(MapTheme.MIN_THICKNESS, kotlin.math.min(t, MapTheme.MAX_THICKNESS))
    }

    private fun computePointSize(): Int {
        return MapTheme.BASE_POINT_SIZE + computeThickness()
    }

    private fun getRadiusBlocks(): Int {
        return try {
            val cfg = net.shiroha233.roadweaver.config.ConfigService.get()
            val radiusChunks = if (cfg.dynamicPlanEnabled()) cfg.dynamicPlanRadiusChunks() else cfg.initialPlanRadiusChunks()
            kotlin.math.max(1, radiusChunks) * 16
        } catch (_: Throwable) {
            256 * 16
        }
    }

    private fun openContextMenuFor(target: BlockPos, x: Int, y: Int) {
        contextMenu.clearItems()
        contextMenu.addItem(MENU_TELEPORT, Runnable { onTeleportTo(target) })
        contextMenu.addSeparator()
        contextMenu.addItem(MENU_SET_ALIAS, Runnable { openAliasDialog(target) })
        contextMenu.addItem(MENU_EDIT_NOTE, Runnable { openNoteEditor(target) })
        contextMenu.open(x, y)
    }

    /** 打开别名设置对话框 */
    private fun openAliasDialog(target: BlockPos) {
        val mc = this.minecraft ?: return
        val currentAlias = ClientMapNotes.getAlias(target)
        mc.setScreen(
            SimpleTextInputScreen(
                DIALOG_ALIAS_TITLE,
                currentAlias ?: "",
                java.util.function.Consumer { alias -> ClientMapNotes.setAlias(target, alias) },
                this
            )
        )
    }

    /** 打开笔记编辑器（书与笔风格） */
    private fun openNoteEditor(target: BlockPos) {
        val mc = this.minecraft ?: return
        mc.setScreen(NoteEditScreen(target, this))
    }

    private fun computeConfigBtnBounds(): IntArray {
        val x = mapX + MapTheme.INNER_PADDING + 4
        val y = mapY + MapTheme.INNER_PADDING + 4
        val w = this.font.width(BTN_CONFIG) + 6
        val h = this.font.lineHeight + 4
        return intArrayOf(x, y, w, h)
    }

    private fun computeManualBtnBounds(): IntArray {
        val lbl = Component.empty().append(BTN_MANUAL).append(": ").append(Component.translatable("gui.roadweaver.common.on"))
        val w = this.font.width(lbl) + 6
        val h = this.font.lineHeight + 4
        val x = mapX + MapTheme.INNER_PADDING + 4
        val y = mapY + mapH - MapTheme.INNER_PADDING - 4 - h
        return intArrayOf(x, y, w, h)
    }

    private fun insideRect(x: Double, y: Double, bounds: IntArray): Boolean {
        return x >= bounds[0] && x <= bounds[0] + bounds[2] && y >= bounds[1] && y <= bounds[1] + bounds[3]
    }

    private fun insideRect(x: Double, y: Double, rx: Int, ry: Int, rw: Int, rh: Int): Boolean {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh
    }
}
