package net.shiroha233.roadweaver.structures.pieces;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.structures.data.LootConfig;
import net.shiroha233.roadweaver.structures.data.MobSpawnRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单模板结构片段
 * 
 * 用于放置单个 NBT 模板的结构片段，支持：
 * - 旋转和镜像
 * - 忽略结构空位方块
 * - 保存到区块数据
 * - 结构放置后生成生物
 * - 结构放置后设置战利品表
 */
public class SimpleTemplatePiece extends TemplateStructurePiece {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/SimpleTemplatePiece");
    
    private final ResourceLocation templateId;
    private final List<MobSpawnRule> mobSpawns;
    private final List<LootConfig> lootConfigs;
    
    // 注意：结构通常会跨越多个区块，postProcess 会被多次调用。
    // 不能简单用一个 boolean 一刀切，否则会出现：
    // - 第一次调用时另一个区块的箱子还没生成出来 -> 战利品设置失败且不会重试
    // - 生物生成被多次执行 -> 女仆数量超出预期
    private boolean[] lootApplied;
    private int[] lootApplyAttempts;
    private boolean mobsSpawned = false;
    
    /**
     * 从模板创建结构片段（基础版本，无生物/战利品）
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
        this(manager, templateId, pos, rotation, mirror, List.of(), List.of());
    }
    
    /**
     * 从模板创建结构片段（完整版本，包含生物/战利品配置）
     * 
     * @param manager    模板管理器
     * @param templateId 模板 ID
     * @param pos        放置位置
     * @param rotation   旋转
     * @param mirror     镜像
     * @param mobSpawns  生物生成规则
     * @param lootConfigs 战利品配置
     */
    public SimpleTemplatePiece(StructureTemplateManager manager,
                               ResourceLocation templateId,
                               BlockPos pos,
                               Rotation rotation,
                               Mirror mirror,
                               List<MobSpawnRule> mobSpawns,
                               List<LootConfig> lootConfigs) {
        super(ModStructurePieceTypes.SIMPLE_TEMPLATE, 
              0, 
              manager, 
              templateId, 
              templateId.toString(), 
              createPlaceSettings(rotation, mirror), 
              pos);
        this.templateId = templateId;
        this.mobSpawns = mobSpawns != null ? mobSpawns : List.of();
        this.lootConfigs = lootConfigs != null ? lootConfigs : List.of();
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
        // 1.20.1 使用 tryParse 而非 parse
        this.templateId = ResourceLocation.tryParse(tag.getString("Template"));
        
        // 反序列化生物生成规则
        this.mobSpawns = deserializeMobSpawns(tag);
        // 反序列化战利品配置
        this.lootConfigs = deserializeLootConfigs(tag);
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
        
        // 序列化生物生成规则
        serializeMobSpawns(tag);
        // 序列化战利品配置
        serializeLootConfigs(tag);
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
        
        // 获取结构锚点位置
        BlockPos anchorPos = this.templatePosition;
        Rotation rotation = this.placeSettings.getRotation();
 
        initPostProcessState();
 
        boolean allLootApplied = true;
         
        // 处理战利品配置（先于生物生成，确保容器已放置）
        for (int i = 0; i < lootConfigs.size(); i++) {
            if (lootApplied[i]) {
                continue;
            }
 
            LootConfig lootConfig = lootConfigs.get(i);
            try {
                // 计算旋转后的偏移位置
                BlockPos rotatedOffset = transformOffset(lootConfig.offset(), rotation);
                BlockPos containerPos = anchorPos.offset(rotatedOffset);
 
                lootApplyAttempts[i]++;
                boolean applied = applyLootTable(level, containerPos, lootConfig, random);
                if (applied) {
                    lootApplied[i] = true;
                } else {
                    allLootApplied = false;
                }
            } catch (Exception e) {
                allLootApplied = false;
                LOGGER.warn("设置战利品表失败 at {}: {}", anchorPos, e.getMessage());
            }
        }
 
        // 如果没有战利品配置，就不需要等待
        if (lootConfigs.isEmpty()) {
            allLootApplied = true;
        }
         
        // 处理生物生成
        // 设计原则：等结构关键点（这里用"箱子全部就绪"作为信号）完成后再刷生物，避免刷到房屋外/空中。
        if (!mobsSpawned && allLootApplied) {
            for (MobSpawnRule spawnRule : mobSpawns) {
                try {
                    // 计算旋转后的偏移位置
                    BlockPos rotatedOffset = transformOffset(spawnRule.offset(), rotation);
                    BlockPos spawnPos = anchorPos.offset(rotatedOffset);
                    spawnMob(level, spawnPos, spawnRule, random);
                } catch (Exception e) {
                    LOGGER.warn("生成生物失败 at {}: {}", anchorPos, e.getMessage());
                }
            }
            mobsSpawned = true;
        }
    }
 
    private void initPostProcessState() {
        if (lootApplied == null || lootApplied.length != lootConfigs.size()) {
            lootApplied = new boolean[lootConfigs.size()];
            lootApplyAttempts = new int[lootConfigs.size()];
        }
    }
     
    /**
     * 根据结构旋转变换偏移坐标
     */
    private BlockPos transformOffset(Vec3i offset, Rotation rotation) {
        // 根据旋转变换 XZ 坐标
        return switch (rotation) {
            case NONE -> new BlockPos(offset.getX(), offset.getY(), offset.getZ());
            case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
        };
    }
    
    /**
     * 为容器设置战利品表
     */
     private boolean applyLootTable(WorldGenLevel level, BlockPos containerPos, LootConfig config, RandomSource random) {
         // 概率检查
         if (config.chance() < 1.0f && random.nextFloat() > config.chance()) {
             return true;
         }
         
         BlockEntity blockEntity = level.getBlockEntity(containerPos);
         if (blockEntity instanceof RandomizableContainerBlockEntity container) {
             // 1.21.1 使用 ResourceKey 而非 ResourceLocation
             container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, config.lootTable()), random.nextLong());
             LOGGER.debug("设置战利品表 {} at {}", config.lootTable(), containerPos);
             return true;
         } else {
             LOGGER.debug("位置 {} 没有找到容器方块实体（可能该区块还未放置到这里）", containerPos);
             return false;
         }
     }
    
    /**
     * 生成生物
     */
     private void spawnMob(WorldGenLevel level, BlockPos spawnPos, MobSpawnRule rule, RandomSource random) {
        // 概率检查
        if (rule.chance() < 1.0f && random.nextFloat() > rule.chance()) {
            return;
        }

        // 软依赖：实体不存在时跳过（例如未安装对应前置模组）
        EntityType<?> resolvedType = rule.resolveEntityType().orElse(null);
        if (resolvedType == null) {
            return;
        }
        
        // 计算生成数量
        int count = rule.countMin();
        if (rule.countMax() > rule.countMin()) {
            count = rule.countMin() + random.nextInt(rule.countMax() - rule.countMin() + 1);
        }
        
         for (int i = 0; i < count; i++) {
             // 之前使用 [-1, 1] 的随机偏移，容易把生物刷到房屋外。
             // 这里缩小随机范围，尽量保证生成点仍在室内。
             double spread = 0.3;
             double x = spawnPos.getX() + 0.5 + (random.nextDouble() - 0.5) * spread * 2;
             double y = spawnPos.getY();
             double z = spawnPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * spread * 2;
             
            
             Entity entity = resolvedType.create(level.getLevel());
             if (entity == null) {
                 continue;
             }
            
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
            
            // 如果是 Mob，调用 finalizeSpawn 进行初始化
            // 1.21.1 不需要传入 CompoundTag 参数
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), 
                    MobSpawnType.STRUCTURE, null);
                mob.setPersistenceRequired();
            }
            
             if (level.addFreshEntity(entity)) {
                 LOGGER.debug("生成生物 {} at ({}, {}, {})", rule.entityId(), x, y, z);
             }
         }
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
    
    // ==================== 序列化/反序列化辅助方法 ====================
    
    private void serializeMobSpawns(CompoundTag tag) {
        if (mobSpawns.isEmpty()) return;
        
        ListTag listTag = new ListTag();
        for (MobSpawnRule rule : mobSpawns) {
            DataResult<Tag> result = MobSpawnRule.CODEC.encodeStart(NbtOps.INSTANCE, rule);
            result.result().ifPresent(listTag::add);
        }
        if (!listTag.isEmpty()) {
            tag.put("MobSpawns", listTag);
        }
    }
    
    private void serializeLootConfigs(CompoundTag tag) {
        if (lootConfigs.isEmpty()) return;
        
        ListTag listTag = new ListTag();
        for (LootConfig config : lootConfigs) {
            DataResult<Tag> result = LootConfig.CODEC.encodeStart(NbtOps.INSTANCE, config);
            result.result().ifPresent(listTag::add);
        }
        if (!listTag.isEmpty()) {
            tag.put("LootConfigs", listTag);
        }
    }
    
    private static List<MobSpawnRule> deserializeMobSpawns(CompoundTag tag) {
        if (!tag.contains("MobSpawns")) return List.of();
        
        List<MobSpawnRule> result = new ArrayList<>();
        ListTag listTag = tag.getList("MobSpawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag ruleTag = listTag.getCompound(i);
            DataResult<MobSpawnRule> parseResult = MobSpawnRule.CODEC.parse(NbtOps.INSTANCE, ruleTag);
            parseResult.result().ifPresent(result::add);
        }
        return result;
    }
    
    private static List<LootConfig> deserializeLootConfigs(CompoundTag tag) {
        if (!tag.contains("LootConfigs")) return List.of();
        
        List<LootConfig> result = new ArrayList<>();
        ListTag listTag = tag.getList("LootConfigs", Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag configTag = listTag.getCompound(i);
            DataResult<LootConfig> parseResult = LootConfig.CODEC.parse(NbtOps.INSTANCE, configTag);
            parseResult.result().ifPresent(result::add);
        }
        return result;
    }
}
