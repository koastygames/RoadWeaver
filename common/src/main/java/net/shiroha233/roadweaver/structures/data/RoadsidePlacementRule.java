package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 路边结构放置规则（数据驱动版）
 * 
 * 定义单个结构类型的放置条件：
 * - 允许的群系分类
 * - 最小道路长度要求
 * 
 * 支持 Codec 序列化，可在 datapack JSON 中配置。
 */
public record RoadsidePlacementRule(
    Set<BiomeCategory> allowedBiomes,
    int minRoadLength
) {
    
    public static final Codec<RoadsidePlacementRule> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BiomeCategory.CODEC.listOf()
                .optionalFieldOf("allowed_biomes", List.of())
                .forGetter(rule -> List.copyOf(rule.allowedBiomes)),
            Codec.INT.optionalFieldOf("min_road_length", 0).forGetter(RoadsidePlacementRule::minRoadLength)
        ).apply(instance, (biomes, minLen) -> {
            Set<BiomeCategory> biomeSet = biomes.isEmpty() 
                ? EnumSet.allOf(BiomeCategory.class) 
                : EnumSet.copyOf(biomes);
            return new RoadsidePlacementRule(biomeSet, minLen);
        })
    );
    
    /**
     * 检查群系是否允许放置
     */
    public boolean isBiomeAllowed(BiomeCategory category) {
        return allowedBiomes.contains(category);
    }
    
    /**
     * 检查道路长度是否满足要求
     */
    public boolean isRoadLongEnough(int roadLength) {
        return roadLength >= minRoadLength;
    }
    
    // ==================== 预定义规则 ====================
    
    /** 通用规则：所有群系，无道路长度限制 */
    public static final RoadsidePlacementRule UNIVERSAL = new RoadsidePlacementRule(
        EnumSet.allOf(BiomeCategory.class), 0
    );
    
    /** 温带规则：平原、森林、针叶林、樱花林 */
    public static final RoadsidePlacementRule TEMPERATE = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.PLAINS, BiomeCategory.FOREST, BiomeCategory.TAIGA, BiomeCategory.CHERRY_GROVE), 0
    );
    
    /** 寒带规则：雪地、针叶林 */
    public static final RoadsidePlacementRule COLD = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.SNOWY, BiomeCategory.TAIGA), 0
    );
    
    /** 热带规则：沙漠、热带草原、恶地 */
    public static final RoadsidePlacementRule HOT = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.DESERT, BiomeCategory.SAVANNA, BiomeCategory.BADLANDS), 0
    );
    
    /** 长距离规则：只在长道路上出现（100段以上） */
    public static final RoadsidePlacementRule LONG_ROAD_ONLY = new RoadsidePlacementRule(
        EnumSet.allOf(BiomeCategory.class), 100
    );
    
    /** 樱花林专属 */
    public static final RoadsidePlacementRule CHERRY_ONLY = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.CHERRY_GROVE), 0
    );
    
    /** 森林专属 */
    public static final RoadsidePlacementRule FOREST_ONLY = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.FOREST), 0
    );
}
