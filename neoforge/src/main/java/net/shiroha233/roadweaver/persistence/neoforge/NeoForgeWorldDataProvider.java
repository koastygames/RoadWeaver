package net.shiroha233.roadweaver.persistence.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NeoForgeWorldDataProvider extends WorldDataProvider {
    private static final String DATA_NAME = "roadweaver_world_data";
    private static final SavedDataType<Data> TYPE = new SavedDataType<>(
            DATA_NAME,
            level -> new Data(),
            Data::makeCodec
    );

    public static class Data extends SavedData {
        private record PlannedTileCenterEntry(long tileKey, long centerKey) {
            private static final Codec<PlannedTileCenterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("tile_key").forGetter(PlannedTileCenterEntry::tileKey),
                    Codec.LONG.fieldOf("center_key").forGetter(PlannedTileCenterEntry::centerKey)
            ).apply(instance, PlannedTileCenterEntry::new));
        }

        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_HIGHWAY_CONNECTIONS = "highway_connections";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";
        private static final Codec<Set<Long>> LONG_SET_CODEC = Codec.list(Codec.LONG).xmap(HashSet::new, ArrayList::new);
        private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StructureLocationData.CODEC.optionalFieldOf(KEY_LOCATIONS, new StructureLocationData(List.of())).forGetter(Data::getStructureLocations),
                Codec.list(StructureConnection.CODEC).optionalFieldOf(KEY_CONNECTIONS, List.of()).forGetter(Data::getConnections),
                Codec.list(StructureConnection.CODEC).optionalFieldOf(KEY_HIGHWAY_CONNECTIONS, List.of()).forGetter(Data::getHighwayConnections),
                LONG_SET_CODEC.optionalFieldOf(KEY_PLANNED_TILES, Set.of()).forGetter(Data::getPlannedTileKeys),
                Codec.list(PlannedTileCenterEntry.CODEC).optionalFieldOf(KEY_PLANNED_TILE_CENTERS, List.of()).forGetter(Data::getPlannedTileCenterEntries)
        ).apply(instance, Data::fromSerialized));

        private StructureLocationData structureLocations = new StructureLocationData(new ArrayList<>());
        private List<StructureConnection> connections = new ArrayList<>();
        private List<StructureConnection> highwayConnections = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        private static Codec<Data> makeCodec(ServerLevel level) {
            return CODEC;
        }

        private static Data fromSerialized(
                StructureLocationData structureLocations,
                List<StructureConnection> connections,
                List<StructureConnection> highwayConnections,
                Set<Long> plannedTileKeys,
                List<PlannedTileCenterEntry> plannedTileCenters
        ) {
            Data data = new Data();
            data.structureLocations = structureLocations != null ? structureLocations : new StructureLocationData(List.of());
            data.connections = new ArrayList<>(connections != null ? connections : List.of());
            data.highwayConnections = new ArrayList<>(highwayConnections != null ? highwayConnections : List.of());
            data.plannedTileKeys = new HashSet<>(plannedTileKeys != null ? plannedTileKeys : Set.of());
            if (plannedTileCenters != null) {
                for (PlannedTileCenterEntry entry : plannedTileCenters) {
                    data.plannedTileCenters.put(entry.tileKey(), entry.centerKey());
                }
            }
            return data;
        }

        private List<PlannedTileCenterEntry> getPlannedTileCenterEntries() {
            List<PlannedTileCenterEntry> entries = new ArrayList<>(plannedTileCenters.size());
            for (Map.Entry<Long, Long> entry : plannedTileCenters.entrySet()) {
                entries.add(new PlannedTileCenterEntry(entry.getKey(), entry.getValue()));
            }
            return entries;
        }

        public StructureLocationData getStructureLocations() {
            return structureLocations;
        }

        public void setStructureLocations(StructureLocationData data) {
            this.structureLocations = Objects.requireNonNullElseGet(data, () -> new StructureLocationData(new ArrayList<>()));
            setDirty();
        }

        public List<StructureConnection> getConnections() {
            return connections;
        }

        public void setConnections(List<StructureConnection> connections) {
            this.connections = Objects.requireNonNullElseGet(connections, ArrayList::new);
            setDirty();
        }

        public List<StructureConnection> getHighwayConnections() {
            return highwayConnections;
        }

        public void setHighwayConnections(List<StructureConnection> connections) {
            this.highwayConnections = Objects.requireNonNullElseGet(connections, ArrayList::new);
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

    private Data getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        return getOrCreate(level).getStructureLocations();
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        getOrCreate(level).setStructureLocations(data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        return getOrCreate(level).getConnections();
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        getOrCreate(level).setConnections(connections);
    }

    @Override
    public List<StructureConnection> getHighwayConnections(ServerLevel level) {
        return getOrCreate(level).getHighwayConnections();
    }

    @Override
    public void setHighwayConnections(ServerLevel level, List<StructureConnection> connections) {
        getOrCreate(level).setHighwayConnections(connections);
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
