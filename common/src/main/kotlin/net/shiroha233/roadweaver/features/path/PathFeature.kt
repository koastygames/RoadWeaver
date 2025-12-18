package net.shiroha233.roadweaver.features.path

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.bridge.BuoyBuilder
import net.shiroha233.roadweaver.features.path.bridge.BuoyMarkerPlanner
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService
import net.shiroha233.roadweaver.features.path.pathlogic.surface.BridgeTransitionAdjuster
import net.shiroha233.roadweaver.features.path.pathlogic.surface.HeightProfileService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage
import java.util.HashSet

class PathFeature(codec: Codec<PathFeatureConfig>) : Feature<PathFeatureConfig>(codec) {
    override fun place(ctx: FeaturePlaceContext<PathFeatureConfig>): Boolean {
        val world = ctx.level()
        val lvl: Level = world.level
        val server = (lvl as? ServerLevel) ?: return false

        val currentChunk = ChunkPos(ctx.origin())
        val minX = currentChunk.minBlockX
        val minZ = currentChunk.minBlockZ
        val maxX = currentChunk.maxBlockX
        val maxZ = currentChunk.maxBlockZ

        val roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ)
        if (roadDataList.isNullOrEmpty()) return false

        val random = ctx.random()
        val cfg: ModConfig = ConfigService.get()
        val averagingRadius = maxOf(0, cfg.averagingRadius())

        val processedMiddle = HashSet<BlockPos>()
        val decorations = HashSet<Decoration>()

        for (data in roadDataList) {
            processRoadDataInChunk(world, server, currentChunk, data, processedMiddle, decorations, random, cfg, averagingRadius)
        }

        DecorationExecutor.tryPlaceDecorations(decorations)
        return true
    }

    companion object {
        private fun processRoadDataInChunk(
            world: WorldGenLevel,
            server: ServerLevel,
            currentChunk: ChunkPos,
            data: Records.RoadData,
            processedMiddle: MutableSet<BlockPos>,
            decorations: MutableSet<Decoration>,
            random: RandomSource,
            cfg: ModConfig,
            averagingRadius: Int
        ) {
            val roadType = data.roadType
            val roadWidth = maxOf(1, data.width)
            val materials: List<BlockState> = data.materials
            val slabMaterials: List<BlockState> = data.slabMaterials
            val segments: List<Records.RoadSegmentPlacement> = data.roadSegmentList
            if (segments.size < 5) return

            val middlePositions = segments.map { it.middlePos }
            val res = BridgeRangeCalculator.compute(middlePositions, data.spans)
            val isBridge = res.isBridge
            val bridgeRanges = res.mergedRanges
            val skipSegments = res.skipSegments

            val useBuoysInstead = cfg.bridgeEnabled() && cfg.bridgeUseBuoysInstead()
            val useBuoysWhenSkipped = cfg.bridgeEnabled() && cfg.bridgeUseBuoysWhenSkipped()

            val intervalBlocks = maxOf(4, cfg.buoyIntervalBlocks())
            val buoyMarkersForBridge = if (useBuoysInstead) BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks) else null
            val buoyMarkersForSkipped = if (useBuoysWhenSkipped) BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks) else null

            val targetY: List<Int> = data.targetY
            val hp = HeightProfileService.build(world, middlePositions, currentChunk, averagingRadius, cfg, targetY)
            val usePersisted = hp.usePersisted
            val smoothedYArr = hp.smoothedY

            var baseYArr: IntArray? = if (usePersisted && targetY.size == middlePositions.size) targetY.toIntArray() else smoothedYArr

            if (baseYArr != null && cfg.bridgeEnabled() && !useBuoysInstead && bridgeRanges.isNotEmpty()) {
                baseYArr = BridgeTransitionAdjuster.adjust(baseYArr, bridgeRanges, cfg)
            }

            val deckY = server.seaLevel + cfg.bridgeDeckClearance()

            var segmentIndex = 0
            val bridgeCtx = BridgeSegmentPlanner.newContext()

            for (i in 2 until (segments.size - 2)) {
                val middle = middlePositions[i]
                if (!processedMiddle.add(middle)) continue

                segmentIndex++
                if (segmentIndex < 8 || segmentIndex > segments.size - 8) continue

                val middleChunk = ChunkPos(middle)
                if (!middleChunk.equals(currentChunk)) continue

                val prev = middlePositions[i - 2]
                val next = middlePositions[i + 2]

                val sea = server.seaLevel
                val motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.x, middle.z)
                val surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, middle.x, middle.z)
                val topYCenter = if (motion > sea + 2) motion else surface
                val averaged = BlockPos(middle.x, topYCenter, middle.z)
                val baseYForThis = baseYArr?.get(i) ?: topYCenter

                val seg = segments[i]
                if (StructureAvoidanceService.shouldAvoid(world, middle)) {
                    continue
                }

                if (i in skipSegments.indices && skipSegments[i]) {
                    // 超长水域跨度：整段跳过生成
                    if (useBuoysWhenSkipped && buoyMarkersForSkipped != null && i in buoyMarkersForSkipped.indices && buoyMarkersForSkipped[i]) {
                        BuoyBuilder.placeBuoy(world, middle, server.seaLevel, random, cfg)
                    }
                    continue
                }

                if (useBuoysInstead && i in isBridge.indices && isBridge[i]) {
                    if (buoyMarkersForBridge != null && i in buoyMarkersForBridge.indices && buoyMarkersForBridge[i]) {
                        BuoyBuilder.placeBuoy(world, middle, server.seaLevel, random, cfg)
                    }
                    // 浮标模式：水域跨度不放桥也不铺路，避免在水里生成“路堤”
                    continue
                }

                if (cfg.bridgeEnabled() && isBridge[i]) {
                    BridgeSegmentPlanner.processSegment(
                        world,
                        seg,
                        middle,
                        prev,
                        next,
                        roadWidth,
                        baseYForThis,
                        deckY,
                        segmentIndex,
                        random,
                        cfg,
                        bridgeRanges,
                        baseYArr,
                        i,
                        bridgeCtx
                    )
                } else {
                    // 对非桥梁路段进行地形适配（填土/削坡/边缘平滑）
                    // 使用插值高度计算，确保与路面铺设的高度一致
                    net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter.adaptWithInterpolation(
                        world,
                        middle,
                        i,
                        middlePositions,
                        baseYArr,
                        roadWidth,
                        random,
                        cfg
                    )

                    val yArr = baseYArr ?: return
                    SegmentPaver.paveSegment(world, seg, i, middlePositions, yArr, roadType, materials, slabMaterials, random, cfg)

                    // 跨海被跳过（超长水域跨度）时：在两端岸边放置提示路牌
                    // 仅在“岸边正常路段”触发一次；真正落地仍由 Decoration.placeAllowed 做表面与禁放判断
                    if (cfg.roadSignsEnabled()) {
                        SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world,
                            decorations,
                            averaged,
                            next,
                            prev,
                            roadWidth,
                            skipSegments,
                            i
                        )
                    }
                }

                if (!isBridge[i] || cfg.bridgeKeepLamps()) {
                    DecorationPlanner.addDecoration(
                        world,
                        decorations,
                        averaged,
                        segmentIndex,
                        next,
                        prev,
                        middlePositions,
                        roadWidth,
                        random,
                        cfg,
                        if (roadType == 0) DecorationPlanner.Mode.ARTIFICIAL else DecorationPlanner.Mode.NATURAL
                    )
                }
            }
        }
    }
}
