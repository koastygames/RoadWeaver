package net.shiroha233.roadweaver.structures.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.ServerLevelAccessor
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 结构生物生成规则（数据驱动）
 *
 * 定义结构放置后生成的生物：
 * - 生物类型
 * - 生成数量范围
 * - 相对于结构锚点的偏移位置
 * - 生成概率
 *
 * 支持 Codec 序列化，可在 datapack JSON 中配置。
 */
@Suppress("unused")
data class MobSpawnRule(
    val entityId: ResourceLocation,
    val countMin: Int,
    val countMax: Int,
    val offset: Vec3i,
    val chance: Float
) {
    /**
     * 运行时解析实体类型。
     *
     * 设计原因：
     * - 结构 JSON 是数据包内容，会在资源加载阶段解码。
     * - 若此时强制解析 EntityType（并要求注册表中存在），缺少前置模组会导致直接崩溃。
     * - 因此改为存 ResourceLocation，并在真正需要生成生物时再解析；缺失则跳过。
     */
    fun resolveEntityType(): Optional<EntityType<*>> {
        val opt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
        if (opt.isEmpty) {
            if (MISSING_ENTITY_LOGGED.add(entityId)) {
                LOGGER.warn("未找到实体类型 {}（可能缺少前置模组），将跳过该生物生成规则", entityId)
            }
        }
        return opt
    }

    /**
     * 在指定位置生成生物
     *
     * @return 生成的生物数量
     */
    fun spawn(level: ServerLevelAccessor, anchorPos: BlockPos, random: RandomSource): Int {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return 0
        }

        // 软依赖：实体不存在时跳过
        val resolvedType = resolveEntityType().orElse(null) ?: return 0

        // 计算生成数量
        var count = countMin
        if (countMax > countMin) {
            count = countMin + random.nextInt(countMax - countMin + 1)
        }

        // 计算生成位置
        val spawnPos = anchorPos.offset(offset.x, offset.y, offset.z)

        var spawned = 0
        for (i in 0 until count) {
            // 添加小范围随机偏移，避免生物重叠
            val x = spawnPos.x + 0.5 + (random.nextDouble() - 0.5) * 2
            val y = spawnPos.y.toDouble()
            val z = spawnPos.z + 0.5 + (random.nextDouble() - 0.5) * 2

            val entity = resolvedType.create(level.level) ?: continue
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f)

            // 如果是 Mob，调用 finalizeSpawn 进行初始化
            val mob = entity as? Mob
            if (mob != null) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null)
                mob.setPersistenceRequired()
            }

            if (level.addFreshEntity(entity)) {
                spawned++
            }
        }

        return spawned
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("RoadWeaver/MobSpawnRule")

        // 避免缺少前置时刷屏：同一个缺失实体只警告一次
        private val MISSING_ENTITY_LOGGED = ConcurrentHashMap.newKeySet<ResourceLocation>()

        @JvmField
        val CODEC: Codec<MobSpawnRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC
                    .fieldOf("entity")
                    .forGetter(MobSpawnRule::entityId),
                Codec.INT.optionalFieldOf("count_min", 1)
                    .forGetter(MobSpawnRule::countMin),
                Codec.INT.optionalFieldOf("count_max", 1)
                    .forGetter(MobSpawnRule::countMax),
                Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO)
                    .forGetter(MobSpawnRule::offset),
                Codec.FLOAT.optionalFieldOf("chance", 1.0f)
                    .forGetter(MobSpawnRule::chance)
            ).apply(instance, ::MobSpawnRule)
        }

        /** 列表 Codec */
        @JvmField
        val LIST_CODEC: Codec<List<MobSpawnRule>> = CODEC.listOf()

        // ==================== 预定义规则 ====================

        /** 单个村民 */
        @JvmField
        val SINGLE_VILLAGER = MobSpawnRule(
            ResourceLocation.fromNamespaceAndPath("minecraft", "villager"),
            1,
            1,
            Vec3i(0, 1, 0),
            1.0f
        )

        /** 1-2 个村民 */
        @JvmField
        val VILLAGERS = MobSpawnRule(
            ResourceLocation.fromNamespaceAndPath("minecraft", "villager"),
            1,
            2,
            Vec3i(0, 1, 0),
            1.0f
        )

        /** 单只猫（50%概率） */
        @JvmField
        val CAT = MobSpawnRule(
            ResourceLocation.fromNamespaceAndPath("minecraft", "cat"),
            1,
            1,
            Vec3i(0, 1, 0),
            0.5f
        )

        /** 铁傀儡（用于大型结构） */
        @JvmField
        val IRON_GOLEM = MobSpawnRule(
            ResourceLocation.fromNamespaceAndPath("minecraft", "iron_golem"),
            1,
            1,
            Vec3i(0, 1, 0),
            0.3f
        )
    }
}
