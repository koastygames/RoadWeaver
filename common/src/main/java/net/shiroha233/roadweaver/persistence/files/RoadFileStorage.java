/* 文件职责：以现有世界文件路径持久化道路索引与道路 NBT 数据。 */
package net.shiroha233.roadweaver.persistence.files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 道路文件存储：索引 JSON + 每条道路一个 NBT 文件。
 */
public final class RoadFileStorage {
    private RoadFileStorage() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY = "roads";
    private static final String INDEX_FILE = "index.json";
    private static final String ROAD_DIR = "roads";

    private static final class IndexData {
        List<RoadEntry> roads = new ArrayList<>();
    }

    private static final class RoadEntry {
        long fingerprint;
        int width;
        int roadType;
        int minX;
        int minZ;
        int maxX;
        int maxZ;
        String file;
    }

    public static void addRoad(ServerLevel level, RoadData road) {
        if (!isOverworld(level) || road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) return;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                Files.createDirectories(root);
                IndexData index = readIndex(root);
                RoadEntry entry = toEntry(level, road);
                Path roadFile = root.resolve(ROAD_DIR).resolve(entry.file);
                writeRoadFile(roadFile, road);
                index.roads.removeIf(existing -> existing.fingerprint == entry.fingerprint);
                index.roads.add(entry);
                writeIndex(root, index);
            } catch (IOException e) {
                throw new IllegalStateException("failed to save road to file storage", e);
            }
        }
    }

    public static List<RoadData> queryRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (!isOverworld(level)) return List.of();
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                if (index.roads.isEmpty()) return List.of();
                LinkedHashMap<Long, RoadData> merged = new LinkedHashMap<>();
                for (RoadEntry entry : index.roads) {
                    if (!intersects(entry, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) continue;
                    RoadData road = readRoadFile(root.resolve(ROAD_DIR).resolve(entry.file));
                    if (road != null) merged.putIfAbsent(computeFingerprint(road), road);
                }
                return new ArrayList<>(merged.values());
            } catch (IOException e) {
                LOGGER.warn("读取道路文件存储失败", e);
                return List.of();
            }
        }
    }

    public static CompletableFuture<List<RoadData>> queryRectAsync(ServerLevel level,
                                                                    int minBlockX, int minBlockZ,
                                                                    int maxBlockX, int maxBlockZ) {
        return CompletableFuture.supplyAsync(() -> queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ));
    }

    public static List<RoadData> loadAll(ServerLevel level) {
        if (!isOverworld(level)) return List.of();
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                if (index.roads.isEmpty()) return List.of();
                LinkedHashMap<Long, RoadData> merged = new LinkedHashMap<>();
                for (RoadEntry entry : index.roads) {
                    RoadData road = readRoadFile(root.resolve(ROAD_DIR).resolve(entry.file));
                    if (road != null) merged.putIfAbsent(computeFingerprint(road), road);
                }
                return new ArrayList<>(merged.values());
            } catch (IOException e) {
                LOGGER.warn("读取全部道路文件存储失败", e);
                return List.of();
            }
        }
    }

    public static RoadData loadByFingerprint(ServerLevel level, long fingerprint) {
        if (!isOverworld(level)) return null;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                for (RoadEntry entry : index.roads) {
                    if (entry.fingerprint != fingerprint) continue;
                    return readRoadFile(root.resolve(ROAD_DIR).resolve(entry.file));
                }
            } catch (IOException e) {
                LOGGER.warn("按 fingerprint 读取道路文件失败", e);
            }
            return null;
        }
    }

    public static void deleteRoad(ServerLevel level, long fingerprint) {
        if (!isOverworld(level)) return;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                RoadEntry removed = null;
                for (var it = index.roads.iterator(); it.hasNext();) {
                    RoadEntry entry = it.next();
                    if (entry.fingerprint == fingerprint) {
                        removed = entry;
                        it.remove();
                        break;
                    }
                }
                if (removed != null) {
                    Files.deleteIfExists(root.resolve(ROAD_DIR).resolve(removed.file));
                    writeIndex(root, index);
                }
            } catch (IOException e) {
                LOGGER.warn("删除道路文件失败", e);
            }
        }
    }

    public static boolean hasAnyRoad(ServerLevel level) {
        if (!isOverworld(level)) return false;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                if (!index.roads.isEmpty()) return true;
                Path roadDir = root.resolve(ROAD_DIR);
                if (!Files.isDirectory(roadDir)) return false;
                try (var files = Files.list(roadDir)) {
                    return files.anyMatch(path -> path.getFileName().toString().endsWith(".nbt"));
                }
            } catch (IOException e) {
                LOGGER.warn("检查道路文件存在性失败", e);
                return false;
            }
        }
    }

    public static boolean hasRoadInRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (!isOverworld(level)) return false;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                IndexData index = readIndex(root);
                for (RoadEntry entry : index.roads) {
                    if (intersects(entry, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) return true;
                }
            } catch (IOException e) {
                LOGGER.warn("检查道路区域索引失败", e);
            }
            return false;
        }
    }

    public static void replaceRoad(ServerLevel level, long oldFingerprint, RoadData newRoad) {
        if (!isOverworld(level) || newRoad == null || newRoad.roadSegmentList() == null || newRoad.roadSegmentList().isEmpty()) return;
        synchronized (lock(level)) {
            try {
                Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
                Files.createDirectories(root);
                IndexData index = readIndex(root);
                RoadEntry newEntry = toEntry(level, newRoad);
                Path newFile = root.resolve(ROAD_DIR).resolve(newEntry.file);
                writeRoadFile(newFile, newRoad);

                RoadEntry oldEntry = null;
                for (var it = index.roads.iterator(); it.hasNext();) {
                    RoadEntry entry = it.next();
                    if (entry.fingerprint == oldFingerprint || entry.fingerprint == newEntry.fingerprint) {
                        if (entry.fingerprint == oldFingerprint) oldEntry = entry;
                        it.remove();
                    }
                }
                index.roads.add(newEntry);
                writeIndex(root, index);

                if (oldEntry != null && oldEntry.fingerprint != newEntry.fingerprint) {
                    Files.deleteIfExists(root.resolve(ROAD_DIR).resolve(oldEntry.file));
                }
            } catch (IOException e) {
                throw new IllegalStateException("failed to replace road in file storage", e);
            }
        }
    }

    public static void clearAll(ServerLevel level) {
        if (!isOverworld(level)) return;
        synchronized (lock(level)) {
            FileStorageIO.deleteTree(FileStoragePathResolver.categoryRoot(level, CATEGORY), LOGGER, "清理道路文件存储失败");
        }
    }

    private static Object lock(ServerLevel level) {
        return (FileStoragePathResolver.categoryRoot(level, CATEGORY).toString() + "#lock").intern();
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static boolean intersects(RoadEntry entry, int minX, int minZ, int maxX, int maxZ) {
        return entry.maxX >= minX && entry.minX <= maxX && entry.maxZ >= minZ && entry.minZ <= maxZ;
    }

    private static RoadEntry toEntry(ServerLevel level, RoadData road) {
        RoadEntry entry = new RoadEntry();
        entry.fingerprint = computeFingerprint(road);
        entry.width = road.width();
        entry.roadType = road.roadType();
        entry.file = Long.toUnsignedString(entry.fingerprint) + ".nbt";
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RoadSegmentPlacement seg : road.roadSegmentList()) {
            if (seg == null) continue;
            BlockPos p = seg.middlePos();
            if (p == null) continue;
            int x = p.getX();
            int z = p.getZ();
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = minZ = maxX = maxZ = 0;
        }
        entry.minX = minX;
        entry.minZ = minZ;
        entry.maxX = maxX;
        entry.maxZ = maxZ;
        return entry;
    }

    private static IndexData readIndex(Path root) throws IOException {
        Path indexPath = root.resolve(INDEX_FILE);
        if (!Files.exists(indexPath)) return new IndexData();
        try (var reader = Files.newBufferedReader(indexPath)) {
            IndexData data = GSON.fromJson(reader, IndexData.class);
            return data != null && data.roads != null ? data : new IndexData();
        } catch (RuntimeException e) {
            FileStorageIO.quarantineCorrupt(indexPath, LOGGER, "道路索引文件损坏，已隔离");
            return new IndexData();
        }
    }

    private static void writeIndex(Path root, IndexData data) throws IOException {
        FileStorageIO.writeStringAtomic(root.resolve(INDEX_FILE), GSON.toJson(data));
    }

    private static void writeRoadFile(Path file, RoadData road) throws IOException {
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        CompoundTag compound = new CompoundTag();
        Tag tag = RoadData.CODEC.encodeStart(NbtOps.INSTANCE, road).result().orElseThrow(() -> new IllegalStateException("road codec encode failed"));
        compound.put("road", tag);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.write(compound, data);
        }
        FileStorageIO.writeBytesAtomic(file, out.toByteArray());
    }

    private static RoadData readRoadFile(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        RoadData road = deserializeRoadData(Files.readAllBytes(file));
        if (road == null) {
            FileStorageIO.quarantineCorrupt(file, LOGGER, "道路 NBT 文件损坏，已隔离");
        }
        return road;
    }

    private static RoadData deserializeRoadData(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            CompoundTag compound = NbtIo.read(in);
            if (compound == null || !compound.contains("road")) return null;
            Tag tag = compound.get("road");
            return RoadData.CODEC.parse(new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE, tag)).result().orElse(null);
        } catch (Exception e) {
            LOGGER.warn("反序列化道路数据失败", e);
            return null;
        }
    }

    public static long computeFingerprint(RoadData road) {
        if (road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) return 0L;
        BlockPos a = firstPos(road);
        BlockPos b = lastPos(road);
        if (a == null || b == null) return 0L;
        long ka = (((long) a.getX()) << 32) ^ (a.getZ() & 0xffffffffL);
        long kb = (((long) b.getX()) << 32) ^ (b.getZ() & 0xffffffffL);
        long lo = Math.min(ka, kb), hi = Math.max(ka, kb);
        long f = (hi << 1) ^ lo;
        f ^= ((long) road.width() & 0xffffffffL);
        f ^= ((long) road.roadType() & 0xffffffffL) << 33;
        return f;
    }

    private static BlockPos firstPos(RoadData road) {
        for (RoadSegmentPlacement segment : road.roadSegmentList()) {
            if (segment != null && segment.middlePos() != null) return segment.middlePos();
        }
        return null;
    }

    private static BlockPos lastPos(RoadData road) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        for (int i = segments.size() - 1; i >= 0; i--) {
            RoadSegmentPlacement segment = segments.get(i);
            if (segment != null && segment.middlePos() != null) return segment.middlePos();
        }
        return null;
    }
}
