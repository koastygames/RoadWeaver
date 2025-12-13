package net.shiroha233.roadweaver.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * 初始小屋放置器
 * 
 * 职责：
 * 1. 在世界首开时在出生点附近预计算初始小屋位置
 * 2. 存储到 PendingStructureStorage，让 Beardifier 自动处理地形适应
 * 3. 幂等性检查（避免重复放置）
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
}
