package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.structures.StructureSystem;
import net.shiroha233.roadweaver.structures.api.StructureBlueprint;
import net.shiroha233.roadweaver.structures.pipeline.StructurePlacer;

import java.util.List;

/**
 * 路边结构放置服务
 * 
 * 职责：
 * 1. 根据道路段信息，决定在哪些位置放置路边结构
 * 2. 随机选择结构类型
 * 3. 计算放置位置（路侧偏移）和朝向（面向道路）
 * 4. 直接使用 WorldGenLevel 放置结构模板
 * 5. 通过结构索引检查间距，防止重复放置
 * 
 * 设计说明：
 * - 路边结构应该在道路两侧随机出现，不是每个路段都有
 * - 使用概率控制密度，通过配置调整
 * - 检查最小间距，避免结构过于密集
 * - 直接使用 WorldGenLevel 放置（而非 StructurePlacer），避免在 Feature 阶段死锁
 */
public final class RoadsideStructureService {
    private RoadsideStructureService() {}
    
    // 路边结构距离道路中心线的基础偏移（格）
    // 增大此值让结构离道路更远
    private static final int BASE_SIDE_OFFSET = 8;
    // 额外的随机偏移范围（结构可能在 BASE + 0 ~ BASE + RANDOM 之间）
    private static final int RANDOM_OFFSET_RANGE = 4;
    
    /**
     * 尝试在指定路段旁放置路边结构
     * 
     * @param world       世界（用于高度采样和检查）
     * @param server      服务端世界（用于实际放置）
     * @param middlePos   路段中心位置
     * @param prevPos     前一个路段位置（用于计算方向）
     * @param nextPos     后一个路段位置（用于计算方向）
     * @param roadWidth   道路宽度
     * @param segmentIndex 路段索引（用于控制放置频率）
     * @param random      随机源
     * @param cfg         模组配置
     * @return 如果成功放置则返回 true
     */
    public static boolean tryPlace(WorldGenLevel world,
                                   ServerLevel server,
                                   BlockPos middlePos,
                                   BlockPos prevPos,
                                   BlockPos nextPos,
                                   int roadWidth,
                                   int segmentIndex,
                                   RandomSource random,
                                   ModConfig cfg) {
        // 检查是否启用路边结构
        if (!cfg.roadsideStructuresEnabled()) {
            return false;
        }
        
        // 概率检查：不是每个路段都放置结构
        int interval = Math.max(1, cfg.roadsideStructureInterval());
        if (segmentIndex % interval != 0) {
            return false;
        }
        
        // 额外的随机概率
        float chance = Math.max(0f, Math.min(1f, cfg.roadsideStructureChance()));
        if (random.nextFloat() > chance) {
            return false;
        }
        
        // 计算道路方向向量
        int dx = nextPos.getX() - prevPos.getX();
        int dz = nextPos.getZ() - prevPos.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (len < 0.001) {
            return false;  // 方向不明确，跳过
        }
        
        // 归一化方向向量
        double dirX = dx / len;
        double dirZ = dz / len;
        
        // 计算垂直于道路的向量（正交向量）
        // 如果道路方向是 (dirX, dirZ)，则垂直方向是 (-dirZ, dirX)
        double orthoX = -dirZ;
        double orthoZ = dirX;
        
        // 随机选择左侧或右侧
        boolean leftSide = random.nextBoolean();
        double sideMultiplier = leftSide ? 1.0 : -1.0;
        
        // 计算侧向偏移距离
        int halfWidth = Math.max(1, roadWidth / 2);
        int sideOffset = BASE_SIDE_OFFSET + halfWidth + random.nextInt(RANDOM_OFFSET_RANGE + 1);
        
        // 计算放置位置
        int placeX = middlePos.getX() + (int) Math.round(orthoX * sideOffset * sideMultiplier);
        int placeZ = middlePos.getZ() + (int) Math.round(orthoZ * sideOffset * sideMultiplier);
        
        // 获取地表高度
        int placeY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, placeX, placeZ);
        BlockPos placePos = new BlockPos(placeX, placeY, placeZ);
        
        // 检查高度差：如果与道路高度差太大，不放置
        int heightDiff = Math.abs(placeY - middlePos.getY());
        if (heightDiff > 5) {
            return false;
        }
        
        // 随机选择结构类型
        RoadsideType type = RoadsideType.chooseWeighted(random);
        StructureBlueprint bp = RoadsideBlueprints.get(type);
        if (bp == null) {
            return false;
        }
        
        // 检查最小间距（separation 表示与其他结构的最小距离）
        int minDist = bp.spawnRule().separation();
        if (StructureSystem.index(server).existsNear(placePos, minDist)) {
            return false;
        }
        
        // 计算朝向：使结构面向道路
        Rotation rotation = calculateRotation(dirX, dirZ, leftSide, type.faceRoad());
        
        // 使用统一的 StructurePlacer 放置结构
        // withTerrace=true: 生成地形托盘
        // noBasement=true: 路边结构不带底座
        ResourceLocation templateId = type.templateId();
        return StructurePlacer.placeSimple(world, server, templateId, placePos, rotation, 
                                            true, true, random);
    }
    
    /**
     * 批量处理路段，尝试放置路边结构
     * 
     * @param world          世界
     * @param server         服务端世界
     * @param middlePositions 所有路段中心位置列表
     * @param roadWidth      道路宽度
     * @param random         随机源
     * @param cfg            配置
     * @param startIndex     起始索引（跳过首尾段）
     * @param endIndex       结束索引
     */
    public static void processSegments(WorldGenLevel world,
                                       ServerLevel server,
                                       List<BlockPos> middlePositions,
                                       int roadWidth,
                                       RandomSource random,
                                       ModConfig cfg,
                                       int startIndex,
                                       int endIndex) {
        if (middlePositions == null || middlePositions.size() < 5) {
            return;
        }
        
        int safeStart = Math.max(2, startIndex);
        int safeEnd = Math.min(middlePositions.size() - 3, endIndex);
        
        for (int i = safeStart; i < safeEnd; i++) {
            BlockPos middle = middlePositions.get(i);
            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);
            
            tryPlace(world, server, middle, prev, next, roadWidth, i, random, cfg);
        }
    }
    
    /**
     * 计算结构的旋转角度
     * 
     * @param dirX      道路方向 X 分量
     * @param dirZ      道路方向 Z 分量
     * @param leftSide  是否在左侧
     * @param faceRoad  是否需要面向道路
     * @return Minecraft 旋转枚举值
     */
    private static Rotation calculateRotation(double dirX, double dirZ, boolean leftSide, boolean faceRoad) {
        if (!faceRoad) {
            // 不需要特定朝向，返回随机旋转或默认
            return Rotation.NONE;
        }
        
        // 计算应该面向的方向
        // 如果在左侧，应该面向右（即道路方向的反方向的正交方向）
        // 简化处理：根据道路主方向决定旋转
        
        // 判断道路主要朝向
        double absX = Math.abs(dirX);
        double absZ = Math.abs(dirZ);
        
        if (absX > absZ) {
            // 道路主要是东西向
            if (leftSide) {
                // 在北侧，面向南
                return dirX > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
            } else {
                // 在南侧，面向北
                return dirX > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            }
        } else {
            // 道路主要是南北向
            if (leftSide) {
                // 在西侧，面向东
                return dirZ > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            } else {
                // 在东侧，面向西
                return dirZ > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            }
        }
    }
}
