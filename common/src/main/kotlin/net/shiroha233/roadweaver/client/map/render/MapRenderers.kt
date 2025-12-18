package net.shiroha233.roadweaver.client.map.render

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.helpers.Records
import java.util.function.IntUnaryOperator

object MapRenderers {
    fun interface SegmentInView {
        fun test(x1: Int, z1: Int, x2: Int, z2: Int): Boolean
    }

    @JvmStatic
    fun renderGrid(
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
        GridRenderer.render(
            g,
            font,
            mapX,
            mapY,
            mapW,
            mapH,
            innerPad,
            viewMinX,
            viewMaxX,
            viewMinZ,
            viewMaxZ,
            colorGrid,
            gridTargetPx,
            colorText
        )
    }

    @JvmStatic
    fun renderStructures(
        g: GuiGraphics,
        points: List<BlockPos>,
        toScreenX: IntUnaryOperator,
        toScreenY: IntUnaryOperator,
        isInViewWorld: java.util.function.BiPredicate<Int, Int>,
        size: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        for (p in points) {
            val bx = p.x
            val bz = p.z
            if (!isInViewWorld.test(bx, bz)) continue
            val x = toScreenX.applyAsInt(bx)
            val y = toScreenY.applyAsInt(bz)
            RenderUtils.drawPoint(g, x, y, size, color, left, top, right, bottom)
        }
    }

    @JvmStatic
    fun renderConnections(
        g: GuiGraphics,
        connections: List<Records.StructureConnection>,
        segmentInView: SegmentInView,
        toScreenX: IntUnaryOperator,
        toScreenY: IntUnaryOperator,
        thickness: Int,
        colorPlanned: Int,
        colorGenerating: Int,
        colorCompleted: Int,
        colorFailed: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        for (c in connections) {
            val fx = c.from.x
            val fz = c.from.z
            val tx = c.to.x
            val tz = c.to.z
            if (!segmentInView.test(fx, fz, tx, tz)) continue
            val x1 = toScreenX.applyAsInt(fx)
            val y1 = toScreenY.applyAsInt(fz)
            val x2 = toScreenX.applyAsInt(tx)
            val y2 = toScreenY.applyAsInt(tz)
            when (c.status) {
                Records.ConnectionStatus.PLANNED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorPlanned, thickness, left, top, right, bottom)
                }
                Records.ConnectionStatus.GENERATING -> {
                    RenderUtils.drawThickDashedLine(g, x1, y1, x2, y2, colorGenerating, thickness, 8, 6, left, top, right, bottom)
                }
                Records.ConnectionStatus.COMPLETED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorCompleted, thickness, left, top, right, bottom)
                }
                Records.ConnectionStatus.FAILED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorFailed, thickness, left, top, right, bottom)
                }
            }
        }
    }

    @JvmStatic
    fun renderRoadPolylines(
        g: GuiGraphics,
        polylines: List<List<BlockPos>>,
        segmentInView: SegmentInView,
        toScreenX: IntUnaryOperator,
        toScreenY: IntUnaryOperator,
        thickness: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        lodStepBlocks: Int
    ) {
        for (pl in polylines) {
            if (pl.size < 2) continue
            var keep = pl[0]
            for (i in 1 until pl.size) {
                val curr = pl[i]
                val dx = curr.x - keep.x
                val dz = curr.z - keep.z
                val adx = kotlin.math.abs(dx)
                val adz = kotlin.math.abs(dz)
                val manhattan = adx + adz
                if (manhattan < lodStepBlocks) continue

                val x1w = keep.x
                val z1w = keep.z
                val x2w = curr.x
                val z2w = curr.z

                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    val x1 = toScreenX.applyAsInt(x1w)
                    val y1 = toScreenY.applyAsInt(z1w)
                    val x2 = toScreenX.applyAsInt(x2w)
                    val y2 = toScreenY.applyAsInt(z2w)
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom)
                }
                keep = curr
            }
            val tail = pl[pl.size - 1]
            if (!tail.equals(keep)) {
                val x1w = keep.x
                val z1w = keep.z
                val x2w = tail.x
                val z2w = tail.z
                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    val x1 = toScreenX.applyAsInt(x1w)
                    val y1 = toScreenY.applyAsInt(z1w)
                    val x2 = toScreenX.applyAsInt(x2w)
                    val y2 = toScreenY.applyAsInt(z2w)
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom)
                }
            }
        }
    }

    @JvmStatic
    fun renderLegend(
        g: GuiGraphics,
        font: Font,
        rightBound: Int,
        startY: Int,
        gap: Int,
        colorText: Int,
        colorStruct: Int,
        colorPlanned: Int,
        colorGenerating: Int,
        colorCompleted: Int,
        colorFailed: Int,
        structuresCount: Int,
        plannedCount: Int,
        generatingCount: Int,
        completedCount: Int,
        failedCount: Int
    ) {
        var y = startY

        val l1 = Component.translatable("gui.roadweaver.map.legend.structures").append(": ").append(structuresCount.toString())
        val w1 = font.width(l1)
        val x1 = rightBound - w1
        var sr = x1 - gap
        g.fill(sr - 5, y + 1, sr, y + 6, colorStruct)
        g.drawString(font, l1, x1, y, colorText, false)

        y += 16
        val l2 = Component.translatable("gui.roadweaver.map.legend.planned").append(": ").append(plannedCount.toString())
        val w2 = font.width(l2)
        val x2 = rightBound - w2
        sr = x2 - gap
        g.fill(sr - 28, y + 2, sr, y + 7, colorPlanned)
        g.drawString(font, l2, x2, y, colorText, false)

        y += 16
        val l3 = Component.translatable("gui.roadweaver.map.legend.generating").append(": ").append(generatingCount.toString())
        val w3 = font.width(l3)
        val x3 = rightBound - w3
        sr = x3 - gap
        val cy = y + 4
        RenderUtils.drawThickDashedLine(g, sr - 28, cy, sr, cy, colorGenerating, 5, 8, 6, sr - 28, y + 1, sr, y + 8)
        g.drawString(font, l3, x3, y, colorText, false)

        y += 16
        val l4 = Component.translatable("gui.roadweaver.map.legend.completed").append(": ").append(completedCount.toString())
        val w4 = font.width(l4)
        val x4 = rightBound - w4
        sr = x4 - gap
        g.fill(sr - 28, y + 2, sr, y + 7, colorCompleted)
        g.drawString(font, l4, x4, y, colorText, false)

        y += 16
        val l5 = Component.translatable("gui.roadweaver.map.legend.failed").append(": ").append(failedCount.toString())
        val w5 = font.width(l5)
        val x5 = rightBound - w5
        sr = x5 - gap
        g.fill(sr - 28, y + 2, sr, y + 7, colorFailed)
        g.drawString(font, l5, x5, y, colorText, false)
    }

    @JvmStatic
    fun drawPlayerArrow(
        g: GuiGraphics,
        sx: Int,
        sy: Int,
        yawDeg: Float,
        tipLen: Int,
        baseLen: Int,
        baseHalfWidth: Int,
        color: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        pxPerBlockX: Double,
        pxPerBlockZ: Double
    ) {
        val rx = Math.toRadians(yawDeg.toDouble())
        val vx = -kotlin.math.sin(rx)
        val vz = kotlin.math.cos(rx)

        var dirX = vx * pxPerBlockX
        var dirY = vz * pxPerBlockZ
        var mag = kotlin.math.hypot(dirX, dirY)
        if (mag < 1e-6) {
            dirX = 1.0
            dirY = 0.0
            mag = 1.0
        }
        dirX /= mag
        dirY /= mag

        val px = sx + dirX * tipLen
        val py = sy + dirY * tipLen
        val bx = sx - dirX * baseLen
        val by = sy - dirY * baseLen
        val perpX = -dirY
        val perpY = dirX

        val bx1 = bx + perpX * baseHalfWidth
        val by1 = by + perpY * baseHalfWidth
        val bx2 = bx - perpX * baseHalfWidth
        val by2 = by - perpY * baseHalfWidth

        val ipx = kotlin.math.round(px).toInt()
        val ipy = kotlin.math.round(py).toInt()
        val ibx1 = kotlin.math.round(bx1).toInt()
        val iby1 = kotlin.math.round(by1).toInt()
        val ibx2 = kotlin.math.round(bx2).toInt()
        val iby2 = kotlin.math.round(by2).toInt()

        val outline = 0xFFFFFFFF.toInt()
        RenderUtils.fillTriangle(g, ipx - 1, ipy, ibx1 - 1, iby1, ibx2 - 1, iby2, outline, left, top, right, bottom)
        RenderUtils.fillTriangle(g, ipx + 1, ipy, ibx1 + 1, iby1, ibx2 + 1, iby2, outline, left, top, right, bottom)
        RenderUtils.fillTriangle(g, ipx, ipy - 1, ibx1, iby1 - 1, ibx2, iby2 - 1, outline, left, top, right, bottom)
        RenderUtils.fillTriangle(g, ipx, ipy + 1, ibx1, iby1 + 1, ibx2, iby2 + 1, outline, left, top, right, bottom)
        RenderUtils.fillTriangle(g, ipx, ipy, ibx1, iby1, ibx2, iby2, color, left, top, right, bottom)
    }
}
