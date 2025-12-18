package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 仅负责预热 TerrainSamplingCache：通过固定粗步长的轻量 A* 访问沿线采样点，
 * 提前填充高度/群系/水体等噪声采样缓存。
 * 不参与任何道路生成结果。
 */
object TerrainCachePrewarmer {
    private const val COARSE_STEP = 64

    @JvmStatic
    fun prewarmAlongRoute(
        startGround: BlockPos?,
        endGround: BlockPos?,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache?
    ) {
        if (cache === null) return
        if (startGround === null || endGround === null) return
        if (maxSteps <= 0) return

        // 仅预热：计算失败也不影响主流程
        calculateCoarseSkeleton(startGround, endGround, level, maxSteps, cache)
    }

    private fun calculateCoarseSkeleton(
        startGround: BlockPos,
        endGround: BlockPos,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache
    ): List<BlockPos>? {
        val d = COARSE_STEP

        val start = BlockPos(snapToGrid(startGround.x, d), 0, snapToGrid(startGround.z, d))
        val end = BlockPos(snapToGrid(endGround.x, d), 0, snapToGrid(endGround.z, d))
        val startG = BlockPos(start.x, heightSampler(cache, start.x, start.z, level), start.z)
        val endG = BlockPos(end.x, heightSampler(cache, end.x, end.z, level), end.z)

        val open = PriorityQueue<CoarseNode>(compareBy { it.f })
        val best = HashMap<Long, CoarseNode>()
        val closed = HashSet<Long>()
        val startNode = CoarseNode(startG, null, 0.0, heuristicEuclid(startG, endG))
        open.add(startNode)
        best[posKey2d(startG)] = startNode

        val neighborOffsets = arrayOf(
            intArrayOf(d, 0), intArrayOf(-d, 0), intArrayOf(0, d), intArrayOf(0, -d),
            intArrayOf(d, d), intArrayOf(d, -d), intArrayOf(-d, d), intArrayOf(-d, -d)
        )

        var stepsBudget = maxOf(1, maxSteps)
        while (open.isNotEmpty() && stepsBudget-- > 0) {
            if (Thread.currentThread().isInterrupted) return null
            val cur = open.poll() ?: break
            val ck = posKey2d(cur.pos)
            if (!closed.add(ck)) continue

            // 到达终点附近即可：预热目的，不需要严格到点
            if (manhattan2d(cur.pos, endG) < d * 2) {
                // reconstructCoarse 会触发一些沿线采样（height/water/biome），进一步提升预热效果
                return reconstructCoarse(cur)
            }

            for (off in neighborOffsets) {
                val nx = cur.pos.x + off[0]
                val nz = cur.pos.z + off[1]
                val ny = heightSampler(cache, nx, nz, level)
                // 额外预热水体/群系相关缓存
                cache.isColumnWater(level, nx, nz)
                cache.getBiome(level, nx, nz)

                val np = BlockPos(nx, ny, nz)
                val nk = posKey2d(np)
                if (closed.contains(nk)) continue

                val stepCost = if (abs(off[0]) + abs(off[1]) == 2 * d) 1.41421356237 else 1.0
                val elevation = abs(ny - cur.pos.y)
                val g = cur.g + stepCost + elevation * 0.02
                val h = heuristicEuclid(np, endG)
                val f = g + h

                val prevBest = best[nk]
                if (prevBest === null || g < prevBest.g) {
                    val nxt = CoarseNode(np, cur, g, f)
                    best[nk] = nxt
                    open.add(nxt)
                }
            }
        }
        return null
    }

    private fun posKey2d(p: BlockPos): Long {
        return (p.x.toLong() shl 32) xor (p.z.toLong() and 0xffffffffL)
    }

    private fun manhattan2d(a: BlockPos, b: BlockPos): Int {
        return abs(a.x - b.x) + abs(a.z - b.z)
    }

    private fun heuristicEuclid(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz) / COARSE_STEP.toDouble()
    }

    private fun reconstructCoarse(end: CoarseNode): List<BlockPos> {
        val out = ArrayList<BlockPos>()
        var c: CoarseNode? = end
        while (c !== null) {
            out.add(c.pos)
            c = c.parent
        }
        out.reverse()
        return out
    }

    private fun heightSampler(cache: TerrainSamplingCache, x: Int, z: Int, level: ServerLevel): Int {
        return cache.height(level, x, z)
    }

    private fun snapToGrid(v: Int, gridSize: Int): Int {
        return Math.floorDiv(v, gridSize) * gridSize
    }

    private class CoarseNode(
        val pos: BlockPos,
        val parent: CoarseNode?,
        val g: Double,
        val f: Double
    )
}
