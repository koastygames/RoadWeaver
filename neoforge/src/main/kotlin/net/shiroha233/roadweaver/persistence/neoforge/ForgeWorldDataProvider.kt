package net.shiroha233.roadweaver.persistence.neoforge

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import java.util.Objects

/**
 * NeoForge 端世界数据提供者实现，使用 SavedData 在 ServerLevel 持久化存储。
 */
@Suppress("MemberVisibilityCanBePrivate")
class ForgeWorldDataProvider : WorldDataProvider() {
    private companion object {
        private const val DATA_NAME = "roadweaver_world_data"

        // NBT 字段名
        private const val KEY_LOCATIONS = "structure_locations"
        private const val KEY_CONNECTIONS = "connections"
        private const val KEY_PLANNED_TILES = "planned_tiles"
        private const val KEY_PLANNED_TILE_CENTERS = "planned_tile_centers"

        private val FACTORY: SavedData.Factory<Data> = SavedData.Factory({ Data() }, Data::load, DataFixTypes.LEVEL)
    }

    /**
     * 实际持久化的数据容器。
     * 保存结构位置、结构连接。
     */
    class Data : SavedData() {
        private var structureLocations: Records.StructureLocationData = Records.StructureLocationData(ArrayList())
        private var connections: List<Records.StructureConnection> = ArrayList()
        private var plannedTileKeys: Set<Long> = HashSet()
        private var plannedTileCenters: Map<Long, Long> = HashMap()

        override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
            Objects.requireNonNull(tag)
            val ops: DynamicOps<Tag> = NbtOps.INSTANCE

            // 结构位置（Record 编码为 CompoundTag）
            Records.StructureLocationData.CODEC.encodeStart(ops, structureLocations)
                .result()
                .ifPresent { nbt -> tag.put(KEY_LOCATIONS, Objects.requireNonNull(nbt)) }

            // 结构连接（List 编码为 ListTag）
            Codec.list(Records.StructureConnection.CODEC).encodeStart(ops, connections)
                .result()
                .ifPresent { nbt -> tag.put(KEY_CONNECTIONS, Objects.requireNonNull(nbt)) }

            Codec.list(Codec.LONG).encodeStart(ops, ArrayList(plannedTileKeys))
                .result()
                .ifPresent { nbt -> tag.put(KEY_PLANNED_TILES, Objects.requireNonNull(nbt)) }

            Codec.unboundedMap(Codec.LONG, Codec.LONG).encodeStart(ops, plannedTileCenters)
                .result()
                .ifPresent { nbt -> tag.put(KEY_PLANNED_TILE_CENTERS, Objects.requireNonNull(nbt)) }

            return tag
        }

        fun getStructureLocations(): Records.StructureLocationData = structureLocations

        fun setStructureLocations(data: Records.StructureLocationData?) {
            structureLocations = data ?: Records.StructureLocationData(ArrayList())
            setDirty()
        }

        fun getConnections(): List<Records.StructureConnection> = connections

        fun setConnections(connections: List<Records.StructureConnection>?) {
            this.connections = connections ?: ArrayList()
            setDirty()
        }

        fun getPlannedTileKeys(): Set<Long> = plannedTileKeys

        fun setPlannedTileKeys(keys: Set<Long>?) {
            plannedTileKeys = keys ?: HashSet()
            setDirty()
        }

        fun getPlannedTileCenters(): Map<Long, Long> = plannedTileCenters

        fun setPlannedTileCenters(centers: Map<Long, Long>?) {
            plannedTileCenters = centers ?: HashMap()
            setDirty()
        }

        companion object {
            @JvmStatic
            fun load(tag: CompoundTag, provider: HolderLookup.Provider): Data {
                val data = Data()
                val ops: DynamicOps<Tag> = NbtOps.INSTANCE

                // 结构位置（从 CompoundTag 读取）
                if (tag.contains(KEY_LOCATIONS)) {
                    val locTag = tag.get(KEY_LOCATIONS)
                    val res: DataResult<Records.StructureLocationData> =
                        Records.StructureLocationData.CODEC.parse(Dynamic(ops, locTag))
                    res.result().ifPresent { value -> data.structureLocations = value }
                }

                // 结构连接（从 ListTag 读取）
                if (tag.contains(KEY_CONNECTIONS)) {
                    val conTag = tag.get(KEY_CONNECTIONS)
                    val res: DataResult<List<Records.StructureConnection>> =
                        Codec.list(Records.StructureConnection.CODEC).parse(Dynamic(ops, conTag))
                    res.result().ifPresent { value -> data.connections = value }
                }

                if (tag.contains(KEY_PLANNED_TILES)) {
                    val t = tag.get(KEY_PLANNED_TILES)
                    val res: DataResult<List<Long>> = Codec.list(Codec.LONG).parse(Dynamic(ops, t))
                    res.result().ifPresent { list -> data.plannedTileKeys = HashSet(list) }
                }

                if (tag.contains(KEY_PLANNED_TILE_CENTERS)) {
                    val t = tag.get(KEY_PLANNED_TILE_CENTERS)
                    val res: DataResult<Map<Long, Long>> =
                        Codec.unboundedMap(Codec.LONG, Codec.LONG).parse(Dynamic(ops, t))
                    res.result().ifPresent { map -> data.plannedTileCenters = map }
                }

                return data
            }
        }
    }

    private fun getOrCreate(level: ServerLevel): Data {
        return level.dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
    }

    override fun getStructureLocations(level: ServerLevel): Records.StructureLocationData {
        return getOrCreate(level).getStructureLocations()
    }

    override fun setStructureLocations(level: ServerLevel, data: Records.StructureLocationData) {
        getOrCreate(level).setStructureLocations(data)
    }

    override fun getStructureConnections(level: ServerLevel): List<Records.StructureConnection> {
        return getOrCreate(level).getConnections()
    }

    override fun setStructureConnections(level: ServerLevel, connections: List<Records.StructureConnection>) {
        getOrCreate(level).setConnections(connections)
    }

    override fun getPlannedTileKeys(level: ServerLevel): Set<Long> {
        return getOrCreate(level).getPlannedTileKeys()
    }

    override fun setPlannedTileKeys(level: ServerLevel, keys: Set<Long>) {
        getOrCreate(level).setPlannedTileKeys(keys)
    }

    override fun getPlannedTileCenters(level: ServerLevel): Map<Long, Long> {
        return getOrCreate(level).getPlannedTileCenters()
    }

    override fun setPlannedTileCenters(level: ServerLevel, centers: Map<Long, Long>) {
        getOrCreate(level).setPlannedTileCenters(centers)
    }
}
