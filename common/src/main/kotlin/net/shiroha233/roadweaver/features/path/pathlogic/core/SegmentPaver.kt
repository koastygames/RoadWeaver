package net.shiroha233.roadweaver.features.path.pathlogic.core

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.decoration.material.surface.BiomeRoadMaterialSelector
import net.shiroha233.roadweaver.features.path.decoration.system.SurfacePlacementUtil
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadHeightInterpolator
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService

/**
 * 路面铺设器（重构版）
 * 核心改进：每个方块根据其在中心线上的投影位置独立计算高度，消除斜向爬坡锯齿问题。
 */
object SegmentPaver {
    /**
     * 铺设单个路段（新版：每方块独立高度）
     *
     * @param world 世界
     * @param seg 路段数据（中心点+覆盖方块列表）
     * @param segmentIndex 当前路段在中心线中的索引
     * @param centers 完整的道路中心点列表
     * @param targetY 每个中心点的目标高度数组
     * @param roadType 道路类型（0=人工，1=自然）
     * @param materials 路面材质列表
     * @param slabMaterials 半砖材质列表（可为空）
     * @param random 随机源
     * @param cfg 模组配置
     */
    @JvmStatic
    fun paveSegment(
        world: WorldGenLevel,
        seg: Records.RoadSegmentPlacement,
        segmentIndex: Int,
        centers: List<BlockPos>,
        targetY: IntArray,
        roadType: Int,
        materials: List<BlockState>,
        slabMaterials: List<BlockState>,
        random: RandomSource,
        cfg: ModConfig
    ) {
        val positions: List<BlockPos> = seg.positions
        if (positions.isEmpty()) return

        // 批量计算每个方块的插值高度（与 RoadTerrainAdapter.adaptWithInterpolation 使用同源插值器，保持语义一致）
        val heights = RoadHeightInterpolator.batchInterpolate(positions, segmentIndex, centers, targetY)

        for (i in positions.indices) {
            val widthBlock = positions[i]
            val y = heights[i]

            var placePos = BlockPos(widthBlock.x, y, widthBlock.z)

            // 水体处理：如果目标位置在流体内，把路面向上抬，避免“生成了但看不见/被水覆盖”
            if (!world.getFluidState(placePos).isEmpty) {
                placePos = placePos.above()
            } else if (!world.getFluidState(placePos.above()).isEmpty) {
                var cursor = placePos.above()
                var climb = 0
                while (climb < 32 && !world.getFluidState(cursor).isEmpty) {
                    cursor = cursor.above()
                    climb++
                }
                placePos = cursor
            }

            if (StructureAvoidanceService.shouldAvoid(world, placePos)) {
                continue
            }

            // 选择材质：自然道路按群系，人工道路按存档数据
            val baseMats: List<BlockState>
            val slabMats: List<BlockState>
            if (roadType == 1) {
                val result = BiomeRoadMaterialSelector.forBiomeWithSlabs(world, placePos)
                baseMats = result.baseMaterials
                slabMats = result.slabMaterials
            } else {
                baseMats = materials
                slabMats = slabMaterials
            }

            if (baseMats.isEmpty()) {
                continue
            }

            // 放置路面方块
            SurfacePlacementUtil.placeOnSurface(world, placePos, baseMats, roadType, random, cfg)

            // 配置了半砖：检查是否需要平滑过渡（自然/人工通用）
            if (slabMats.isNotEmpty()) {
                if (shouldPlaceSlab(widthBlock.x, widthBlock.z, y, centers, targetY)) {
                    var slabState = slabMats[random.nextInt(slabMats.size)]
                    if (slabState.block is SlabBlock) {
                        slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    }
                    world.setBlock(placePos, slabState, 3)
                }
            }
        }
    }

    private fun shouldPlaceSlab(
        x: Int,
        z: Int,
        currentY: Int,
        centers: List<BlockPos>,
        targetY: IntArray
    ): Boolean {
        val yAhead = RoadHeightInterpolator.getInterpolatedY(x + 1, z, centers, targetY)
        val yBehind = RoadHeightInterpolator.getInterpolatedY(x - 1, z, centers, targetY)
        val yLeft = RoadHeightInterpolator.getInterpolatedY(x, z + 1, centers, targetY)
        val yRight = RoadHeightInterpolator.getInterpolatedY(x, z - 1, centers, targetY)

        val needsSlabX = (yAhead > currentY && yBehind >= currentY) || (yBehind > currentY && yAhead >= currentY)
        val needsSlabZ = (yLeft > currentY && yRight >= currentY) || (yRight > currentY && yLeft >= currentY)

        return needsSlabX || needsSlabZ
    }
}
