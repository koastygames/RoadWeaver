package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.ConcurrentHashMap;

public final class FastHeightSampler {
    private final ServerLevel level;
    private final ConcurrentHashMap<Long, Integer> surfaceHeightCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();
    private FastHeightSampler(ServerLevel level) { this.level = level; }
    public static FastHeightSampler create(ServerLevel level) { return new FastHeightSampler(level); }
    public int sampleHeight(int x, int z) {
        long key=packXZ(x,z);
        return surfaceHeightCache.computeIfAbsent(key, k -> {
            int sea=level.getSeaLevel();
            int motion=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            return motion>sea+2?motion:surface;
        });
    }
    public int sampleOceanFloor(int x,int z) {
        return oceanFloorCache.computeIfAbsent(packXZ(x,z), k -> level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z));
    }
    public void prewarmRegion(int minX,int minZ,int maxX,int maxZ,int step) {
        int stride=Math.max(1,step);
        for(int x=minX;x<=maxX;x+=stride) for(int z=minZ;z<=maxZ;z+=stride){sampleHeight(x,z);sampleOceanFloor(x,z);}
    }
    public void clearCache(){surfaceHeightCache.clear();oceanFloorCache.clear();}
    public int getCacheSize(){return surfaceHeightCache.size();}
    private static long packXZ(int x,int z){return ((long)x<<32)|(z&0xFFFFFFFFL);}
}
