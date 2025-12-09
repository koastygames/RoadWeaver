package net.shiroha233.roadweaver.structures.pieces;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 简单模板结构片段
 * 
 * 用于放置单个 NBT 模板的结构片段，支持：
 * - 旋转和镜像
 * - 忽略结构空位方块
 * - 保存到区块数据
 */
public class SimpleTemplatePiece extends TemplateStructurePiece {
    
    private final ResourceLocation templateId;
    
    /**
     * 从模板创建结构片段
     * 
     * @param manager    模板管理器
     * @param templateId 模板 ID
     * @param pos        放置位置
     * @param rotation   旋转
     * @param mirror     镜像
     */
    public SimpleTemplatePiece(StructureTemplateManager manager,
                               ResourceLocation templateId,
                               BlockPos pos,
                               Rotation rotation,
                               Mirror mirror) {
        super(ModStructurePieceTypes.SIMPLE_TEMPLATE, 
              0, 
              manager, 
              templateId, 
              templateId.toString(), 
              createPlaceSettings(rotation, mirror), 
              pos);
        this.templateId = templateId;
    }
    
    /**
     * 从 NBT 反序列化（用于加载已保存的结构）
     */
    public SimpleTemplatePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructurePieceTypes.SIMPLE_TEMPLATE, 
              tag, 
              manager, 
              id -> createPlaceSettings(
                  Rotation.valueOf(tag.getString("Rot")),
                  Mirror.valueOf(tag.getString("Mir"))
              ));
        this.templateId = ResourceLocation.parse(tag.getString("Template"));
    }
    
    /**
     * 创建放置设置
     */
    private static StructurePlaceSettings createPlaceSettings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    
    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Template", templateId.toString());
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putString("Mir", placeSettings.getMirror().name());
    }
    
    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, 
                                    RandomSource random, BoundingBox box) {
        // 处理数据标记方块（如 jigsaw 方块）
        // 路边结构目前不使用数据标记，留空
    }
    
    @Override
    public void postProcess(WorldGenLevel level, 
                           StructureManager structureManager,
                           ChunkGenerator generator,
                           RandomSource random,
                           BoundingBox box,
                           ChunkPos chunkPos,
                           BlockPos pivot) {
        // 调用父类放置模板
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pivot);
    }
    
    /**
     * 获取模板 ID
     */
    public ResourceLocation getTemplateId() {
        return templateId;
    }
    
    /**
     * 获取结构片段类型
     */
    @Override
    public StructurePieceType getType() {
        return ModStructurePieceTypes.SIMPLE_TEMPLATE;
    }
}
