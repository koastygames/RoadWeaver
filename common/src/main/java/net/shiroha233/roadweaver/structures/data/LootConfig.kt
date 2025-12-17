package net.shiroha233.roadweaver.structures.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.storage.loot.LootTable

/**
 * 结构战利品配置（数据驱动）
 *
 * 定义结构中战利品容器的位置和战利品表：
 * - 战利品表 ID
 * - 相对于结构锚点的偏移位置
 * - 生成概率
 *
 * 支持 Codec 序列化，可在 datapack JSON 中配置。
 */
@Suppress("unused")
data class LootConfig(
    val lootTable: ResourceLocation,
    val offset: Vec3i,
    val chance: Float
) {
    /**
     * 为指定位置的容器设置战利品表
     *
     * @return 是否成功设置
     */
    fun apply(level: ServerLevelAccessor, anchorPos: BlockPos, random: RandomSource): Boolean {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false
        }

        // 计算容器位置
        val containerPos = anchorPos.offset(offset.x, offset.y, offset.z)

        // 获取方块实体并检查是否为可随机化容器
        val blockEntity = level.getBlockEntity(containerPos) ?: return false
        val container = blockEntity as? RandomizableContainerBlockEntity ?: return false

        // 设置战利品表
        val lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTable)
        container.setLootTable(lootTableKey, random.nextLong())
        return true
    }

    /**
     * 在指定位置放置一个带战利品的箱子
     *
     * 用于模板中没有预置容器时，直接生成一个箱子并设置战利品表。
     *
     * @return 是否成功放置
     */
    fun placeChestWithLoot(level: ServerLevelAccessor, anchorPos: BlockPos, random: RandomSource): Boolean {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false
        }

        // 计算容器位置
        val containerPos = anchorPos.offset(offset.x, offset.y, offset.z)

        // 放置箱子
        level.setBlock(containerPos, Blocks.CHEST.defaultBlockState(), 2)

        // 获取方块实体并设置战利品表
        val blockEntity = level.getBlockEntity(containerPos)
        val container = blockEntity as? RandomizableContainerBlockEntity ?: return false

        val lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTable)
        container.setLootTable(lootTableKey, random.nextLong())
        return true
    }

    companion object {
        @JvmField
        val CODEC: Codec<LootConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC
                    .fieldOf("loot_table")
                    .forGetter(LootConfig::lootTable),
                Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO)
                    .forGetter(LootConfig::offset),
                Codec.FLOAT.optionalFieldOf("chance", 1.0f)
                    .forGetter(LootConfig::chance)
            ).apply(instance, ::LootConfig)
        }

        /** 列表 Codec */
        @JvmField
        val LIST_CODEC: Codec<List<LootConfig>> = CODEC.listOf()

        // ==================== 预定义配置 ====================

        /** 路边补给箱（默认位置） */
        @JvmField
        val ROADSIDE_SUPPLIES = LootConfig(
            ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/roadside_supplies"),
            Vec3i(0, 1, 0),
            1.0f
        )

        /** 小屋宝箱 */
        @JvmField
        val CABIN_CHEST = LootConfig(
            ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/cabin_chest"),
            Vec3i(0, 1, 0),
            1.0f
        )

        /** 稀有战利品（低概率） */
        @JvmField
        val RARE_LOOT = LootConfig(
            ResourceLocation.fromNamespaceAndPath("roadweaver", "chests/rare_loot"),
            Vec3i(0, 1, 0),
            0.2f
        )
    }
}
