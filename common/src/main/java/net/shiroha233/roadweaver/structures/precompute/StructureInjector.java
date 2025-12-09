package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 结构注入器
 * 
 * 在区块 STRUCTURE_STARTS 阶段被 Mixin 调用，
 * 将预计算的路边结构注入到区块的结构数据中。
 * 
 * 这样做的好处：
 * 1. 结构在噪声生成之前就存在，Beardifier 可以自动处理地形适应
 * 2. 结构会自动保存到区块数据
 * 3. 与原版结构系统完全兼容
 */
public final class StructureInjector {
    private StructureInjector() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureInjector");
    
    /**
     * 在区块的 STRUCTURE_STARTS 阶段注入预计算的结构
     * 
     * @param level  服务端世界
     * @param chunk  正在生成的区块
     */
    public static void injectPendingStructures(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        
        // 获取该区块的待放置结构
        List<PendingRoadsideStructure> pending = PendingStructureStorage.getPendingStructures(level, chunkPos);
        if (pending.isEmpty()) {
            return;
        }
        
        // 获取必要的管理器
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        StructureManager structureManager = level.structureManager();
        StructureTemplateManager templateManager = level.getStructureManager();
        
        int injectedCount = 0;
        
        for (PendingRoadsideStructure pendingStructure : pending) {
            try {
                // 获取结构定义
                Structure structure = structureRegistry.get(pendingStructure.structureId());
                if (structure == null) {
                    LOGGER.warn("Structure {} not found in registry, skipping", pendingStructure.structureId());
                    continue;
                }
                
                // 获取模板 ID（支持 RoadsideStructure 和 SpawnCabinStructure）
                ResourceLocation templateId;
                if (structure instanceof RoadsideStructure roadsideStructure) {
                    templateId = roadsideStructure.templateId();
                } else if (structure instanceof SpawnCabinStructure spawnCabin) {
                    templateId = spawnCabin.templateId();
                } else {
                    LOGGER.warn("Structure {} is not a supported type, skipping", pendingStructure.structureId());
                    continue;
                }
                
                // 创建结构片段
                SimpleTemplatePiece piece = new SimpleTemplatePiece(
                    templateManager,
                    templateId,
                    pendingStructure.anchor(),
                    pendingStructure.rotation(),
                    Mirror.NONE
                );
                
                // 创建 StructureStart
                StructureStart start = new StructureStart(
                    structure,
                    chunkPos,
                    0, // references
                    new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))
                );
                
                // 注入到区块（使用 section 0，原版也是这样做的）
                SectionPos sectionPos = SectionPos.of(chunkPos, 0);
                structureManager.setStartForStructure(sectionPos, structure, start, chunk);
                
                injectedCount++;
                
            } catch (Exception e) {
                LOGGER.error("Failed to inject structure {} at {}", 
                    pendingStructure.structureId(), pendingStructure.anchor(), e);
            }
        }
        
        if (injectedCount > 0) {
            LOGGER.debug("Injected {} roadside structures into chunk [{}, {}]", 
                injectedCount, chunkPos.x, chunkPos.z);
        }
        
        // 标记已处理
        PendingStructureStorage.markAsInjected(level, chunkPos);
    }
}
