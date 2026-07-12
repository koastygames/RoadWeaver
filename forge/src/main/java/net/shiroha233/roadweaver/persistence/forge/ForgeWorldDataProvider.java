package net.shiroha233.roadweaver.persistence.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.*;

/**
 * Forge 平台世界数据提供者实现
 */
public class ForgeWorldDataProvider extends WorldDataProvider {

    private static final String DATA_NAME = "roadweaver_world_data";

    /**
     * 实际持久化的数据容器
     */
    public static class Data extends SavedData {
        private StructureLocationData structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        private List<StructureConnection> connections = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";
        private static final Codec<Map<Long, Long>> LONG_KEY_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
                map -> {
                    HashMap<Long, Long> out = new HashMap<>();
                    for (Map.Entry<String, Long> entry : map.entrySet()) {
                        try {
                            out.put(Long.parseLong(entry.getKey()), entry.getValue());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    return out;
                },
                map -> {
                    HashMap<String, Long> out = new HashMap<>();
                    for (Map.Entry<Long, Long> entry : map.entrySet()) {
                        out.put(Long.toString(entry.getKey()), entry.getValue());
                    }
                    return out;
                });

        public Data() {}

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            if (tag.contains(KEY_LOCATIONS)) {
                Tag locTag = tag.get(KEY_LOCATIONS);
                DataResult<StructureLocationData> res = StructureLocationData.CODEC.parse(new Dynamic<>(ops, locTag));
                res.result().ifPresent(val -> data.structureLocations = val);
            }

            if (tag.contains(KEY_CONNECTIONS)) {
                Tag conTag = tag.get(KEY_CONNECTIONS);
                DataResult<List<StructureConnection>> res = Codec.list(StructureConnection.CODEC).parse(new Dynamic<>(ops, conTag));
                res.result().ifPresent(val -> data.connections = val);
            }

            if (tag.contains(KEY_PLANNED_TILES)) {
                Tag t = tag.get(KEY_PLANNED_TILES);
                DataResult<List<Long>> res = Codec.list(Codec.LONG).parse(new Dynamic<>(ops, t));
                res.result().ifPresent(list -> data.plannedTileKeys = new HashSet<>(list));
            }

            if (tag.contains(KEY_PLANNED_TILE_CENTERS)) {
                Tag t = tag.get(KEY_PLANNED_TILE_CENTERS);
                DataResult<Map<Long, Long>> res = LONG_KEY_MAP_CODEC.parse(new Dynamic<>(ops, t));
                res.result().ifPresent(map -> data.plannedTileCenters = map);
            }

            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            Objects.requireNonNull(tag);
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            StructureLocationData.CODEC.encodeStart(ops, structureLocations)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_LOCATIONS, Objects.requireNonNull(nbt)));

            Codec.list(StructureConnection.CODEC).encodeStart(ops, connections)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_CONNECTIONS, Objects.requireNonNull(nbt)));

            Codec.list(Codec.LONG).encodeStart(ops, new ArrayList<>(plannedTileKeys))
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILES, Objects.requireNonNull(nbt)));

            LONG_KEY_MAP_CODEC.encodeStart(ops, plannedTileCenters)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILE_CENTERS, Objects.requireNonNull(nbt)));

            return tag;
        }

        public StructureLocationData getStructureLocations() {
            return new StructureLocationData(structureLocations.structureLocations(), structureLocations.structureInfos());
        }

        public void setStructureLocations(StructureLocationData data) {
            StructureLocationData source = Objects.requireNonNullElseGet(data, () -> new StructureLocationData(new ArrayList<>(), new ArrayList<>()));
            this.structureLocations = new StructureLocationData(source.structureLocations(), source.structureInfos());
            setDirty();
        }

        public List<StructureConnection> getConnections() {
            return new ArrayList<>(connections);
        }

        public void setConnections(List<StructureConnection> connections) {
            this.connections = new ArrayList<>(Objects.requireNonNullElseGet(connections, ArrayList::new));
            setDirty();
        }

        public Set<Long> getPlannedTileKeys() {
            return new HashSet<>(plannedTileKeys);
        }

        public void setPlannedTileKeys(Set<Long> keys) {
            this.plannedTileKeys = new HashSet<>(Objects.requireNonNullElseGet(keys, HashSet::new));
            setDirty();
        }

        public Map<Long, Long> getPlannedTileCenters() {
            return new HashMap<>(plannedTileCenters);
        }

        public void setPlannedTileCenters(Map<Long, Long> centers) {
            this.plannedTileCenters = new HashMap<>(Objects.requireNonNullElseGet(centers, HashMap::new));
            setDirty();
        }
    }

    private Data getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        StructureLocationData current = StructureFileStorage.getStructureLocations(level);
        if (hasStructureLocations(current)) return current;
        StructureLocationData legacy = getOrCreate(level).getStructureLocations();
        if (hasStructureLocations(legacy)) {
            StructureFileStorage.setStructureLocations(level, legacy);
            return StructureFileStorage.getStructureLocations(level);
        }
        return current;
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StructureFileStorage.setStructureLocations(level, data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        List<StructureConnection> current = StructureFileStorage.getStructureConnections(level);
        if (!current.isEmpty()) return current;
        List<StructureConnection> legacy = getOrCreate(level).getConnections();
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setStructureConnections(level, legacy);
            return StructureFileStorage.getStructureConnections(level);
        }
        return current;
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StructureFileStorage.setStructureConnections(level, connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        Set<Long> current = StructureFileStorage.getPlannedTileKeys(level);
        if (!current.isEmpty()) return current;
        Set<Long> legacy = getOrCreate(level).getPlannedTileKeys();
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileKeys(level, legacy);
            return StructureFileStorage.getPlannedTileKeys(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StructureFileStorage.setPlannedTileKeys(level, keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Map<Long, Long> current = StructureFileStorage.getPlannedTileCenters(level);
        if (!current.isEmpty()) return current;
        Map<Long, Long> legacy = getOrCreate(level).getPlannedTileCenters();
        if (legacy != null && !legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileCenters(level, legacy);
            return StructureFileStorage.getPlannedTileCenters(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StructureFileStorage.setPlannedTileCenters(level, centers);
    }

    private static boolean hasStructureLocations(StructureLocationData data) {
        return data != null
                && ((!data.structureLocations().isEmpty()) || (!data.structureInfos().isEmpty()));
    }
}
