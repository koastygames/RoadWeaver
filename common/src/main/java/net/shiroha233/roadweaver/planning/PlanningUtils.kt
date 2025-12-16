package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records
import kotlin.math.max
import kotlin.math.min

object PlanningUtils {
    @JvmStatic
    fun pos2dKey(p: BlockPos): Long {
        val x = p.x.toLong()
        val z = p.z.toLong()
        return (x shl 32) xor (z and 0xffffffffL)
    }

    @JvmStatic
    fun edgeKey(a: BlockPos, b: BlockPos): Long {
        val ka = pos2dKey(a)
        val kb = pos2dKey(b)
        val lo = min(ka, kb)
        val hi = max(ka, kb)
        return (hi shl 1) xor lo
    }

    @JvmStatic
    fun sameEdge(a: Records.StructureConnection, b: Records.StructureConnection): Boolean {
        val af = a.from()
        val at = a.to()
        val bf = b.from()
        val bt = b.to()
        return (af == bf && at == bt) || (af == bt && at == bf)
    }

    @JvmStatic
    fun sameEdge(c: Records.StructureConnection, a: BlockPos, b: BlockPos): Boolean {
        val cf = c.from()
        val ct = c.to()
        return (cf == a && ct == b) || (cf == b && ct == a)
    }
}
