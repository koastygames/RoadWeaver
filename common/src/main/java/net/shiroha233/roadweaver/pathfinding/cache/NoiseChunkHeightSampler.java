package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.ConcurrentHashMap;

/** 26.3 replacement for the removed NoiseChunk interpolation API. */
final class NoiseChunkHeightSampler {
    private final ServerLevel level;
    private final ConcurrentHashMap<Long,Integer> worldSurface=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long,Integer> oceanFloor=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long,Integer> motionBlocking=new ConcurrentHashMap<>();
    private NoiseChunkHeightSampler(ServerLevel level){this.level=level;}
    static NoiseChunkHeightSampler create(ServerLevel level, net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState randomState){return new NoiseChunkHeightSampler(level);}
    int motionBlockingNoLeaves(int x,int z){return sample(motionBlocking,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);}
    int worldSurfaceWg(int x,int z){return sample(worldSurface,Heightmap.Types.WORLD_SURFACE_WG,x,z);}
    int oceanFloorWg(int x,int z){return sample(oceanFloor,Heightmap.Types.OCEAN_FLOOR_WG,x,z);}
    void clear(){worldSurface.clear();oceanFloor.clear();motionBlocking.clear();}
    private int sample(ConcurrentHashMap<Long,Integer> cache,Heightmap.Types type,int x,int z){long key=((long)x<<32)|(z&0xFFFFFFFFL);return cache.computeIfAbsent(key,k->level.getHeight(type,x,z));}
}
