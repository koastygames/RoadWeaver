package net.shiroha233.roadweaver.client.map.render

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

object GridRenderer {
    @JvmStatic
    fun render(
        g: GuiGraphics,
        font: Font,
        mapX: Int,
        mapY: Int,
        mapW: Int,
        mapH: Int,
        innerPad: Int,
        viewMinX: Double,
        viewMaxX: Double,
        viewMinZ: Double,
        viewMaxZ: Double,
        colorGrid: Int,
        gridTargetPx: Int,
        colorText: Int
    ) {
        val left = mapX + innerPad
        val top = mapY + innerPad
        val right = mapX + mapW - innerPad
        val bottom = mapY + mapH - innerPad

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        val ppbX = contentW / max(1.0, (viewMaxX - viewMinX))
        val ppbZ = contentH / max(1.0, (viewMaxZ - viewMinZ))
        val ppb = (ppbX + ppbZ) * 0.5
        val step = snapStep(round(gridTargetPx / max(0.001, ppb)).toInt())

        val startX = floor(viewMinX / step).toInt() * step
        val endX = ceil(viewMaxX / step).toInt() * step
        var wx = startX
        while (wx <= endX) {
            val sx = toScreenX(wx, mapX, mapW, innerPad, viewMinX, viewMaxX)
            if (sx >= left && sx <= right) g.fill(sx, top, sx + 1, bottom, colorGrid)
            wx += step
        }

        val startZ = floor(viewMinZ / step).toInt() * step
        val endZ = ceil(viewMaxZ / step).toInt() * step
        var wz = startZ
        while (wz <= endZ) {
            val sy = toScreenY(wz, mapY, mapH, innerPad, viewMinZ, viewMaxZ)
            if (sy >= top && sy <= bottom) g.fill(left, sy, right, sy + 1, colorGrid)
            wz += step
        }

        val labelComp: Component = if (step % 16 == 0) {
            val chunks = step / 16
            Component.translatable("gui.roadweaver.map.grid.scale.chunks_blocks", chunks, step)
        } else {
            Component.translatable("gui.roadweaver.map.grid.scale.blocks", step)
        }

        val pad = 4
        val tw = font.width(labelComp)
        val tx = right - tw - pad
        val ty = bottom - font.lineHeight - pad
        g.drawString(font, labelComp, tx, ty, colorText, false)
    }

    @JvmStatic
    fun computeGridStep(
        mapX: Int,
        mapY: Int,
        mapW: Int,
        mapH: Int,
        innerPad: Int,
        viewMinX: Double,
        viewMaxX: Double,
        viewMinZ: Double,
        viewMaxZ: Double,
        gridTargetPx: Int
    ): Int {
        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2
        val ppbX = contentW / max(1.0, (viewMaxX - viewMinX))
        val ppbZ = contentH / max(1.0, (viewMaxZ - viewMinZ))
        val ppb = (ppbX + ppbZ) * 0.5
        val approx = round(gridTargetPx / max(0.001, ppb)).toInt()
        return snapStep(approx)
    }

    private fun toScreenX(blockX: Int, mapX: Int, mapW: Int, innerPad: Int, viewMinX: Double, viewMaxX: Double): Int {
        val contentW = mapW - innerPad * 2
        val rangeX = max(1.0, viewMaxX - viewMinX)
        val nx = (blockX - viewMinX) / rangeX
        return mapX + innerPad + round(nx * contentW).toInt()
    }

    private fun toScreenY(blockZ: Int, mapY: Int, mapH: Int, innerPad: Int, viewMinZ: Double, viewMaxZ: Double): Int {
        val contentH = mapH - innerPad * 2
        val rangeZ = max(1.0, viewMaxZ - viewMinZ)
        val nz = (blockZ - viewMinZ) / rangeZ
        return mapY + innerPad + round(nz * contentH).toInt()
    }

    private fun snapStep(approx: Int): Int {
        val steps = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192)
        if (approx <= 1) return 1
        for (s in steps) {
            if (approx <= s) return s
        }
        return steps[steps.size - 1]
    }
}
