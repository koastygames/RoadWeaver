package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.Heightmap

class TerrainSamplingCache {
    private val waterCache = HashMap<Long, Boolean>()
    private val nearWaterCache = HashMap<Long, Boolean>()
    private val columnWaterCache = HashMap<Long, Boolean>()
    private val heightCache = HashMap<Long, Int>()
    private val oceanFloorCache = HashMap<Long, Int>()
    private val biomeCache = HashMap<Long, Holder<Biome>>()

    companion object {
        private fun hashXZ(x: Int, z: Int): Long {
            return (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
        }
    }

    fun height(level: ServerLevel, x: Int, z: Int): Int {
        val key = hashXZ(x, z)
        heightCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()
        val generator = level.chunkSource.generator
        val rs = level.chunkSource.generatorState.randomState()
        val sea = level.seaLevel
        val motion = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, rs)
        if (motion > sea + 2) {
            heightCache[key] = motion
            return motion
        }

        val surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs)
        val waterBiome = isWaterLike(level, x, z)
        val shouldUseSurface = waterBiome || (surface <= sea + 2 && oceanFloor(level, x, z) < sea)
        val h = if (shouldUseSurface) surface else motion

        heightCache[key] = h
        return h
    }

    fun isWaterLike(level: ServerLevel, x: Int, z: Int): Boolean {
        val key = hashXZ(x, z)
        waterCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()

        // 修正：使用 BiomeSource 进行噪声采样，不加载区块
        // 注意：getNoiseBiome 需要夸脱坐标 (x >> 2, y >> 2, z >> 2)
        val chunkSource = level.chunkSource
        val randomState = chunkSource.generatorState.randomState()
        val biomeSource = chunkSource.generator.biomeSource

        // 采样 Y=64 处的生物群系（海平面附近）
        val biome = biomeSource.getNoiseBiome(x shr 2, 16, z shr 2, randomState.sampler())

        val res = biome.`is`(BiomeTags.IS_RIVER) || biome.`is`(BiomeTags.IS_OCEAN) || biome.`is`(BiomeTags.IS_DEEP_OCEAN)
        waterCache[key] = res
        return res
    }

    fun oceanFloor(level: ServerLevel, x: Int, z: Int): Int {
        val key = hashXZ(x, z)
        oceanFloorCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()
        val generator = level.chunkSource.generator
        val rs = level.chunkSource.generatorState.randomState()
        // 修正：使用对应的 Heightmap 类型进行噪声采样
        val h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs)
        oceanFloorCache[key] = h
        return h
    }

    fun isNearWaterLike(level: ServerLevel, x: Int, z: Int, neighborDistance: Int): Boolean {
        val key = hashXZ(x, z)
        nearWaterCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()
        val d = neighborDistance
        val neighborOffsets = arrayOf(
            intArrayOf(d, 0), intArrayOf(-d, 0), intArrayOf(0, d), intArrayOf(0, -d),
            intArrayOf(d, d), intArrayOf(d, -d), intArrayOf(-d, d), intArrayOf(-d, -d)
        )
        for (off in neighborOffsets) {
            val nx = x + off[0]
            val nz = z + off[1]
            if (isWaterLike(level, nx, nz)) {
                nearWaterCache[key] = true
                return true
            }
        }
        nearWaterCache[key] = false
        return false
    }

    fun isColumnWater(level: ServerLevel, x: Int, z: Int): Boolean {
        val key = hashXZ(x, z)
        columnWaterCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()

        // 使用多种方式检测水体，解决以下问题：
        // 1. 浅滩(beach)不在 IS_RIVER/IS_OCEAN 群系标签中，但实际有水
        // 2. 群系边界处噪声采样可能判断失误，导致跨海不建桥

        val of = oceanFloor(level, x, z)  // OCEAN_FLOOR_WG：海底/河床高度
        val h = height(level, x, z)        // MOTION_BLOCKING_NO_LEAVES：表面高度
        val sea = level.seaLevel

        // 方法1：群系判断（原有逻辑）
        // 适用于标准的河流/海洋群系
        val isWaterBiome = isWaterLike(level, x, z)
        val biomeWater = isWaterBiome && of < sea

        // 方法2：高度差判断（新增逻辑）
        // 核心原理：如果表面高度接近海平面，但海底明显更低，说明中间是水
        // - h <= sea + 1：表面在海平面或略高（水面通常在 seaLevel）
        // - of < h - 1：海底比表面低至少2格，说明有水深
        // 这样可以检测到：浅滩、沼泽边缘、甚至非标准群系中的水体
        val heightWater = (h <= sea + 1) && (of < h - 1)

        // 任一方法检测到水体即可
        val res = biomeWater || heightWater

        columnWaterCache[key] = res
        return res
    }

    fun getBiome(level: ServerLevel, x: Int, z: Int): Holder<Biome> {
        val key = hashXZ(x, z)
        biomeCache[key]?.let {
            TerrainSamplingStats.recordCacheHit()
            return it
        }
        TerrainSamplingStats.recordCacheMiss()
        val chunkSource = level.chunkSource
        val randomState = chunkSource.generatorState.randomState()
        val biomeSource = chunkSource.generator.biomeSource
        val biome = biomeSource.getNoiseBiome(x shr 2, 16, z shr 2, randomState.sampler())
        biomeCache[key] = biome
        return biome
    }

    fun clear() {
        waterCache.clear()
        nearWaterCache.clear()
        columnWaterCache.clear()
        heightCache.clear()
        oceanFloorCache.clear()
        biomeCache.clear()
    }
}
