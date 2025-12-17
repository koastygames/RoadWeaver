package net.shiroha233.roadweaver.helpers

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.util.ArrayList

/**
 * 公共数据记录与编解码器（Common）
 * 注意：保持与现有代码的字段访问兼容；允许在 Codec 字段名上与历史数据不完全一致。
 */
object Records {
    data class WoodAssets(val fence: Block, val hangingSign: Block, val planks: Block)

    /**
     * 单个结构位置与类型
     */
    data class StructureInfo(val pos: BlockPos, val structureId: String) {
        companion object {
            @JvmField
            val CODEC: Codec<StructureInfo> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(StructureInfo::pos),
                    Codec.STRING.optionalFieldOf("structure_id", "unknown").forGetter(StructureInfo::structureId)
                ).apply(instance, ::StructureInfo)
            }
        }
    }

    /**
     * 结构位置集合（升级版，包含类型信息）
     */
    class StructureLocationData(
        structureLocations: List<BlockPos>?,
        structureInfos: List<StructureInfo>?
    ) {
        val structureLocations: MutableList<BlockPos> = ArrayList(structureLocations ?: ArrayList())
        val structureInfos: MutableList<StructureInfo> = ArrayList(structureInfos ?: ArrayList())

        constructor(structureLocations: List<BlockPos>?) : this(structureLocations, ArrayList())

        fun addStructure(pos: BlockPos) {
            structureLocations.add(pos)
        }

        fun addStructureInfo(info: StructureInfo) {
            structureInfos.add(info)
            if (!structureLocations.contains(info.pos)) {
                structureLocations.add(info.pos)
            }
        }

        companion object {
            // 兼容历史数据：支持旧格式（只有 BlockPos 列表）和新格式（包含 StructureInfo）
            @JvmField
            val CODEC: Codec<StructureLocationData> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockPos.CODEC.listOf().optionalFieldOf("structure_locations", ArrayList()).forGetter { it.structureLocations.toList() },
                    StructureInfo.CODEC.listOf().optionalFieldOf("structure_infos", ArrayList()).forGetter { it.structureInfos.toList() }
                ).apply(instance) { locs, infos ->
                    StructureLocationData(locs, infos)
                }
            }
        }
    }

    /**
     * 结构连接状态
     */
    enum class ConnectionStatus {
        PLANNED,
        GENERATING,
        COMPLETED,
        FAILED
    }

    /**
     * 结构连接（from -> to + 状态）
     */
    data class StructureConnection(val from: BlockPos, val to: BlockPos, val status: ConnectionStatus) {
        constructor(from: BlockPos, to: BlockPos) : this(from, to, ConnectionStatus.PLANNED)

        companion object {
            @JvmField
            val CODEC: Codec<StructureConnection> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("from").forGetter(StructureConnection::from),
                    BlockPos.CODEC.fieldOf("to").forGetter(StructureConnection::to),
                    Codec.STRING.optionalFieldOf("status", "PLANNED")
                        .xmap(ConnectionStatus::valueOf) { it.name }
                        .forGetter(StructureConnection::status)
                ).apply(instance, ::StructureConnection)
            }
        }
    }

    data class RoadSegmentPlacement(val middlePos: BlockPos, val positions: List<BlockPos>) {
        companion object {
            @JvmField
            val CODEC: Codec<RoadSegmentPlacement> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("middle_pos").forGetter(RoadSegmentPlacement::middlePos),
                    BlockPos.CODEC.listOf().fieldOf("positions").forGetter(RoadSegmentPlacement::positions)
                ).apply(instance, ::RoadSegmentPlacement)
            }
        }
    }

    enum class SpanType {
        BRIDGE,
        TUNNEL
    }

    data class RoadSpan(val start: BlockPos, val end: BlockPos, val type: SpanType) {
        companion object {
            @JvmField
            val CODEC: Codec<RoadSpan> = RecordCodecBuilder.create { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("start").forGetter(RoadSpan::start),
                    BlockPos.CODEC.fieldOf("end").forGetter(RoadSpan::end),
                    Codec.STRING.fieldOf("type")
                        .xmap(SpanType::valueOf, Enum<*>::name)
                        .forGetter(RoadSpan::type)
                ).apply(instance, ::RoadSpan)
            }
        }
    }

    data class RoadData(
        val width: Int,
        val roadType: Int,
        val materials: List<BlockState>,
        val slabMaterials: List<BlockState>,
        val roadSegmentList: List<RoadSegmentPlacement>,
        val spans: List<RoadSpan>,
        val targetY: List<Int>
    ) {
        companion object {
            @JvmField
            val CODEC: Codec<RoadData> = RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.INT.fieldOf("width").forGetter(RoadData::width),
                    Codec.INT.fieldOf("road_type").forGetter(RoadData::roadType),
                    BlockState.CODEC.listOf().fieldOf("materials").forGetter(RoadData::materials),
                    BlockState.CODEC.listOf().optionalFieldOf("slab_materials", ArrayList()).forGetter(RoadData::slabMaterials),
                    RoadSegmentPlacement.CODEC.listOf().fieldOf("placements").forGetter(RoadData::roadSegmentList),
                    RoadSpan.CODEC.listOf().optionalFieldOf("spans", ArrayList()).forGetter(RoadData::spans),
                    Codec.INT.listOf().optionalFieldOf("target_y", ArrayList()).forGetter(RoadData::targetY)
                ).apply(instance, ::RoadData)
            }
        }
    }
}
