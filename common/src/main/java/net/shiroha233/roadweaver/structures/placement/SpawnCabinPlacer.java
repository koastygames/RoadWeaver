package net.shiroha233.roadweaver.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * 初始小屋放置器
 * 
 * 职责：
 * 1. 在世界首开时在出生点附近预计算初始小屋位置
 * 2. 如果区块未生成，存储到 PendingStructureStorage，让 Beardifier 自动处理地形
 * 3. 如果区块已生成，直接放置（无地形适应）
 * 4. 幂等性检查（避免重复放置）
 * 5. 将 StructureStart 保存到区块数据
 */
public final class SpawnCabinPlacer {
    private SpawnCabinPlacer() {}
    
    private static final ResourceLocation STRUCTURE_ID = ResourceLocation.fromNamespaceAndPath("roadweaver", "spawn_cabin");
    
    /**
     * 确保初始小屋已放置
     * 
     * @param level 服务端世界
     * @return 如果放置了新的小屋返回 true
     */
    public static boolean ensurePlaced(ServerLevel level) {
        if (level == null) return false;
        
        // 获取出生点
        BlockPos spawn = level.getSharedSpawnPos();
        
        // 幂等性检查：查看世界数据中是否已有结构记录
        var provider = WorldDataProvider.getInstance();
        var locs = provider.getStructureLocations(level);
        if (locs != null && locs.structureLocations() != null && !locs.structureLocations().isEmpty()) {
            return false;
        }
        
        // 从注册表获取结构定义
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = structureRegistry.get(STRUCTURE_ID);
        
        if (!(structure instanceof SpawnCabinStructure spawnCabin)) {
            // 结构未注册或类型不匹配
            return false;
        }
        
        // 计算放置位置
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos anchor = new BlockPos(spawn.getX(), y, spawn.getZ());
        Vec3i sizeHint = spawnCabin.sizeHint();
        ChunkPos chunkPos = new ChunkPos(anchor);
        
        // 检查区块状态：如果还没过 STRUCTURE_STARTS 阶段，使用预计算系统
        if (!isChunkPastStructureStarts(level, chunkPos)) {
            // 存储到预计算系统，让 Beardifier 自动处理地形适应
            PendingStructureStorage.addPendingStructure(
                level,
                STRUCTURE_ID,
                anchor,
                Rotation.NONE,
                sizeHint.getX(),
                sizeHint.getY(),
                sizeHint.getZ()
            );
            
            // 记录到世界数据（用于幂等性检查）
            provider.addStructureLocation(level, anchor);
            
            return true;
        }
        
        // 区块已生成，直接放置（无法享受 Beardifier 地形适应）
        StructureManager structureManager = level.structureManager();
        StructureTemplateManager templateManager = level.getStructureManager();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        
        StructureStart start = spawnCabin.placeAt(
            level, structureManager, templateManager, generator,
            anchor, Rotation.NONE, level.getRandom()
        );
        
        if (start == null || !start.isValid()) {
            return false;
        }
        
        // 保存到区块数据
        ChunkAccess chunk = level.getChunk(anchor);
        SectionPos sectionPos = SectionPos.of(chunkPos, 0);
        structureManager.setStartForStructure(sectionPos, spawnCabin, start, chunk);
        
        // 记录到世界数据（用于幂等性检查）
        provider.addStructureLocation(level, anchor);
        
        return true;
    }
    
    /**
     * 检查区块是否已经过了 STRUCTURE_STARTS 阶段
     */
    private static boolean isChunkPastStructureStarts(ServerLevel level, ChunkPos chunkPos) {
        try {
            ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, false);
            if (chunk == null) {
                return false; // 区块还没开始生成
            }
            ChunkStatus status = chunk.getPersistedStatus();
            return status.isOrAfter(ChunkStatus.STRUCTURE_STARTS);
        } catch (Exception e) {
            return false;
        }
    }
}
