package net.shiroha233.roadweaver.client.map.render

import net.minecraft.client.gui.GuiGraphics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object RenderUtils {
    @JvmStatic
    fun drawLine(
        g: GuiGraphics,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        val steps = max(dx, dy)
        if (steps == 0) {
            if (x1 in left..right && y1 in top..bottom) g.fill(x1, y1, x1 + 1, y1 + 1, color)
            return
        }
        var fx = x1.toFloat()
        var fy = y1.toFloat()
        val sx = (x2 - x1) / steps.toFloat()
        val sy = (y2 - y1) / steps.toFloat()
        for (i in 0..steps) {
            val px = fx.roundToInt()
            val py = fy.roundToInt()
            if (px in left..right && py in top..bottom) {
                g.fill(px, py, px + 1, py + 1, color)
            }
            fx += sx
            fy += sy
        }
    }

    @JvmStatic
    fun drawThickLine(
        g: GuiGraphics,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        thicknessIn: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val thickness = max(1, thicknessIn)
        if (thickness == 1) {
            drawLine(g, x1, y1, x2, y2, color, left, top, right, bottom)
            return
        }
        val half = thickness / 2
        for (ox in -half..half) {
            for (oy in -half..half) {
                drawLine(g, x1 + ox, y1 + oy, x2 + ox, y2 + oy, color, left, top, right, bottom)
            }
        }
    }

    @JvmStatic
    fun drawDashedLine(
        g: GuiGraphics,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        dashIn: Int,
        gapIn: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val dash = max(1, dashIn)
        val gap = max(1, gapIn)
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        val steps = max(dx, dy)
        if (steps == 0) {
            if (x1 in left..right && y1 in top..bottom) g.fill(x1, y1, x1 + 1, y1 + 1, color)
            return
        }
        var fx = x1.toFloat()
        var fy = y1.toFloat()
        val sx = (x2 - x1) / steps.toFloat()
        val sy = (y2 - y1) / steps.toFloat()
        val pattern = dash + gap
        for (i in 0..steps) {
            val idx = i % pattern
            if (idx < dash) {
                val px = fx.roundToInt()
                val py = fy.roundToInt()
                if (px in left..right && py in top..bottom) {
                    g.fill(px, py, px + 1, py + 1, color)
                }
            }
            fx += sx
            fy += sy
        }
    }

    @JvmStatic
    fun drawThickDashedLine(
        g: GuiGraphics,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        thicknessIn: Int,
        dash: Int,
        gap: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val thickness = max(1, thicknessIn)
        if (thickness == 1) {
            drawDashedLine(g, x1, y1, x2, y2, color, dash, gap, left, top, right, bottom)
            return
        }
        val half = thickness / 2
        for (ox in -half..half) {
            for (oy in -half..half) {
                drawDashedLine(g, x1 + ox, y1 + oy, x2 + ox, y2 + oy, color, dash, gap, left, top, right, bottom)
            }
        }
    }

    @JvmStatic
    fun drawPoint(
        g: GuiGraphics,
        x: Int,
        y: Int,
        size: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val half = max(0, size / 2)
        if (x < left || x > right || y < top || y > bottom) return
        g.fill(x - half, y - half, x - half + size, y - half + size, color)
    }

    @JvmStatic
    fun fillTriangle(
        g: GuiGraphics,
        x1In: Int,
        y1In: Int,
        x2In: Int,
        y2In: Int,
        x3In: Int,
        y3In: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        var x1 = x1In
        var y1 = y1In
        var x2 = x2In
        var y2 = y2In
        var x3 = x3In
        var y3 = y3In

        if (y2 < y1) {
            val tx = x1
            val ty = y1
            x1 = x2
            y1 = y2
            x2 = tx
            y2 = ty
        }
        if (y3 < y1) {
            val tx = x1
            val ty = y1
            x1 = x3
            y1 = y3
            x3 = tx
            y3 = ty
        }
        if (y3 < y2) {
            val tx = x2
            val ty = y2
            x2 = x3
            y2 = y3
            x3 = tx
            y3 = ty
        }

        if (y1 == y3) return

        val inv12 = if (y2 != y1) (x2 - x1) / (y2 - y1).toFloat() else 0f
        val inv13 = (x3 - x1) / (y3 - y1).toFloat()
        val inv23 = if (y3 != y2) (x3 - x2) / (y3 - y2).toFloat() else 0f

        var sx12 = x1.toFloat()
        var sx13 = x1.toFloat()
        for (y in y1 until y2) {
            if (y < top || y > bottom) {
                sx12 += inv12
                sx13 += inv13
                continue
            }
            var xa = min(sx12, sx13).roundToInt()
            var xb = max(sx12, sx13).roundToInt()
            xa = max(xa, left)
            xb = min(xb, right)
            if (xa <= xb) g.fill(xa, y, xb + 1, y + 1, color)
            sx12 += inv12
            sx13 += inv13
        }

        var sx23 = x2.toFloat()
        var sx13b = x1 + inv13 * (y2 - y1)
        for (y in y2..y3) {
            if (y < top || y > bottom) {
                sx23 += inv23
                sx13b += inv13
                continue
            }
            var xa = min(sx23, sx13b).roundToInt()
            var xb = max(sx23, sx13b).roundToInt()
            xa = max(xa, left)
            xb = min(xb, right)
            if (xa <= xb) g.fill(xa, y, xb + 1, y + 1, color)
            sx23 += inv23
            sx13b += inv13
        }
    }
}
