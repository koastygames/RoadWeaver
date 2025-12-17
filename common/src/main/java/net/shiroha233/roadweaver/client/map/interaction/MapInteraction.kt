package net.shiroha233.roadweaver.client.map.interaction

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.shiroha233.roadweaver.client.map.MapView
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import net.shiroha233.roadweaver.client.map.data.MapSnapshot

object MapInteraction {
    @JvmStatic
    fun renderHoverHighlight(
        g: GuiGraphics,
        snapshot: MapSnapshot,
        view: MapView,
        mapX: Int,
        mapY: Int,
        mapW: Int,
        mapH: Int,
        innerPad: Int,
        mouseX: Double,
        mouseY: Double
    ) {
        val mx = mouseX
        val my = mouseY
        if (!insideMap(mx, my, mapX, mapY, mapW, mapH, innerPad)) return

        var bestDist = Int.MAX_VALUE
        var best: BlockPos? = null

        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2

        for (p in snapshot.structures()) {
            if (!view.isInViewWorld(p.x, p.z)) continue
            val x = view.toScreenX(p.x, mapX, innerPad, contentW)
            val y = view.toScreenY(p.z, mapY, innerPad, contentH)
            val dx = kotlin.math.abs((x - mx).toInt())
            val dy = kotlin.math.abs((y - my).toInt())
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist) {
                bestDist = d2
                best = p
            }
        }

        if (best != null && bestDist <= 64) {
            val x = view.toScreenX(best.x, mapX, innerPad, contentW)
            val y = view.toScreenY(best.z, mapY, innerPad, contentH)
            g.fill(x - 4, y - 4, x + 5, y + 5, 0xCCFFD54F.toInt())
        }
    }

    @JvmStatic
    fun renderHoverTooltip(
        g: GuiGraphics,
        font: Font,
        snapshot: MapSnapshot,
        view: MapView,
        mapX: Int,
        mapY: Int,
        mapW: Int,
        mapH: Int,
        innerPad: Int,
        mouseX: Double,
        mouseY: Double
    ) {
        val mx = mouseX
        val my = mouseY
        val contentW = mapW - innerPad * 2
        val contentH = mapH - innerPad * 2

        if (!insideMap(mx, my, mapX, mapY, mapW, mapH, innerPad)) {
            val wx = kotlin.math.round(view.screenToWorldX(mx, mapX, innerPad, contentW)).toInt()
            val wz = kotlin.math.round(view.screenToWorldZ(my, mapY, innerPad, contentH)).toInt()
            g.renderTooltip(font, Component.translatable("gui.roadweaver.map.coord", wx, wz), mx.toInt(), my.toInt())
            return
        }

        var bestDist = Int.MAX_VALUE
        var best: BlockPos? = null

        for (p in snapshot.structures()) {
            if (!view.isInViewWorld(p.x, p.z)) continue
            val x = view.toScreenX(p.x, mapX, innerPad, contentW)
            val y = view.toScreenY(p.z, mapY, innerPad, contentH)
            val dx = kotlin.math.abs((x - mx).toInt())
            val dy = kotlin.math.abs((y - my).toInt())
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist) {
                bestDist = d2
                best = p
            }
        }

        if (best != null && bestDist <= 64) {
            val name = snapshot.structureName(best)
            val alias = ClientMapNotes.getAlias(best)
            val coords = Component.translatable("gui.roadweaver.map.coord", best.x, best.z)
            val label = when {
                alias != null -> Component.literal(alias).append(" ").append(coords)
                name != null -> Component.literal(name).append(" ").append(coords)
                else -> coords
            }
            g.renderTooltip(font, label, mx.toInt(), my.toInt())
        } else {
            val wx = kotlin.math.round(view.screenToWorldX(mx, mapX, innerPad, contentW)).toInt()
            val wz = kotlin.math.round(view.screenToWorldZ(my, mapY, innerPad, contentH)).toInt()
            g.renderTooltip(font, Component.translatable("gui.roadweaver.map.coord", wx, wz), mx.toInt(), my.toInt())
        }
    }

    private fun insideMap(x: Double, y: Double, mapX: Int, mapY: Int, mapW: Int, mapH: Int, innerPad: Int): Boolean {
        return x >= mapX + innerPad && x <= mapX + mapW - innerPad && y >= mapY + innerPad && y <= mapY + mapH - innerPad
    }
}
