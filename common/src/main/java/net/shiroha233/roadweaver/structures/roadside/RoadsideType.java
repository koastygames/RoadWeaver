package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

/**
 * 路边结构类型枚举
 * 
 * 每种类型对应一个 NBT 模板文件，并定义其放置参数：
 * - 模板路径
 * - 结构尺寸提示（用于碰撞检测和间距计算）
 * - 放置权重（影响随机选择概率）
 * - 是否需要面向道路
 */
public enum RoadsideType {
    
    /**
     * 路边长椅 - 供旅人休息的小型木质长椅
     */
    BENCH(
            new ResourceLocation("roadweaver", "roadside/roadside_bench"),
            new Vec3i(3, 2, 2),  // 长椅大约 3x2x2
            10,                   // 权重：常见
            true                  // 面向道路
    ),
    
    /**
     * 小型营火 - 路边的篝火休息点
     */
    CAMPFIRE(
            new ResourceLocation("roadweaver", "roadside/small_campfire"),
            new Vec3i(3, 3, 3),  // 营火大约 3x3x3
            8,                    // 权重：较常见
            false                 // 不需要特定朝向
    );
    
    private final ResourceLocation templateId;
    private final Vec3i sizeHint;
    private final int weight;
    private final boolean faceRoad;
    
    RoadsideType(ResourceLocation templateId, Vec3i sizeHint, int weight, boolean faceRoad) {
        this.templateId = templateId;
        this.sizeHint = sizeHint;
        this.weight = weight;
        this.faceRoad = faceRoad;
    }
    
    /**
     * 获取结构模板的 ResourceLocation
     * 路径格式: roadweaver:roadside/xxx
     * 对应文件: data/roadweaver/structures/roadside/xxx.nbt
     */
    public ResourceLocation templateId() {
        return templateId;
    }
    
    /**
     * 获取结构的大致尺寸（X, Y, Z）
     * 用于碰撞检测和确定放置位置的偏移
     */
    public Vec3i sizeHint() {
        return sizeHint;
    }
    
    /**
     * 获取随机选择权重
     * 权重越高，被选中的概率越大
     */
    public int weight() {
        return weight;
    }
    
    /**
     * 是否需要面向道路放置
     * true: 结构正面朝向道路方向
     * false: 随机朝向或保持默认朝向
     */
    public boolean faceRoad() {
        return faceRoad;
    }
    
    /**
     * 根据权重随机选择一种路边结构类型
     * 
     * @param random 随机源
     * @return 选中的结构类型
     */
    public static RoadsideType chooseWeighted(net.minecraft.util.RandomSource random) {
        int totalWeight = 0;
        for (RoadsideType type : values()) {
            totalWeight += type.weight;
        }
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RoadsideType type : values()) {
            cumulative += type.weight;
            if (roll < cumulative) {
                return type;
            }
        }
        
        // 兜底返回第一个
        return values()[0];
    }
}
