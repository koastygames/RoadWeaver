package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.ChunkGenTracker;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats;
import net.shiroha233.roadweaver.persistence.RoadSpatialIndex;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PathBranchPlanningService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缂備胶鍠嶇粩瀵哥磽閹惧磭鎽犻柣銏㈠枎閹筹繝宕ㄩ妸锔藉焸缂佺媴绱曢幃濠囧闯?
 */
public final class CacheManager {
    private CacheManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    /**
     * 闁哄牆绉存慨鐔煎闯閵娿儲鍎欓柛鏂诲妽濡炲倿宕氬┑鍡╂綏闁?
     */
    public static void onServerStarted() {
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        RoadSpatialIndex.clearAllCache();
        TerrainSamplingStats.reset();
        LOGGER.debug("CacheManager: caches initialized");
    }

    /**
     * 闁哄牆绉存慨鐔煎闯閵娿儰绮绘慨婵勫灪濡炲倸銆掗崨顖涘€為柟纰樺亾闁哄牆顦辩槐锔锯偓?
     */
    public static void onServerStopping(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            try {
                RoadShardStorage.flushAll(level);
            } catch (Exception e) {
                LOGGER.warn("RoadShardStorage flush failed: {}", e.getMessage());
            }
        }

        RoadShardStorage.shutdown();

        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        PendingStructureStorage.clearAll();
        RoadSpatialIndex.clearAllCache();
        RoadPlanningService.resetAll();
        HighwayCellPathPlanningService.resetAll();
        PathBranchPlanningService.resetAll();
        ChunkGenTracker.clearAll();
        SignTextService.clearPending();
        TerrainSamplingStats.reset();

        LOGGER.debug("CacheManager: caches cleared on shutdown");
    }

    /**
     * 缂備焦娼欑€规娊宕″灞剧グ闁哄啳鍩栫粩濠氭偠閸℃凹鍤夌紓浣规綑鐎规娊宕楃€圭姳绮撻柣銊ュ缁憋妇鈧?
     */
    public static void onDimensionUnload(ServerLevel level) {
        if (level == null) return;

        try {
            RoadShardStorage.closeConnection(level);
        } catch (Exception e) {
            LOGGER.warn("Failed to close dimension {} storage: {}",
                    level.dimension().identifier(), e.getMessage());
        }

        RoadSpatialIndex.clearCache(level);
        PendingStructureStorage.clearDimension(level.dimension().identifier());
        RoadsideStructureRegistry.clearCache(level.dimension());
        BridgeTemplateStructureRegistry.clearCache(level.dimension());
        SignTextService.onDimensionUnload(level);

        LOGGER.debug("CacheManager: dimension {} cache cleared", level.dimension().identifier());
    }

    /**
     * 闂侇剚鎹侀惌楣冨极閻楀牆绁﹂柛娆惿戝ú鍧楀触鎼存繂鈻忛悗鐢垫嚀缁ㄦ煡宕犻崫鍕仴闁汇劌瀚埞鏍⒒鐎电鍋嶇€殿喗娲滅槐锔锯偓娑櫭妵鎴﹀极?
     */
    public static void invalidateRoadCache(ServerLevel level, int chunkX, int chunkZ) {
        RoadSpatialIndex.invalidateChunk(level, chunkX, chunkZ);
    }
}
