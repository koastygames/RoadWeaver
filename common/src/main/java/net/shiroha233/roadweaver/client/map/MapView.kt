package net.shiroha233.roadweaver.client.map

import net.minecraft.client.Minecraft
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import kotlin.math.abs
import kotlin.math.max

class MapView {
    private var minX: Double = 0.0
    private var maxX: Double = 0.0
    private var minZ: Double = 0.0
    private var maxZ: Double = 0.0
    private var initialized: Boolean = false

    internal fun isInitialized(): Boolean = initialized

    internal fun resetFromSnapshot(snapshot: MapSnapshot) {
        minX = snapshot.minX().toDouble()
        maxX = snapshot.maxX().toDouble()
        minZ = snapshot.minZ().toDouble()
        maxZ = snapshot.maxZ().toDouble()
        if (maxX - minX < 1) maxX = minX + 1
        if (maxZ - minZ < 1) maxZ = minZ + 1
        initialized = false
    }

    internal fun calibrateInitialToPlayer(mc: Minecraft?, contentW: Int, contentH: Int, gridTargetPx: Int) {
        if (mc == null || mc.player == null) return
        val px = mc.player!!.x
        val pz = mc.player!!.z
        val desiredBlocksPerCell = 16.0 * 16.0
        val desiredPxPerBlock = gridTargetPx / desiredBlocksPerCell
        if (desiredPxPerBlock <= 0) return
        val rangeX = contentW / desiredPxPerBlock
        val rangeZ = contentH / desiredPxPerBlock
        minX = px - rangeX * 0.5
        maxX = px + rangeX * 0.5
        minZ = pz - rangeZ * 0.5
        maxZ = pz + rangeZ * 0.5
        lockAspect(contentW, contentH)
        clampZoom(contentW, contentH, gridTargetPx)
        initialized = true
    }

    fun toScreenX(blockX: Int, mapX: Int, innerPad: Int, contentW: Int): Int {
        val rangeX = max(1.0, maxX - minX)
        val nx = (blockX - minX) / rangeX
        return mapX + innerPad + kotlin.math.round(nx * contentW).toInt()
    }

    fun toScreenY(blockZ: Int, mapY: Int, innerPad: Int, contentH: Int): Int {
        val rangeZ = max(1.0, maxZ - minZ)
        val nz = (blockZ - minZ) / rangeZ
        return mapY + innerPad + kotlin.math.round(nz * contentH).toInt()
    }

    fun screenToWorldX(sx: Double, mapX: Int, innerPad: Int, contentW: Int): Double {
        val nx = (sx - (mapX + innerPad)) / max(1.0, contentW.toDouble())
        return minX + nx * max(1.0, maxX - minX)
    }

    fun screenToWorldZ(sy: Double, mapY: Int, innerPad: Int, contentH: Int): Double {
        val ny = (sy - (mapY + innerPad)) / max(1.0, contentH.toDouble())
        return minZ + ny * max(1.0, maxZ - minZ)
    }

    fun isInViewWorld(x: Int, z: Int): Boolean {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ
    }

    internal fun segmentInViewWorld(x1: Int, z1: Int, x2: Int, z2: Int): Boolean {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minZ = minOf(z1, z2)
        val maxZ = maxOf(z1, z2)
        if (maxX < this.minX) return false
        if (minX > this.maxX) return false
        if (maxZ < this.minZ) return false
        if (minZ > this.maxZ) return false
        return true
    }

    internal fun pxPerBlockX(contentW: Int): Double {
        return contentW / max(1.0, (maxX - minX))
    }

    internal fun pxPerBlockZ(contentH: Int): Double {
        return contentH / max(1.0, (maxZ - minZ))
    }

    internal fun lockAspect(contentW: Int, contentH: Int) {
        if (contentW <= 0 || contentH <= 0) return
        val aspect = contentW / contentH.toDouble()
        val rx = max(1.0, maxX - minX)
        val rz = max(1.0, maxZ - minZ)
        val r = rx / rz
        if (abs(r - aspect) < 1e-6) return
        val cx = (minX + maxX) * 0.5
        val cz = (minZ + maxZ) * 0.5
        if (r > aspect) {
            val nrz = rx / aspect
            minZ = cz - nrz * 0.5
            maxZ = cz + nrz * 0.5
        } else {
            val nrx = rz * aspect
            minX = cx - nrx * 0.5
            maxX = cx + nrx * 0.5
        }
    }

    internal fun clampZoom(contentW: Int, contentH: Int, gridTargetPx: Int) {
        if (contentW <= 0 || contentH <= 0) return
        val minPpb = gridTargetPx / (512.0 * 16.0)
        val maxPpb = gridTargetPx / 16.0
        var rx = max(1.0, maxX - minX)
        var rz = max(1.0, maxZ - minZ)
        val ppbX = contentW / rx
        val ppbZ = contentH / rz
        val cx = (minX + maxX) * 0.5
        val cz = (minZ + maxZ) * 0.5
        var changed = false
        if (ppbX > maxPpb) {
            rx = contentW / maxPpb
            changed = true
        } else if (ppbX < minPpb) {
            rx = contentW / minPpb
            changed = true
        }
        if (ppbZ > maxPpb) {
            rz = contentH / maxPpb
            changed = true
        } else if (ppbZ < minPpb) {
            rz = contentH / minPpb
            changed = true
        }
        if (changed) {
            minX = cx - rx * 0.5
            maxX = cx + rx * 0.5
            minZ = cz - rz * 0.5
            maxZ = cz + rz * 0.5
            lockAspect(contentW, contentH)
        }
    }

    internal fun applyZoomAround(cx: Double, cz: Double, factor: Double, contentW: Int, contentH: Int, gridTargetPx: Int) {
        val rx = maxX - minX
        val rz = maxZ - minZ
        val nrx = max(1.0, rx * factor)
        val nrz = max(1.0, rz * factor)
        val ax = (cx - minX) / rx
        val az = (cz - minZ) / rz
        minX = cx - ax * nrx
        maxX = minX + nrx
        minZ = cz - az * nrz
        maxZ = minZ + nrz
        lockAspect(contentW, contentH)
        clampZoom(contentW, contentH, gridTargetPx)
    }

    internal fun panByScreenDelta(dx: Double, dy: Double, contentW: Int, contentH: Int) {
        val rx = maxX - minX
        val rz = maxZ - minZ
        val wx = -dx / max(1.0, contentW.toDouble()) * rx
        val wz = -dy / max(1.0, contentH.toDouble()) * rz
        minX += wx
        maxX += wx
        minZ += wz
        maxZ += wz
    }

    /** 将视图居中到指定世界坐标，保持当前缩放级别 */
    internal fun centerOn(worldX: Double, worldZ: Double, contentW: Int, contentH: Int) {
        val rx = maxX - minX
        val rz = maxZ - minZ
        minX = worldX - rx * 0.5
        maxX = worldX + rx * 0.5
        minZ = worldZ - rz * 0.5
        maxZ = worldZ + rz * 0.5
        lockAspect(contentW, contentH)
    }

    internal fun getMinX(): Double = minX
    internal fun getMaxX(): Double = maxX
    internal fun getMinZ(): Double = minZ
    internal fun getMaxZ(): Double = maxZ
}
