package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * 结构规模枚举
 * 
 * 定义不同规模结构的放置参数：
 * - 地形检查阈值（坡度、高度差）
 * - 托盘缓冲区大小
 * - 间距基准值
 * 
 * 支持 Codec 序列化，可在 datapack JSON 中使用。
 */
public enum StructureScale implements StringRepresentable {
    
    /**
     * 小型结构（如长椅、路牌）
     * - 简单地形检查
     * - 较小托盘
     */
    SMALL("small", 3, 5, 4, 64, 16),
    
    /**
     * 中型结构（如咖啡屋、商店）
     * - 严格坡度检查
     * - 多点采样
     * - 较大托盘
     */
    MEDIUM("medium", 6, 10, 8, 256, 48),
    
    /**
     * 大型结构（预留）
     * - 最严格的地形要求
     * - 最大托盘
     */
    LARGE("large", 4, 12, 12, 512, 64);
    
    public static final Codec<StructureScale> CODEC = StringRepresentable.fromEnum(StructureScale::values);
    
    private final String name;
    private final int maxSlope;          // 允许的最大坡度（底部高度差）
    private final int maxHeightDiff;     // 与道路的最大高度差
    private final int terraceBuffer;     // 托盘缓冲区宽度
    private final int defaultSpacing;    // 同类型结构默认间距
    private final int defaultSeparation; // 与其他结构默认间距
    
    StructureScale(String name, int maxSlope, int maxHeightDiff, int terraceBuffer, 
                   int defaultSpacing, int defaultSeparation) {
        this.name = name;
        this.maxSlope = maxSlope;
        this.maxHeightDiff = maxHeightDiff;
        this.terraceBuffer = terraceBuffer;
        this.defaultSpacing = defaultSpacing;
        this.defaultSeparation = defaultSeparation;
    }
    
    @Override
    public String getSerializedName() {
        return name;
    }
    
    /** 允许的最大坡度（结构底部区域的高度差） */
    public int maxSlope() { return maxSlope; }
    
    /** 与道路的最大高度差 */
    public int maxHeightDiff() { return maxHeightDiff; }
    
    /** 托盘缓冲区宽度 */
    public int terraceBuffer() { return terraceBuffer; }
    
    /** 同类型结构的默认间距 */
    public int defaultSpacing() { return defaultSpacing; }
    
    /** 与其他结构的默认间距 */
    public int defaultSeparation() { return defaultSeparation; }
}
