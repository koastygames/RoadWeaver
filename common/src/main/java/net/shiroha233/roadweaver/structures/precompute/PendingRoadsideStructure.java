package net.shiroha233.roadweaver.structures.precompute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/**
 * 待放置的路边结构信息
 * 
 * 在道路规划阶段预计算，存储到世界数据中。
 * 在区块 STRUCTURE_STARTS 阶段被 Mixin 注入到 StructureManager。
 */
public record PendingRoadsideStructure(
    ResourceLocation structureId,  // 结构 ID
    BlockPos anchor,               // 放置锚点
    Rotation rotation,             // 旋转
    int sizeX,                     // 结构尺寸 X
    int sizeY,                     // 结构尺寸 Y
    int sizeZ                      // 结构尺寸 Z
) {
    public static final Codec<PendingRoadsideStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("structure_id").forGetter(PendingRoadsideStructure::structureId),
            BlockPos.CODEC.fieldOf("anchor").forGetter(PendingRoadsideStructure::anchor),
            Codec.STRING.fieldOf("rotation").xmap(Rotation::valueOf, Rotation::name).forGetter(PendingRoadsideStructure::rotation),
            Codec.INT.fieldOf("size_x").forGetter(PendingRoadsideStructure::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(PendingRoadsideStructure::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(PendingRoadsideStructure::sizeZ)
        ).apply(instance, PendingRoadsideStructure::new)
    );
    
    /**
     * 获取结构所在的区块 X
     */
    public int chunkX() {
        return anchor.getX() >> 4;
    }
    
    /**
     * 获取结构所在的区块 Z
     */
    public int chunkZ() {
        return anchor.getZ() >> 4;
    }
    
    /**
     * 获取区块键（用于索引）
     */
    public long chunkKey() {
        return ((long) chunkX() << 32) | (chunkZ() & 0xFFFFFFFFL);
    }
}
