/* 文件职责：持久化结构位置、来源和道路连接状态。 */
package net.shiroha233.roadweaver.persistence.files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结构文件存储：世界级 JSON 状态文件。
 */
public final class StructureFileStorage {
    private StructureFileStorage() {}

    public static final int SOURCE_PREDICTED = 0;
    public static final int SOURCE_MANUAL = 1;
    public static final int SCAN_TILE_SIZE_CHUNKS = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY = "structures";
    private static final String STATE_FILE = "state.json";

    private static final class StateData {
        StructureLocationData structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        List<StructureConnection> connections = new ArrayList<>();
        Set<Long> plannedTileKeys = new HashSet<>();
        Map<Long, Long> plannedTileCenters = new HashMap<>();
        Map<Long, Integer> structureSources = new HashMap<>();
        Map<String, String> meta = new HashMap<>();
        Map<Long, Long> scanTiles = new HashMap<>();
    }

    public record StructureSnapshot(StructureLocationData locations, Map<Long, Integer> sources) {
        public StructureSnapshot {
            locations = locations == null
                    ? new StructureLocationData(new ArrayList<>(), new ArrayList<>())
                    : new StructureLocationData(locations.structureLocations(), locations.structureInfos());
            sources = sources == null ? Map.of() : Map.copyOf(sources);
        }

        public int sourceAt(BlockPos pos) {
            return pos == null ? -1 : sources.getOrDefault(posKey(pos), SOURCE_PREDICTED);
        }
    }

    public static synchronized StructureLocationData getStructureLocations(ServerLevel level) {
        StructureLocationData data = state(level).structureLocations;
        return new StructureLocationData(data.structureLocations(), data.structureInfos());
    }

    public static synchronized void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StateData state = state(level);
        Map<Long, Integer> previousSources = new HashMap<>(state.structureSources);
        state.structureLocations = data != null ? normalizeStructureLocations(data) : new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        state.structureSources = rebuildSources(state.structureLocations, previousSources, SOURCE_MANUAL);
        save(level, state);
    }

    public static synchronized List<StructureConnection> getStructureConnections(ServerLevel level) {
        return new ArrayList<>(state(level).connections);
    }

    public static synchronized void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StateData state = state(level);
        state.connections = connections != null ? new ArrayList<>(connections) : new ArrayList<>();
        save(level, state);
    }

    public static synchronized void mergeStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        if (level == null || connections == null || connections.isEmpty()) return;
        StateData state = state(level);
        mergeConnections(state.connections, connections);
        save(level, state);
    }

    public static synchronized Set<Long> getPlannedTileKeys(ServerLevel level) {
        return new HashSet<>(state(level).plannedTileKeys);
    }

    public static synchronized void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StateData state = state(level);
        state.plannedTileKeys = keys != null ? new HashSet<>(keys) : new HashSet<>();
        save(level, state);
    }

    public static synchronized Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return new HashMap<>(state(level).plannedTileCenters);
    }

    public static synchronized void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StateData state = state(level);
        state.plannedTileCenters = centers != null ? new HashMap<>(centers) : new HashMap<>();
        save(level, state);
    }

    public static synchronized void addStructures(ServerLevel level, List<StructureInfo> infos, int source) {
        if (level == null || infos == null || infos.isEmpty()) return;
        StateData state = state(level);
        for (StructureInfo info : infos) {
            if (info == null || info.pos() == null) continue;
            putStructure(state, info, source);
        }
        save(level, state);
    }

    public static synchronized List<StructureInfo> queryRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, int... sources) {
        if (level == null) return List.of();
        Set<Integer> filter = normalizeSources(sources);
        List<StructureInfo> out = new ArrayList<>();
        StateData state = state(level);
        for (StructureInfo info : state.structureLocations.structureInfos()) {
            if (info == null || info.pos() == null) continue;
            int x = info.pos().getX();
            int z = info.pos().getZ();
            if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) continue;
            int source = state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED);
            if (filter.contains(source)) out.add(info);
        }
        return out;
    }

    public static synchronized StructureSnapshot getStructureSnapshot(ServerLevel level) {
        if (level == null) return new StructureSnapshot(null, Map.of());
        StateData state = state(level);
        return new StructureSnapshot(state.structureLocations, state.structureSources);
    }

    public static synchronized int sourceAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return -1;
        return getStructureSnapshot(level).sourceAt(pos);
    }

    public static synchronized void ensurePolicy(ServerLevel level, String policyHash) {
        StateData state = state(level);
        String hash = policyHash == null ? "" : policyHash;
        String current = state.meta.getOrDefault("policy_hash", "");
        if (!current.equals(hash)) {
            keepOnlyManualStructures(state);
            state.scanTiles.clear();
            state.meta.put("policy_hash", hash);
            save(level, state);
        }
    }

    public static synchronized boolean claimScanTile(ServerLevel level, int tileX, int tileZ) {
        StateData state = state(level);
        long now = System.currentTimeMillis() / 1000L;
        long key = chunkKey(tileX, tileZ);
        Long scannedAt = state.scanTiles.get(key);
        if (scannedAt != null) {
            if (scannedAt > 0) return false;
            long start = scannedAt < 0 ? -scannedAt : now - 10 * 60 - 1;
            if (now - start < 10 * 60) return false;
            state.scanTiles.put(key, -now);
            save(level, state);
            return true;
        }
        state.scanTiles.put(key, -now);
        save(level, state);
        return true;
    }

    public static synchronized void markScanTileDone(ServerLevel level, int tileX, int tileZ) {
        StateData state = state(level);
        state.scanTiles.put(chunkKey(tileX, tileZ), System.currentTimeMillis() / 1000L);
        save(level, state);
    }

    public static synchronized void releaseScanTile(ServerLevel level, int tileX, int tileZ) {
        StateData state = state(level);
        state.scanTiles.remove(chunkKey(tileX, tileZ));
        save(level, state);
    }

    public static synchronized boolean claimScanWindow(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return claimScanKey(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    public static synchronized void markScanWindowDone(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        markScanKeyDone(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    public static synchronized void releaseScanWindow(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        releaseScanKey(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    public static synchronized boolean hasAnyStructure(ServerLevel level) {
        return !state(level).structureLocations.structureInfos().isEmpty();
    }

    public static synchronized boolean hasAnyManualStructure(ServerLevel level) {
        StateData state = state(level);
        for (StructureInfo info : state.structureLocations.structureInfos()) {
            if (info != null && info.pos() != null && state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                return true;
            }
        }
        for (BlockPos pos : state.structureLocations.structureLocations()) {
            if (pos != null && state.structureSources.getOrDefault(posKey(pos), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                return true;
            }
        }
        return false;
    }

    public static synchronized Set<Long> manualStructureKeys(ServerLevel level) {
        StateData state = state(level);
        HashSet<Long> out = new HashSet<>();
        for (StructureInfo info : state.structureLocations.structureInfos()) {
            if (info != null && info.pos() != null && state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                out.add(posKey(info.pos()));
            }
        }
        for (BlockPos pos : state.structureLocations.structureLocations()) {
            if (pos != null && state.structureSources.getOrDefault(posKey(pos), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                out.add(posKey(pos));
            }
        }
        return out;
    }

    private static boolean samePos(BlockPos pos, int x, int z) {
        return pos != null && pos.getX() == x && pos.getZ() == z;
    }

    private static long posKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static int normalizeSource(int source) {
        return source == SOURCE_MANUAL ? SOURCE_MANUAL : SOURCE_PREDICTED;
    }

    private static boolean claimScanKey(ServerLevel level, long key) {
        StateData state = state(level);
        long now = System.currentTimeMillis() / 1000L;
        Long scannedAt = state.scanTiles.get(key);
        if (scannedAt != null) {
            if (scannedAt > 0) return false;
            long start = scannedAt < 0 ? -scannedAt : now - 10 * 60 - 1;
            if (now - start < 10 * 60) return false;
            state.scanTiles.put(key, -now);
            save(level, state);
            return true;
        }
        state.scanTiles.put(key, -now);
        save(level, state);
        return true;
    }

    private static void markScanKeyDone(ServerLevel level, long key) {
        StateData state = state(level);
        state.scanTiles.put(key, System.currentTimeMillis() / 1000L);
        save(level, state);
    }

    private static void releaseScanKey(ServerLevel level, long key) {
        StateData state = state(level);
        state.scanTiles.remove(key);
        save(level, state);
    }

    private static long scanWindowKey(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        long hash = 0xcbf29ce484222325L;
        hash = mixScanHash(hash, minChunkX);
        hash = mixScanHash(hash, minChunkZ);
        hash = mixScanHash(hash, maxChunkX);
        hash = mixScanHash(hash, maxChunkZ);
        return hash;
    }

    private static long mixScanHash(long hash, int value) {
        hash ^= value;
        hash *= 0x100000001b3L;
        return hash;
    }

    private static Set<Integer> normalizeSources(int[] sources) {
        if (sources == null || sources.length == 0) {
            return Set.of(SOURCE_PREDICTED, SOURCE_MANUAL);
        }
        Set<Integer> out = new HashSet<>();
        for (int source : sources) out.add(normalizeSource(source));
        return out;
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static StateData state(ServerLevel level) {
        StateData fileState = readFileState(level);
        return normalize(fileState != null ? fileState : new StateData());
    }

    private static StateData readFileState(ServerLevel level) {
        try {
            Path path = statePath(level);
            if (!Files.exists(path)) return null;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                StateData data = GSON.fromJson(reader, StateData.class);
                return data != null ? data : null;
            }
        } catch (Exception e) {
            FileStorageIO.quarantineCorrupt(statePath(level), LOGGER, "结构状态文件损坏，已隔离");
            return null;
        }
    }

    private static StateData normalize(StateData state) {
        if (state.structureLocations == null) {
            state.structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        } else {
            state.structureLocations = normalizeStructureLocations(state.structureLocations);
        }
        if (state.connections == null) state.connections = new ArrayList<>();
        if (state.plannedTileKeys == null) state.plannedTileKeys = new HashSet<>();
        if (state.plannedTileCenters == null) state.plannedTileCenters = new HashMap<>();
        if (state.structureSources == null) state.structureSources = new HashMap<>();
        if (state.meta == null) state.meta = new HashMap<>();
        if (state.scanTiles == null) state.scanTiles = new HashMap<>();
        if (state.structureSources.isEmpty()) {
            state.structureSources = rebuildSources(state.structureLocations, Map.of(), SOURCE_PREDICTED);
        }
        return state;
    }

    private static StructureLocationData normalizeStructureLocations(StructureLocationData data) {
        StructureLocationData normalized = new StructureLocationData(data.structureLocations(), data.structureInfos());
        for (BlockPos pos : normalized.structureLocations()) {
            if (pos == null) continue;
            boolean hasInfo = false;
            for (StructureInfo info : normalized.structureInfos()) {
                if (info != null && samePos(info.pos(), pos.getX(), pos.getZ())) {
                    hasInfo = true;
                    break;
                }
            }
            if (!hasInfo) {
                normalized.structureInfos().add(new StructureInfo(pos, "unknown"));
            }
        }
        return normalized;
    }

    private static Map<Long, Integer> rebuildSources(StructureLocationData data, Map<Long, Integer> previous, int defaultSource) {
        HashMap<Long, Integer> out = new HashMap<>();
        if (data != null) {
            for (StructureInfo info : data.structureInfos()) {
                if (info == null || info.pos() == null) continue;
                long key = posKey(info.pos());
                out.put(key, previous.getOrDefault(key, normalizeSource(defaultSource)));
            }
            for (BlockPos pos : data.structureLocations()) {
                if (pos == null) continue;
                long key = posKey(pos);
                out.putIfAbsent(key, previous.getOrDefault(key, normalizeSource(defaultSource)));
            }
        }
        return out;
    }

    private static void putStructure(StateData state, StructureInfo info, int source) {
        int x = info.pos().getX();
        int z = info.pos().getZ();
        long key = posKey(info.pos());
        Integer existingSource = state.structureSources.get(key);
        StructureInfo existingInfo = findStructureInfo(state.structureLocations.structureInfos(), x, z);
        boolean incomingKnown = StructureInfo.isKnownId(info.structureId());
        boolean existingKnown = existingInfo != null && StructureInfo.isKnownId(existingInfo.structureId());
        if (source == SOURCE_PREDICTED && existingSource != null && existingSource == SOURCE_MANUAL) {
            if (!incomingKnown || existingKnown) return;
            source = SOURCE_MANUAL;
        } else if (!incomingKnown && existingKnown) {
            if (source == SOURCE_MANUAL) state.structureSources.put(key, SOURCE_MANUAL);
            return;
        }
        state.structureLocations.structureInfos().removeIf(existing -> existing != null && samePos(existing.pos(), x, z));
        state.structureLocations.structureLocations().removeIf(pos -> samePos(pos, x, z));
        state.structureLocations.structureInfos().add(info);
        state.structureLocations.structureLocations().add(info.pos());
        state.structureSources.put(key, normalizeSource(source));
    }

    private static StructureInfo findStructureInfo(List<StructureInfo> infos, int x, int z) {
        for (StructureInfo info : infos) {
            if (info != null && samePos(info.pos(), x, z)) return info;
        }
        return null;
    }

    private static void keepOnlyManualStructures(StateData state) {
        ArrayList<StructureInfo> manualInfos = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (StructureInfo info : state.structureLocations.structureInfos()) {
            if (info == null || info.pos() == null) continue;
            long key = posKey(info.pos());
            if (state.structureSources.getOrDefault(key, SOURCE_PREDICTED) == SOURCE_MANUAL && seen.add(key)) {
                manualInfos.add(info);
            }
        }
        for (BlockPos pos : state.structureLocations.structureLocations()) {
            if (pos == null) continue;
            long key = posKey(pos);
            if (state.structureSources.getOrDefault(key, SOURCE_PREDICTED) == SOURCE_MANUAL && seen.add(key)) {
                manualInfos.add(new StructureInfo(pos, "unknown"));
            }
        }

        state.structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        state.structureSources.clear();
        for (StructureInfo info : manualInfos) {
            putStructure(state, info, SOURCE_MANUAL);
        }
    }

    private static void mergeConnections(List<StructureConnection> target, List<StructureConnection> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        LinkedHashMap<PlannedPathKey, StructureConnection> merged = new LinkedHashMap<>();
        for (StructureConnection connection : target) {
            if (validConnection(connection)) merged.put(PlannedPathKey.of(connection), connection);
        }
        for (StructureConnection connection : incoming) {
            if (!validConnection(connection)) continue;
            PlannedPathKey key = PlannedPathKey.of(connection);
            StructureConnection previous = merged.get(key);
            if (previous == null || statusPriority(connection.status()) >= statusPriority(previous.status())) {
                merged.put(key, connection);
            }
        }
        target.clear();
        target.addAll(merged.values());
    }

    private static boolean validConnection(StructureConnection connection) {
        return connection != null && connection.from() != null && connection.to() != null;
    }

    private static int statusPriority(ConnectionStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case COMPLETED -> 4;
            case GENERATING -> 3;
            case PLANNED -> 2;
            case FAILED -> 1;
        };
    }

    private static void save(ServerLevel level, StateData state) {
        try {
            Path path = statePath(level);
            FileStorageIO.writeStringAtomic(path, GSON.toJson(state));
        } catch (IOException e) {
            throw new IllegalStateException("failed to save structure state", e);
        }
    }

    private static Path statePath(ServerLevel level) {
        return FileStoragePathResolver.categoryRoot(level, CATEGORY).resolve(STATE_FILE);
    }
}
