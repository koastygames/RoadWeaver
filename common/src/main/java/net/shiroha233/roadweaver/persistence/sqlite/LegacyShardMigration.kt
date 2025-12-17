package net.shiroha233.roadweaver.persistence.sqlite

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import net.shiroha233.roadweaver.helpers.Records
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 旧分片 NBT 数据迁移服务
 */
object LegacyShardMigration {
    private val LOGGER = LoggerFactory.getLogger("roadweaver")
    private val OPS: DynamicOps<Tag> = NbtOps.INSTANCE

    private const val MIGRATION_MARKER = ".migrated_from_nbt"

    private val MIGRATED_DIMS: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun legacyBasePath(level: ServerLevel): Path {
        val rl: ResourceLocation = level.dimension().location()
        val dimKey = rl.namespace + "/" + rl.path
        val worldRoot = level.server.getWorldPath(LevelResource.ROOT)
        return worldRoot.resolve("data/roadweaver/roads").resolve(dimKey)
    }

    private fun migrationMarkerPath(level: ServerLevel): Path {
        val worldRoot = level.server.getWorldPath(LevelResource.ROOT)
        return worldRoot.resolve("data/roadweaver").resolve(dimKeyForDb(level)).resolve(MIGRATION_MARKER)
    }

    private fun dimKeyForDb(level: ServerLevel): String {
        val rl = level.dimension().location()
        return rl.namespace + "_" + rl.path
    }

    private fun dimCacheKey(level: ServerLevel): String {
        val worldRoot = level.server.getWorldPath(LevelResource.ROOT)
        val worldId = worldRoot?.toAbsolutePath()?.normalize()?.toString() ?: "unknown"
        return worldId + "|" + dimKeyForDb(level)
    }

    @JvmStatic
    fun migrateIfNeeded(level: ServerLevel): Int {
        val cacheKey = dimCacheKey(level)
        if (MIGRATED_DIMS.contains(cacheKey)) {
            return 0
        }

        val markerPath = migrationMarkerPath(level)
        if (Files.exists(markerPath)) {
            MIGRATED_DIMS.add(cacheKey)
            return 0
        }

        val legacyDir = legacyBasePath(level)
        if (!Files.exists(legacyDir) || !Files.isDirectory(legacyDir)) {
            MIGRATED_DIMS.add(cacheKey)
            return 0
        }

        if (!hasLegacyShardFiles(legacyDir)) {
            MIGRATED_DIMS.add(cacheKey)
            return 0
        }

        val migrated = performMigration(level, legacyDir)

        createMigrationMarker(markerPath)
        MIGRATED_DIMS.add(cacheKey)

        return migrated
    }

    private fun performMigration(level: ServerLevel, legacyDir: Path): Int {
        LOGGER.info("LegacyShardMigration: 开始迁移旧道路数据 - 维度: {}", level.dimension().location())
        notifyPlayers(level, Component.translatable("message.roadweaver.migration.start"))

        var totalMigrated = 0
        var fileCount = 0
        val seenFingerprints = HashSet<Long>()

        try {
            Files.newDirectoryStream(legacyDir, "r.*.*.nbt").use { stream ->
                for (shardFile in stream) {
                    fileCount++
                    val roads = loadShardFile(shardFile)
                    for (rd in roads) {
                        val segs = rd.roadSegmentList
                        if (segs.isEmpty()) continue

                        val fp = fingerprint(rd)
                        if (seenFingerprints.add(fp)) {
                            RoadSqliteStorage.addRoad(level, rd)
                            totalMigrated++
                        }
                    }
                }
            }
        } catch (e: IOException) {
            LOGGER.error("LegacyShardMigration: 读取旧数据目录失败", e)
        }

        LOGGER.info(
            "LegacyShardMigration: 迁移完成 - 维度: {}, 文件数: {}, 道路数: {}",
            level.dimension().location(),
            fileCount,
            totalMigrated
        )
        notifyPlayers(level, Component.translatable("message.roadweaver.migration.done", totalMigrated))

        return totalMigrated
    }

    private fun hasLegacyShardFiles(legacyDir: Path): Boolean {
        return try {
            Files.newDirectoryStream(legacyDir, "r.*.*.nbt").use { stream ->
                stream.iterator().hasNext()
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun notifyPlayers(level: ServerLevel?, message: Component) {
        if (level == null || level.server == null) return
        level.server.execute {
            try {
                level.server.playerList.players.forEach { p -> p.sendSystemMessage(message) }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadShardFile(shardFile: Path): List<Records.RoadData> {
        val roads = ArrayList<Records.RoadData>()

        try {
            Files.newInputStream(shardFile).use { is0 ->
                GZIPInputStream(is0).use { gzip ->
                    DataInputStream(gzip).use { dis ->
                        val tag = NbtIo.read(dis)
                        if (tag != null && tag.contains("roads")) {
                            val list = tag.get("roads")
                            val res: DataResult<List<Records.RoadData>> = Codec.list(Records.RoadData.CODEC)
                                .parse(Dynamic(OPS, list))
                            res.result().ifPresent { roads.addAll(it) }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            LOGGER.warn("LegacyShardMigration: 读取分片文件失败: {}", shardFile, e)
        }

        return roads
    }

    private fun fingerprint(rd: Records.RoadData): Long {
        val segs = rd.roadSegmentList
        if (segs.isEmpty()) return 0L

        val a = segs[0].middlePos
        val b = segs[segs.size - 1].middlePos
        val ka = (a.x.toLong() shl 32) xor (a.z.toLong() and 0xffffffffL)
        val kb = (b.x.toLong() shl 32) xor (b.z.toLong() and 0xffffffffL)
        val lo = min(ka, kb)
        val hi = max(ka, kb)
        var f = (hi shl 1) xor lo
        f = f xor (rd.width.toLong() and 0xffffffffL)
        f = f xor ((rd.roadType.toLong() and 0xffffffffL) shl 33)
        return f
    }

    private fun createMigrationMarker(markerPath: Path) {
        try {
            Files.createDirectories(markerPath.parent)
            Files.writeString(
                markerPath,
                "# RoadWeaver 数据迁移标记\n" +
                    "# 此文件表示旧分片 NBT 数据已迁移到 SQLite\n" +
                    "# 删除此文件将导致下次启动时重新迁移（不会产生重复数据）\n" +
                    "migrated_at=" + System.currentTimeMillis() + "\n"
            )
        } catch (e: IOException) {
            LOGGER.warn("LegacyShardMigration: 创建迁移标记失败", e)
        }
    }

    @JvmStatic
    fun reset() {
        MIGRATED_DIMS.clear()
    }
}
