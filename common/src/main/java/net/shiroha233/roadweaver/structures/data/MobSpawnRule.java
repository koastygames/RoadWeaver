package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
 * 
 * JSON 示例：
 * {
 *   "entity": "minecraft:villager",
 *   "count_min": 1,
 *   "count_max": 2,
 *   "offset": [0, 1, 0],
 *   "chance": 1.0
 * }
 */
public record MobSpawnRule(
    ResourceLocation entityId,
    int countMin,
    int countMax,
    Vec3i offset,
    float chance
) {

    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/MobSpawnRule");

    // 避免缺少前置时刷屏：同一个缺失实体只警告一次
    private static final Set<ResourceLocation> MISSING_ENTITY_LOGGED = ConcurrentHashMap.newKeySet();
    
    public static final Codec<MobSpawnRule> CODEC = RecordCodecBuilder.create(instance ->
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
        ).apply(instance, MobSpawnRule::new)
    );
    
    /** 列表 Codec */
    public static final Codec<List<MobSpawnRule>> LIST_CODEC = CODEC.listOf();

    /**
     * 运行时解析实体类型。
     * 
     * 设计原因：
     * - 结构 JSON 是数据包内容，会在资源加载阶段解码。
     * - 若此时强制解析 EntityType（并要求注册表中存在），缺少前置模组会导致直接崩溃。
     * - 因此改为存 ResourceLocation，并在真正需要生成生物时再解析；缺失则跳过。
     */
    public Optional<EntityType<?>> resolveEntityType() {
        Optional<EntityType<?>> opt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (opt.isEmpty()) {
            if (MISSING_ENTITY_LOGGED.add(entityId)) {
                LOGGER.warn("未找到实体类型 {}（可能缺少前置模组），将跳过该生物生成规则", entityId);
            }
        }
        return opt;
    }
    
    /**
     * 在指定位置生成生物
     * 
     * @param level     世界
     * @param anchorPos 结构锚点位置
     * @param random    随机源
     * @return 生成的生物数量
     */
    public int spawn(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        // 概率检查
        if (chance < 1.0f && random.nextFloat() > chance) {
            return 0;
        }

        // 软依赖：实体不存在时跳过
        EntityType<?> resolvedType = resolveEntityType().orElse(null);
        if (resolvedType == null) {
            return 0;
        }
        
        // 计算生成数量
        int count = countMin;
        if (countMax > countMin) {
            count = countMin + random.nextInt(countMax - countMin + 1);
        }
        
        // 计算生成位置
        BlockPos spawnPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // 添加小范围随机偏移，避免生物重叠
            double x = spawnPos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2;
            double y = spawnPos.getY();
            double z = spawnPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2;
            
            Entity entity = resolvedType.create(level.getLevel());
            if (entity == null) {
                continue;
            }
            
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
            
            // 如果是 Mob，调用 finalizeSpawn 进行初始化
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), 
                    MobSpawnType.STRUCTURE, null, null);
                mob.setPersistenceRequired();
            }
            
            if (level.addFreshEntity(entity)) {
                spawned++;
            }
        }
        
        return spawned;
    }
    
    // ==================== 预定义规则 ====================
    
    /** 单个村民 */
    public static final MobSpawnRule SINGLE_VILLAGER = new MobSpawnRule(
        new ResourceLocation("minecraft", "villager"), 1, 1, new Vec3i(0, 1, 0), 1.0f
    );
    
    /** 1-2 个村民 */
    public static final MobSpawnRule VILLAGERS = new MobSpawnRule(
        new ResourceLocation("minecraft", "villager"), 1, 2, new Vec3i(0, 1, 0), 1.0f
    );
    
    /** 单只猫（50%概率） */
    public static final MobSpawnRule CAT = new MobSpawnRule(
        new ResourceLocation("minecraft", "cat"), 1, 1, new Vec3i(0, 1, 0), 0.5f
    );
    
    /** 铁傀儡（用于大型结构） */
    public static final MobSpawnRule IRON_GOLEM = new MobSpawnRule(
        new ResourceLocation("minecraft", "iron_golem"), 1, 1, new Vec3i(0, 1, 0), 0.3f
    );
}
