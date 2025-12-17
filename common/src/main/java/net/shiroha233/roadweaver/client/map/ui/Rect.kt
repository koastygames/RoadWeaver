package net.shiroha233.roadweaver.client.map.ui

/**
 * 简单矩形记录类 - 替代 int[] bounds，提供更清晰的语义
 */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun right(): Int = x + width
    fun bottom(): Int = y + height

    fun contains(px: Double, py: Double): Boolean {
        return px >= x && px <= right() && py >= y && py <= bottom()
    }

    /** 调整位置使矩形保持在屏幕内 */
    fun clampToScreen(screenW: Int, screenH: Int, margin: Int): Rect {
        var nx = x
        var ny = y
        if (nx + width > screenW - margin) nx = screenW - margin - width
        if (ny + height > screenH - margin) ny = screenH - margin - height
        if (nx < margin) nx = margin
        if (ny < margin) ny = margin
        return Rect(nx, ny, width, height)
    }
}
