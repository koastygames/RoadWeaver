package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.List;

/**
 * 结构战利品配置（数据驱动）
 * 
 * 定义结构中战利品容器的位置和战利品表：
 * - 战利品表 ID
 * - 相对于结构锚点的偏移位置
 * - 生成概率
 * 
 * 支持 Codec 序列化，可在 datapack JSON 中配置。
 * 
 * 原理：
 * 结构模板中应预先放置容器方块（箱子、桶等），
 * 此配置在结构放置后为这些容器设置战利品表，
 * 玩家打开时会根据战利品表生成物品。
 * 
 * JSON 示例：
 * {
 *   "loot_table": "roadweaver:chests/roadside_supplies",
 *   "offset": [2, 1, 3],
 *   "chance": 1.0
 * }
 */
public record LootConfig(
    ResourceLocation lootTable,
    Vec3i offset,
    float chance
) {
    
    public static final Codec<LootConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC
                .fieldOf("loot_table")
                .forGetter(LootConfig::lootTable),
            Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO)
                .forGetter(LootConfig::offset),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f)
                .forGetter(LootConfig::chance)
        ).apply(instance, LootConfig::new)
    );
    
    /** 列表 Codec */
    public static final Codec<List<LootConfig>> LIST_CODEC = CODEC.listOf();
    
    /**
     * 为指定位置的容器设置战利品表
     * 
     * @param level     世界
     * @param anchorPos 结构锚点位置
     * @param random    随机源
     * @return 是否成功设置
     */
    public boolean apply(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false;
        }
        
        // 计算容器位置
        BlockPos containerPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        // 获取方块实体
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        if (blockEntity == null) {
            return false;
        }
        
        // 检查是否为可随机化容器
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            // 1.21.1 使用 setLootTable(ResourceKey<LootTable>, long)
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable), random.nextLong());
            return true;
        }
        
        return false;
    }
    
    /**
     * 在指定位置放置一个带战利品的箱子
     * 
     * 用于模板中没有预置容器时，直接生成一个箱子并设置战利品表。
     * 
     * @param level     世界
     * @param anchorPos 结构锚点位置
     * @param random    随机源
     * @return 是否成功放置
     */
    public boolean placeChestWithLoot(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false;
        }
        
        // 计算容器位置
        BlockPos containerPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        // 放置箱子
        level.setBlock(containerPos, Blocks.CHEST.defaultBlockState(), 2);
        
        // 获取方块实体并设置战利品表
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable), random.nextLong());
            return true;
        }
        
        return false;
    }
    
    // ==================== 预定义配置 ====================
    
    /** 路边补给箱（默认位置） */
    public static final LootConfig ROADSIDE_SUPPLIES = new LootConfig(
        ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/roadside_supplies"),
        new Vec3i(0, 1, 0),
        1.0f
    );
    
    /** 小屋宝箱 */
    public static final LootConfig CABIN_CHEST = new LootConfig(
        ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/cabin_chest"),
        new Vec3i(0, 1, 0),
        1.0f
    );
    
    /** 稀有战利品（低概率） */
    public static final LootConfig RARE_LOOT = new LootConfig(
        ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/rare_loot"),
        new Vec3i(0, 1, 0),
        0.2f
    );
}
