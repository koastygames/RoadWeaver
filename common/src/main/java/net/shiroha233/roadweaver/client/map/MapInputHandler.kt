package net.shiroha233.roadweaver.client.map

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * 地图输入处理器 - 处理所有鼠标和键盘输入
 *
 * 设计原理：
 * 1. 单一职责：只负责输入事件处理，不涉及渲染
 * 2. 解耦：通过回调接口与 Screen 解耦
 * 3. 可扩展：新增快捷键只需在此处添加
 */
class MapInputHandler(
    private val state: MapState,
    private val view: MapView,
    private val callbacks: Callbacks
) {

    /** 输入事件回调接口 */
    interface Callbacks {
        fun onCloseScreen()
        fun onOpenConfig()
        fun onRequestView()
        fun onTeleportTo(pos: BlockPos)
        fun onManualConnect(a: BlockPos, b: BlockPos)
        fun onCenterToPlayer()
        fun onCenterToSpawn()
    }

    // 地图布局参数（由 Screen 更新）
    private var mapX: Int = 0
    private var mapY: Int = 0
    private var mapW: Int = 0
    private var mapH: Int = 0
    private var innerPad: Int = 0

    fun updateLayout(mapX: Int, mapY: Int, mapW: Int, mapH: Int, innerPad: Int) {
        this.mapX = mapX
        this.mapY = mapY
        this.mapW = mapW
        this.mapH = mapH
        this.innerPad = innerPad
    }

    // ========== 鼠标输入 ==========

    fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (!insideMap(mouseX, mouseY)) return false

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        val factor = if (delta > 0) 0.9 else 1.1
        val cx = view.screenToWorldX(mouseX, mapX, innerPad, contentW)
        val cz = view.screenToWorldZ(mouseY, mapY, innerPad, contentH)
        view.applyZoomAround(cx, cz, factor, contentW, contentH, MapTheme.GRID_TARGET_PX)

        state.scheduleZoomDebounce(MapTheme.ZOOM_DEBOUNCE_MS)
        state.closeContextMenu()
        return true
    }

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        snapshot: MapSnapshot,
        configBtnX: Int,
        configBtnY: Int,
        configBtnW: Int,
        configBtnH: Int,
        manualBtnX: Int,
        manualBtnY: Int,
        manualBtnW: Int,
        manualBtnH: Int
    ): Boolean {

        // 配置按钮点击
        if (button == 0 && insideRect(mouseX, mouseY, configBtnX, configBtnY, configBtnW, configBtnH)) {
            callbacks.onOpenConfig()
            return true
        }

        // 手动连接按钮点击
        if (button == 0 && insideRect(mouseX, mouseY, manualBtnX, manualBtnY, manualBtnW, manualBtnH)) {
            state.toggleManualMode()
            return true
        }

        // 右键菜单处理
        if (state.isContextMenuOpen()) {
            state.closeContextMenu()
        }

        // 手动连接模式下的点击
        if (state.isManualMode() && insideMap(mouseX, mouseY) && button == 0) {
            val best = findNearestStructure(snapshot, mouseX, mouseY)
            if (best != null) {
                if (!state.hasSelection()) {
                    state.selectFirstPoint(best)
                } else if (state.getSelectedA() == best) {
                    state.clearSelection()
                } else {
                    callbacks.onManualConnect(state.getSelectedA()!!, best)
                    state.clearSelection()
                    callbacks.onRequestView()
                }
                state.closeContextMenu()
                return true
            }
        }

        // 左键拖拽开始
        if (insideMap(mouseX, mouseY) && button == 0) {
            state.startDrag(button, mouseX, mouseY)
            return true
        }

        // 右键打开菜单
        if (insideMap(mouseX, mouseY) && button == 1) {
            val best = findNearestStructure(snapshot, mouseX, mouseY)
            if (best != null) {
                state.openContextMenu(mouseX.toInt(), mouseY.toInt(), best)
                return true
            }
        }

        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!state.isDragging() || button != state.getDragButton()) {
            return false
        }

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        val dx = mouseX - state.getLastMouseX()
        val dy = mouseY - state.getLastMouseY()
        view.panByScreenDelta(dx, dy, contentW, contentH)
        state.updateDrag(mouseX, mouseY)
        view.lockAspect(contentW, contentH)
        state.closeContextMenu()
        return true
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!state.isDragging() || button != state.getDragButton()) {
            return false
        }

        state.endDrag()
        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        view.clampZoom(contentW, contentH, MapTheme.GRID_TARGET_PX)
        state.cancelZoomDebounce()
        callbacks.onRequestView()
        return true
    }

    // ========== 键盘输入 ==========

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int, mc: Minecraft?): Boolean {
        // 检查是否是打开地图的快捷键（按下关闭地图）
        if (mc != null) {
            for (mapping: KeyMapping in mc.options.keyMappings) {
                if ("key.roadweaver.open_map" == mapping.name && mapping.matches(keyCode, scanCode)) {
                    while (mapping.consumeClick()) {
                        // 清空残留点击
                    }
                    callbacks.onCloseScreen()
                    return true
                }
            }
        }

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        val panStep = 50.0

        when (keyCode) {
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> {
                view.panByScreenDelta(0.0, panStep, contentW, contentH)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> {
                view.panByScreenDelta(0.0, -panStep, contentW, contentH)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                view.panByScreenDelta(panStep, 0.0, contentW, contentH)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                view.panByScreenDelta(-panStep, 0.0, contentW, contentH)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                val cx = (view.getMinX() + view.getMaxX()) / 2
                val cz = (view.getMinZ() + view.getMaxZ()) / 2
                view.applyZoomAround(cx, cz, 0.8, contentW, contentH, MapTheme.GRID_TARGET_PX)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                val cx = (view.getMinX() + view.getMaxX()) / 2
                val cz = (view.getMinZ() + view.getMaxZ()) / 2
                view.applyZoomAround(cx, cz, 1.25, contentW, contentH, MapTheme.GRID_TARGET_PX)
                callbacks.onRequestView()
                return true
            }

            GLFW.GLFW_KEY_HOME -> {
                callbacks.onCenterToPlayer()
                return true
            }

            GLFW.GLFW_KEY_END -> {
                callbacks.onCenterToSpawn()
                return true
            }

            GLFW.GLFW_KEY_ESCAPE -> {
                if (state.isContextMenuOpen()) {
                    state.closeContextMenu()
                    return true
                }
                if (state.isManualMode()) {
                    state.setManualMode(false)
                    return true
                }
            }
        }

        return false
    }

    // ========== 辅助方法 ==========

    fun insideMap(x: Double, y: Double): Boolean {
        return x >= mapX + innerPad && x <= mapX + mapW - innerPad && y >= mapY + innerPad && y <= mapY + mapH - innerPad
    }

    private fun insideRect(x: Double, y: Double, rx: Int, ry: Int, rw: Int, rh: Int): Boolean {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh
    }

    fun findNearestStructure(snapshot: MapSnapshot?, mouseX: Double, mouseY: Double): BlockPos? {
        if (snapshot == null) return null

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        var bestDist = Int.MAX_VALUE
        var best: BlockPos? = null

        val structures = snapshot.structures()
        for (p in structures) {
            if (!view.isInViewWorld(p.x, p.z)) continue
            val x = view.toScreenX(p.x, mapX, innerPad, contentW)
            val y = view.toScreenY(p.z, mapY, innerPad, contentH)
            val dx = abs(x - mouseX).toInt()
            val dy = abs(y - mouseY).toInt()
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist) {
                bestDist = d2
                best = p
            }
        }

        return if (best != null && bestDist <= MapTheme.STRUCTURE_CLICK_RADIUS_SQ) best else null
    }
}
