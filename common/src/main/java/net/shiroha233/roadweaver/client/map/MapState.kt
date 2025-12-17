package net.shiroha233.roadweaver.client.map

import net.minecraft.core.BlockPos
import java.util.concurrent.atomic.AtomicInteger

/**
 * 地图界面状态管理器 - 集中管理所有可变UI状态
 *
 * 设计原理：
 * 1. 单一职责：只负责状态存储和简单状态切换
 * 2. 可测试性：状态与渲染/输入分离，便于单元测试
 * 3. 清晰性：所有状态在一处，便于理解和调试
 */
class MapState {

    // ========== 拖拽状态 ==========
    private var dragging: Boolean = false
    private var dragButton: Int = 0
    private var lastMouseX: Double = 0.0
    private var lastMouseY: Double = 0.0

    // ========== 缩放防抖 ==========
    private var zoomDebouncePending: Boolean = false
    private var zoomDebounceDeadlineMs: Long = 0L

    // ========== 右键菜单 ==========
    private var contextMenuOpen: Boolean = false
    private var menuX: Int = 0
    private var menuY: Int = 0
    private var menuTarget: BlockPos? = null

    // ========== 手动连接模式 ==========
    private var manualMode: Boolean = false
    private var selectedA: BlockPos? = null

    // ========== 网络请求序列号 ==========
    private val requestSeq = AtomicInteger(0)

    // ========== 拖拽操作 ==========
    fun startDrag(button: Int, mouseX: Double, mouseY: Double) {
        dragging = true
        dragButton = button
        lastMouseX = mouseX
        lastMouseY = mouseY
        zoomDebouncePending = false
        closeContextMenu()
    }

    fun updateDrag(mouseX: Double, mouseY: Double) {
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    fun endDrag() {
        dragging = false
    }

    fun isDragging(): Boolean = dragging
    fun getDragButton(): Int = dragButton
    fun getLastMouseX(): Double = lastMouseX
    fun getLastMouseY(): Double = lastMouseY

    // ========== 缩放防抖 ==========
    fun scheduleZoomDebounce(delayMs: Long) {
        zoomDebouncePending = true
        zoomDebounceDeadlineMs = System.currentTimeMillis() + delayMs
    }

    fun cancelZoomDebounce() {
        zoomDebouncePending = false
    }

    fun isZoomDebounceReady(): Boolean {
        return zoomDebouncePending && System.currentTimeMillis() >= zoomDebounceDeadlineMs
    }

    fun clearZoomDebounce() {
        zoomDebouncePending = false
    }

    // ========== 右键菜单 ==========
    fun openContextMenu(x: Int, y: Int, target: BlockPos) {
        contextMenuOpen = true
        menuX = x
        menuY = y
        menuTarget = target
    }

    fun closeContextMenu() {
        contextMenuOpen = false
        menuTarget = null
    }

    fun isContextMenuOpen(): Boolean = contextMenuOpen
    fun getMenuX(): Int = menuX
    fun getMenuY(): Int = menuY
    fun getMenuTarget(): BlockPos? = menuTarget

    // ========== 手动连接模式 ==========
    fun toggleManualMode() {
        manualMode = !manualMode
        if (!manualMode) {
            selectedA = null
        }
        closeContextMenu()
    }

    fun setManualMode(enabled: Boolean) {
        manualMode = enabled
        if (!enabled) {
            selectedA = null
        }
    }

    fun isManualMode(): Boolean = manualMode

    fun selectFirstPoint(pos: BlockPos) {
        selectedA = pos
    }

    fun clearSelection() {
        selectedA = null
    }

    fun getSelectedA(): BlockPos? = selectedA

    fun hasSelection(): Boolean = selectedA != null

    // ========== 网络请求序列号 ==========
    fun incrementAndGetRequestSeq(): Int {
        return requestSeq.incrementAndGet()
    }

    fun getCurrentRequestSeq(): Int {
        return requestSeq.get()
    }

    // ========== 重置 ==========
    fun reset() {
        dragging = false
        zoomDebouncePending = false
        contextMenuOpen = false
        menuTarget = null
        manualMode = false
        selectedA = null
    }
}
