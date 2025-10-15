package net.countered.settlementroads.helpers.async;

import com.mojang.datafixers.util.Pair;
import net.countered.settlementroads.config.ConfigProvider;
import net.countered.settlementroads.config.IModConfig;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.persistence.WorldDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * 限流结构定位器 - 解决主线程堵塞问题
 * 
 * ⚠️ 重要说明：
 * Minecraft的API（如findNearestMapStructure）必须在主线程中调用！
 * 因此我们不使用真正的异步线程，而是使用限流策略：
 * 
 * 策略：
 * 1. 将搜寻请求加入队列
 * 2. 每个tick只处理少量请求（避免长时间阻塞）
 * 3. 通过tick间隔实现"让出CPU"的效果
 * 4. 使用回调机制异步返回结果
 * 
 * 优势：
 * - 符合Minecraft的线程模型
 * - 避免并发问题
 * - 仍然能显著减少卡顿（分批处理）
 */
public class ThrottledStructureLocator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    
    // 每个tick最多处理的搜寻请求数量
    private static final int MAX_SEARCHES_PER_TICK = 1;
    
    // 每个世界的待处理搜寻队列
    private static final ConcurrentHashMap<String, Queue<LocateRequest>> pendingRequests = new ConcurrentHashMap<>();
    
    // 统计信息
    private static final ConcurrentHashMap<String, LocateStats> stats = new ConcurrentHashMap<>();
    
    /**
     * 提交一个结构搜寻请求（添加到队列，不立即执行）
     * 
     * @param level 服务器世界
     * @param locateCount 要定位的数量
     * @param locateAtPlayer 是否在玩家位置搜寻
     * @param callback 完成回调（在主线程中调用）
     */
    public static void locateAsync(ServerLevel level, int locateCount, boolean locateAtPlayer,
                                   Consumer<List<LocateResult>> callback) {
        if (locateCount <= 0) {
            if (callback != null) {
                callback.accept(Collections.emptyList());
            }
            return;
        }
        
        String worldKey = level.dimension().location().toString();
        
        // 创建请求
        LocateRequest request = new LocateRequest(level, locateCount, locateAtPlayer, callback);
        
        // 添加到队列
        Queue<LocateRequest> queue = pendingRequests.computeIfAbsent(worldKey,
            k -> new ConcurrentLinkedQueue<>());
        queue.add(request);
        
        // 更新统计
        LocateStats stat = stats.computeIfAbsent(worldKey, k -> new LocateStats());
        stat.queuedRequests++;
        
        LOGGER.debug("🔍 Queued structure search request: world={}, count={}, queueSize={}",
            worldKey, locateCount, queue.size());
    }
    
    /**
     * 在主线程的tick事件中调用，处理待处理的搜寻请求
     * 每次调用只处理少量请求，避免长时间阻塞
     * 
     * @param level 服务器世界
     */
    public static void tickProcess(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        Queue<LocateRequest> queue = pendingRequests.get(worldKey);
        
        if (queue == null || queue.isEmpty()) {
            return;
        }
        
        // 每个tick最多处理N个搜寻请求
        int processed = 0;
        while (processed < MAX_SEARCHES_PER_TICK && !queue.isEmpty()) {
            LocateRequest request = queue.poll();
            if (request != null) {
                try {
                    processRequest(request);
                    processed++;
                } catch (Exception e) {
                    LOGGER.error("❌ Error processing locate request: {}", e.getMessage(), e);
                    if (request.callback != null) {
                        request.callback.accept(Collections.emptyList());
                    }
                }
            }
        }
        
        // 更新统计
        if (processed > 0) {
            LocateStats stat = stats.get(worldKey);
            if (stat != null) {
                stat.processedRequests += processed;
            }
            LOGGER.trace("Processed {} structure search requests, {} remaining in queue",
                processed, queue.size());
        }
    }
    
    /**
     * 处理单个搜寻请求（在主线程中执行）
     */
    private static void processRequest(LocateRequest request) {
        ServerLevel level = request.level;
        int locateCount = request.locateCount;
        boolean locateAtPlayer = request.locateAtPlayer;
        
        List<LocateResult> results = new ArrayList<>();
        IModConfig config = ConfigProvider.get();
        WorldDataProvider dataProvider = WorldDataProvider.getInstance();
        
        Records.StructureLocationData locationData = dataProvider.getStructureLocations(level);
        if (locationData == null) {
            locationData = new Records.StructureLocationData(new ArrayList<>());
        }
        
        List<BlockPos> knownLocations = new ArrayList<>(locationData.structureLocations());
        List<Records.StructureInfo> structureInfos = new ArrayList<>(locationData.structureInfos());
        
        Optional<HolderSet<Structure>> targetStructures = resolveStructureTargets(level, config.structuresToLocate());
        if (targetStructures.isEmpty()) {
            LOGGER.warn("无法解析结构目标列表，跳过搜寻");
            if (request.callback != null) {
                request.callback.accept(results);
            }
            return;
        }
        
        List<BlockPos> centers = collectSearchCenters(level, locateAtPlayer);
        int radius = Math.max(config.structureSearchRadius(), 1);
        
        // 搜寻结构
        for (BlockPos center : centers) {
            if (locateCount <= 0) {
                break;
            }
            
            try {
                Pair<BlockPos, Holder<Structure>> result = level.getChunkSource()
                        .getGenerator()
                        .findNearestMapStructure(level, targetStructures.get(), center, radius, true);
                
                if (result != null) {
                    BlockPos structurePos = result.getFirst();
                    Holder<Structure> structureHolder = result.getSecond();
                    
                    if (!containsBlockPos(knownLocations, structurePos)) {
                        knownLocations.add(structurePos);
                        
                        String structureId = structureHolder.unwrapKey()
                                .map(key -> key.location().toString())
                                .orElse("unknown");
                        
                        Records.StructureInfo info = new Records.StructureInfo(structurePos, structureId);
                        structureInfos.add(info);
                        
                        results.add(new LocateResult(structurePos, structureId, true));
                        locateCount--;
                        
                        LOGGER.debug("✅ Found structure {} at {}", structureId, structurePos);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error finding structure at {}: {}", center, e.getMessage());
            }
        }
        
        // 保存结果
        if (!results.isEmpty()) {
            dataProvider.setStructureLocations(level, new Records.StructureLocationData(knownLocations, structureInfos));
            LOGGER.info("Located {} new structures", results.size());
        }
        
        // 调用回调
        if (request.callback != null) {
            request.callback.accept(results);
        }
    }
    
    /**
     * 获取队列中待处理的请求数量
     */
    public static int getPendingCount(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        Queue<LocateRequest> queue = pendingRequests.get(worldKey);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * 清理指定世界的队列
     */
    public static void clearQueue(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        Queue<LocateRequest> queue = pendingRequests.remove(worldKey);
        if (queue != null) {
            queue.clear();
            LOGGER.debug("Cleared structure search queue for world: {}", worldKey);
        }
        stats.remove(worldKey);
    }
    
    /**
     * 关闭定位器（清理所有资源）
     */
    public static void shutdown() {
        pendingRequests.clear();
        stats.clear();
        LOGGER.info("ThrottledStructureLocator shut down");
    }
    
    /**
     * 解析结构目标
     */
    private static Optional<HolderSet<Structure>> resolveStructureTargets(ServerLevel level, List<String> identifiersList) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        
        if (identifiersList == null || identifiersList.isEmpty()) {
            return Optional.empty();
        }
        
        for (String line : identifiersList) {
            if (line == null) continue;
            String norm = line.replace('\r', ' ').replace('\n', ' ').trim();
            if (norm.isEmpty()) continue;
            
            String[] tokens = norm.split("[;,\\s]+");
            for (String raw : tokens) {
                if (raw == null) continue;
                String token = raw.trim();
                if (token.isEmpty()) continue;
                
                token = token.replace("\r", "").replace("\n", "");
                token = token.replaceAll("^[\\\"'`]+|[\\\"'`]+$", "");
                token = token.replaceAll("[,;，；]+$", "");
                if (!token.isEmpty() && token.charAt(0) == '\uFEFF') token = token.substring(1);
                token = token.replace('＃', '#').trim();
                if (token.isEmpty()) continue;
                
                int hashIdx = token.indexOf('#');
                if (hashIdx >= 0) {
                    String tagToken = token.substring(hashIdx + 1).trim();
                    try {
                        ResourceLocation tagId = ResourceLocation.parse(tagToken);
                        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
                        registry.getTag(tag).ifPresent(named -> {
                            for (Holder<Structure> h : named) holders.add(h);
                        });
                    } catch (Exception ex) {
                        LOGGER.warn("Invalid structure tag: #{}", tagToken);
                    }
                } else {
                    try {
                        String cleaned = token.replaceAll("^[^a-z0-9_.:/\\-]+", "");
                        
                        if (cleaned.contains("*")) {
                            String pattern = cleaned.replace("*", "");
                            for (var entry : registry.entrySet()) {
                                String structureId = entry.getKey().location().toString();
                                if (structureId.startsWith(pattern)) {
                                    registry.getHolder(entry.getKey()).ifPresent(holders::add);
                                }
                            }
                        } else {
                            ResourceLocation id = ResourceLocation.parse(cleaned);
                            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
                            registry.getHolder(key).ifPresent(holders::add);
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("Invalid structure id: {}", token);
                    }
                }
            }
        }
        
        if (holders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(HolderSet.direct(holders));
    }
    
    /**
     * 收集搜索中心点
     */
    private static List<BlockPos> collectSearchCenters(ServerLevel level, boolean locateAtPlayer) {
        List<BlockPos> centers = new ArrayList<>();
        if (locateAtPlayer) {
            for (ServerPlayer player : level.players()) {
                centers.add(player.blockPosition());
            }
        }
        
        BlockPos spawn = level.getSharedSpawnPos();
        if (centers.isEmpty()) {
            centers.add(spawn);
            int r = Math.max(ConfigProvider.get().structureSearchRadius(), 1);
            int[] muls = new int[] {3, 6};
            for (int m : muls) {
                int d = r * m;
                centers.add(spawn.offset( d, 0,  0));
                centers.add(spawn.offset(-d, 0,  0));
                centers.add(spawn.offset( 0, 0,  d));
                centers.add(spawn.offset( 0, 0, -d));
                centers.add(spawn.offset( d, 0,  d));
                centers.add(spawn.offset(-d, 0,  d));
                centers.add(spawn.offset( d, 0, -d));
                centers.add(spawn.offset(-d, 0, -d));
            }
        }
        return centers;
    }
    
    private static boolean containsBlockPos(List<BlockPos> list, BlockPos pos) {
        for (BlockPos existing : list) {
            if (existing.equals(pos)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 定位请求
     */
    private static class LocateRequest {
        final ServerLevel level;
        final int locateCount;
        final boolean locateAtPlayer;
        final Consumer<List<LocateResult>> callback;
        
        LocateRequest(ServerLevel level, int locateCount, boolean locateAtPlayer,
                     Consumer<List<LocateResult>> callback) {
            this.level = level;
            this.locateCount = locateCount;
            this.locateAtPlayer = locateAtPlayer;
            this.callback = callback;
        }
    }
    
    /**
     * 定位结果
     */
    public static class LocateResult {
        public final BlockPos position;
        public final String structureId;
        public final boolean success;
        
        public LocateResult(BlockPos position, String structureId, boolean success) {
            this.position = position;
            this.structureId = structureId;
            this.success = success;
        }
    }
    
    /**
     * 统计信息
     */
    private static class LocateStats {
        int queuedRequests = 0;
        int processedRequests = 0;
    }
}
