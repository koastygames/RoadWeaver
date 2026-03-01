package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路完整数据（宽度、类型、材料、分段、跨度、目标高度）
 */
public record RoadData(
        int width,
        int roadType,
        List<BlockState> materials,
        List<BlockState> slabMaterials,
        List<RoadSegmentPlacement> roadSegmentList,
        List<RoadSpan> spans,
        List<Integer> targetY
) {
    public static final Codec<RoadData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("width").forGetter(RoadData::width),
                    Codec.INT.fieldOf("road_type").forGetter(RoadData::roadType),
                    BlockState.CODEC.listOf().fieldOf("materials").forGetter(RoadData::materials),
                    BlockState.CODEC.listOf().optionalFieldOf("slab_materials", new ArrayList<>()).forGetter(RoadData::slabMaterials),
                    RoadSegmentPlacement.CODEC.listOf().fieldOf("placements").forGetter(RoadData::roadSegmentList),
                    RoadSpan.CODEC.listOf().optionalFieldOf("spans", new ArrayList<>()).forGetter(RoadData::spans),
                    Codec.INT.listOf().optionalFieldOf("target_y", new ArrayList<>()).forGetter(RoadData::targetY)
            ).apply(instance, RoadData::new)
    );
}
