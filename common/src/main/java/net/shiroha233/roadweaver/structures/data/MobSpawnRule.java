package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;

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
    EntityType<?> entityType,
    int countMin,
    int countMax,
    Vec3i offset,
    float chance
) {
    
    public static final Codec<MobSpawnRule> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec()
                .fieldOf("entity")
                .forGetter(MobSpawnRule::entityType),
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
            
            Entity entity = entityType.create(level.getLevel());
            if (entity == null) {
                continue;
            }
            
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
            
            // 如果是 Mob，调用 finalizeSpawn 进行初始化
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), 
                    MobSpawnType.STRUCTURE, null);
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
        EntityType.VILLAGER, 1, 1, new Vec3i(0, 1, 0), 1.0f
    );
    
    /** 1-2 个村民 */
    public static final MobSpawnRule VILLAGERS = new MobSpawnRule(
        EntityType.VILLAGER, 1, 2, new Vec3i(0, 1, 0), 1.0f
    );
    
    /** 单只猫（50%概率） */
    public static final MobSpawnRule CAT = new MobSpawnRule(
        EntityType.CAT, 1, 1, new Vec3i(0, 1, 0), 0.5f
    );
    
    /** 铁傀儡（用于大型结构） */
    public static final MobSpawnRule IRON_GOLEM = new MobSpawnRule(
        EntityType.IRON_GOLEM, 1, 1, new Vec3i(0, 1, 0), 0.3f
    );
}
