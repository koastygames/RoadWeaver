package net.shiroha233.roadweaver.persistence.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;

/**
 * Forge 端世界数据提供者实现，使用 SavedData 在 ServerLevel 持久化存储。
 */
public class ForgeWorldDataProvider extends WorldDataProvider {

    private static final String DATA_NAME = "roadweaver_world_data";

    /**
     * 实际持久化的数据容器。
     * 保存结构位置、结构连接。
     */
    public static class Data extends SavedData {
        private static final Codec<java.util.Set<Long>> LONG_SET_CODEC = Codec.list(Codec.LONG)
                .xmap(list -> new java.util.HashSet<>(list), set -> new java.util.ArrayList<>(set));

        private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Records.StructureLocationData.CODEC.optionalFieldOf("structureLocations", new Records.StructureLocationData(new ArrayList<>()))
                        .forGetter(d -> d.structureLocations),
                Codec.list(Records.StructureConnection.CODEC).optionalFieldOf("connections", java.util.List.of())
                        .forGetter(d -> d.connections),
                LONG_SET_CODEC.optionalFieldOf("plannedTileKeys", new java.util.HashSet<>())
                        .forGetter(d -> d.plannedTileKeys),
                Codec.unboundedMap(Codec.LONG, Codec.LONG).optionalFieldOf("plannedTileCenters", new java.util.HashMap<>())
                        .forGetter(d -> d.plannedTileCenters)
        ).apply(instance, (locs, conns, keys, centers) -> {
            Data d = new Data();
            d.structureLocations = locs;
            d.connections = new ArrayList<>(conns);
            d.plannedTileKeys = keys;
            d.plannedTileCenters = centers;
            return d;
        }));

        private Records.StructureLocationData structureLocations = new Records.StructureLocationData(new ArrayList<>());
        private List<Records.StructureConnection> connections = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        // NBT 字段名
        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";

        public Data() {}

        // getters/setters
        public Records.StructureLocationData getStructureLocations() {
            return structureLocations;
        }

        public void setStructureLocations(Records.StructureLocationData data) {
            this.structureLocations = Objects.requireNonNullElseGet(data, () -> new Records.StructureLocationData(new ArrayList<>()));
            setDirty();
        }

        public List<Records.StructureConnection> getConnections() {
            return connections;
        }

        public void setConnections(List<Records.StructureConnection> connections) {
            this.connections = Objects.requireNonNullElseGet(connections, ArrayList::new);
            setDirty();
        }

        public Set<Long> getPlannedTileKeys() {
            return plannedTileKeys;
        }

        public void setPlannedTileKeys(Set<Long> keys) {
            this.plannedTileKeys = Objects.requireNonNullElseGet(keys, HashSet::new);
            setDirty();
        }

        public Map<Long, Long> getPlannedTileCenters() {
            return plannedTileCenters;
        }

        public void setPlannedTileCenters(Map<Long, Long> centers) {
            this.plannedTileCenters = Objects.requireNonNullElseGet(centers, HashMap::new);
            setDirty();
        }
    }

    private static final SavedDataType<Data> TYPE = new SavedDataType<>(
            DATA_NAME,
            (SavedData.Context ctx) -> new Data(),
            (SavedData.Context ctx) -> Data.CODEC,
            DataFixTypes.LEVEL
    );

    private Data getOrCreate(ServerLevel level) {
        return (Data) level.getDataStorage().computeIfAbsent(TYPE);
    }

    @Override
    public Records.StructureLocationData getStructureLocations(ServerLevel level) {
        return getOrCreate(level).getStructureLocations();
    }

    @Override
    public void setStructureLocations(ServerLevel level, Records.StructureLocationData data) {
        getOrCreate(level).setStructureLocations(data);
    }

    @Override
    public List<Records.StructureConnection> getStructureConnections(ServerLevel level) {
        return getOrCreate(level).getConnections();
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<Records.StructureConnection> connections) {
        getOrCreate(level).setConnections(connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        return getOrCreate(level).getPlannedTileKeys();
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        getOrCreate(level).setPlannedTileKeys(keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return getOrCreate(level).getPlannedTileCenters();
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        getOrCreate(level).setPlannedTileCenters(centers);
    }
}