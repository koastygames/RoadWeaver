package net.shiroha233.roadweaver.persistence.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;

/**
 * NeoForge 绔笘鐣屾暟鎹彁渚涜€呭疄鐜帮紝浣跨敤 SavedData 鍦?ServerLevel 鎸佷箙鍖栧瓨鍌ㄣ€?
 */
public class NeoForgeWorldDataProvider extends WorldDataProvider {

    private static final String DATA_NAME = "roadweaver_world_data";

    /**
     * 瀹為檯鎸佷箙鍖栫殑鏁版嵁瀹瑰櫒銆?
     * 淇濆瓨缁撴瀯浣嶇疆銆佺粨鏋勮繛鎺ャ€?
     */
    public static class Data extends SavedData {
        private StructureLocationData structureLocations = new StructureLocationData(new ArrayList<>());
        private List<StructureConnection> connections = new ArrayList<>();
        private List<StructureConnection> highwayConnections = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        // NBT 瀛楁鍚?
        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_HIGHWAY_CONNECTIONS = "highway_connections";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";

        public Data() {}

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            // 缁撴瀯浣嶇疆锛堜粠 CompoundTag 璇诲彇锛?
            if (tag.contains(KEY_LOCATIONS)) {
                Tag locTag = tag.get(KEY_LOCATIONS);
                DataResult<StructureLocationData> res = StructureLocationData.CODEC.parse(new Dynamic<>(ops, locTag));
                res.result().ifPresent(val -> data.structureLocations = val);
            }

            // 缁撴瀯杩炴帴锛堜粠 ListTag 璇诲彇锛?
            if (tag.contains(KEY_CONNECTIONS)) {
                Tag conTag = tag.get(KEY_CONNECTIONS);
                DataResult<List<StructureConnection>> res = Codec.list(StructureConnection.CODEC).parse(new Dynamic<>(ops, conTag));
                res.result().ifPresent(val -> data.connections = val);
            }

            // Highway 缁撴瀯杩炴帴锛堜粠 ListTag 璇诲彇锛?
            if (tag.contains(KEY_HIGHWAY_CONNECTIONS)) {
                Tag conTag = tag.get(KEY_HIGHWAY_CONNECTIONS);
                DataResult<List<StructureConnection>> res = Codec.list(StructureConnection.CODEC).parse(new Dynamic<>(ops, conTag));
                res.result().ifPresent(val -> data.highwayConnections = val);
            }

            if (tag.contains(KEY_PLANNED_TILES)) {
                Tag t = tag.get(KEY_PLANNED_TILES);
                DataResult<List<Long>> res = Codec.list(Codec.LONG).parse(new Dynamic<>(ops, t));
                res.result().ifPresent(list -> data.plannedTileKeys = new HashSet<>(list));
            }

            if (tag.contains(KEY_PLANNED_TILE_CENTERS)) {
                Tag t = tag.get(KEY_PLANNED_TILE_CENTERS);
                DataResult<Map<Long, Long>> res = Codec.unboundedMap(Codec.LONG, Codec.LONG).parse(new Dynamic<>(ops, t));
                res.result().ifPresent(map -> data.plannedTileCenters = map);
            }

            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            Objects.requireNonNull(tag);
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            // 缁撴瀯浣嶇疆锛圧ecord 缂栫爜涓?CompoundTag锛?
            StructureLocationData.CODEC.encodeStart(ops, structureLocations)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_LOCATIONS, Objects.requireNonNull(nbt)));

            // 缁撴瀯杩炴帴锛圠ist 缂栫爜涓?ListTag锛?
            Codec.list(StructureConnection.CODEC).encodeStart(ops, connections)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_CONNECTIONS, Objects.requireNonNull(nbt)));

            // Highway 缁撴瀯杩炴帴锛圠ist 缂栫爜涓?ListTag锛?
            Codec.list(StructureConnection.CODEC).encodeStart(ops, highwayConnections)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_HIGHWAY_CONNECTIONS, Objects.requireNonNull(nbt)));

            Codec.list(Codec.LONG).encodeStart(ops, new java.util.ArrayList<>(plannedTileKeys))
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILES, Objects.requireNonNull(nbt)));

            Codec.unboundedMap(Codec.LONG, Codec.LONG).encodeStart(ops, plannedTileCenters)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILE_CENTERS, Objects.requireNonNull(nbt)));

            return tag;
        }

        // getters/setters
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
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, DataFixTypes.LEVEL), DATA_NAME);
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

