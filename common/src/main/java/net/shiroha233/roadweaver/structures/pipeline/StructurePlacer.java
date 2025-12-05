package net.shiroha233.roadweaver.structures.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.shiroha233.roadweaver.structures.api.BlendProfile;
import net.shiroha233.roadweaver.structures.api.StructureBlueprint;
import net.shiroha233.roadweaver.structures.model.StructureInstance;
import net.shiroha233.roadweaver.structures.roadside.BeardedTerracePlacer;

import java.util.Optional;
import java.util.UUID;

/**
 * 结构放置统一入口
 * 
 * 所有结构放置都应通过此类，内部自动处理地形托盘。
 * 
 * 提供两种放置模式：
 * 1. place() - 完整模式，需要 StructureBlueprint，返回 StructureInstance
 * 2. placeSimple() - 轻量模式，只需模板 ID，返回是否成功
 * 
 * 地形托盘由 BeardedTerracePlacer 处理，调用方无需关心。
 */
public final class StructurePlacer {
    private StructurePlacer() {}
    
    // 默认地形托盘参数
    private static final int DEFAULT_TERRACE_BUFFER = 2;      // 结构周围缓冲区
    private static final int DEFAULT_TERRACE_TRANSITION = 4;  // 平滑过渡区宽度

    public static StructureInstance place(ServerLevel level, StructureBlueprint bp, ResourceLocation variantTemplateId,
                                          BlockPos anchor, Rotation rotation, Mirror mirror, AABB preBounds, BlendProfile blend) {
        // 允许模板 ID 既可为 "roadweaver:starting_cabin"，也可能误传 "roadweaver:structures/starting_cabin"
        ResourceLocation id = normalizeTemplateId(variantTemplateId);
        StructureTemplateManager mgr = level.getServer().getStructureManager();
        Optional<StructureTemplate> opt = mgr.get(id);
        if (opt.isEmpty()) {
            // fallback: 再尝试原 ID
            opt = mgr.get(variantTemplateId);
        }
        if (opt.isEmpty()) {
            // 获取失败时，返回占位实例
            AABB b = (preBounds != null) ? preBounds : new AABB(anchor).inflate(8, 5, 8);
            return new StructureInstance(UUID.randomUUID(), bp.id(), id, level.dimension().location(), anchor, b, level.getGameTime());
        }
        StructureTemplate tpl = opt.get();

        // 根据旋转计算放置尺寸
        Vec3i raw = tpl.getSize();
        Vec3i size = rotatedSize(raw, rotation);
        BlockPos min = anchor;
        BlockPos max = anchor.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        AABB bounds = new AABB(min, max);

        // 使用新的碗状地形托盘算法，自然融入地形
        // BeardedTerracePlacer 使用 smoothstep 函数实现平滑过渡，并自动修复暴露的泥土
        if (blend != null) {
            int centerX = (min.getX() + max.getX()) / 2;
            int centerZ = (min.getZ() + max.getZ()) / 2;
            int targetY = min.getY();
            
            // 计算结构半径（取长宽较大者的一半）
            int structureRadius = Math.max(size.getX(), size.getZ()) / 2;
            // 内环：结构范围 + 2格缓冲
            int innerRadius = structureRadius + 2;
            // 外环：内环 + 平滑过渡区（8-12格）
            int outerRadius = innerRadius + Math.max(8, blend.ringOuter() - blend.ringInner());
            
            BeardedTerracePlacer.buildTerraceForLargeStructure(level, centerX, centerZ, targetY, 
                                                                innerRadius, outerRadius, level.getRandom());
        }

        // 放置模板
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        tpl.placeInWorld(level, anchor, anchor, settings, level.getRandom(), 2);

        return new StructureInstance(UUID.randomUUID(), bp.id(), id, level.dimension().location(), anchor, bounds, level.getGameTime());
    }

    private static ResourceLocation normalizeTemplateId(ResourceLocation in) {
        String path = in.getPath();
        if (path.startsWith("structures/")) {
            return new ResourceLocation(in.getNamespace(), path.substring("structures/".length()));
        }
        return in;
    }

    private static Vec3i rotatedSize(Vec3i s, Rotation r) {
        return switch (r) {
            case NONE, CLOCKWISE_180 -> s;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(s.getZ(), s.getY(), s.getX());
        };
    }

    // ==================== 轻量级放置方法（用于路边结构等） ====================
    
    /**
     * 轻量级结构放置（用于 Feature 阶段，如路边结构）
     * 
     * 重构说明：
     * - 采用"先放置结构，托盘以结构实际包围盒为准"的策略
     * - 使用 StructurePlaceSettings.setRotationPivot 让结构围绕中心旋转
     * - 这样无论怎么旋转，结构中心始终对齐到 position
     * 
     * @param world        WorldGenLevel
     * @param server       ServerLevel（用于获取模板管理器）
     * @param templateId   模板 ID
     * @param position     放置位置（若 centerMode=true 则为中心点，否则为锚点）
     * @param rotation     旋转
     * @param withTerrace  是否生成地形托盘
     * @param noBasement   结构是否不带底座（影响地形高度计算）
     * @param centerMode   是否以中心点模式放置（结构几何中心对齐到 position）
     * @param random       随机源
     * @return 是否成功放置
     */
    public static boolean placeSimple(WorldGenLevel world,
                                       ServerLevel server,
                                       ResourceLocation templateId,
                                       BlockPos position,
                                       Rotation rotation,
                                       boolean withTerrace,
                                       boolean noBasement,
                                       boolean centerMode,
                                       RandomSource random) {
        // 获取模板
        StructureTemplateManager mgr = server.getStructureManager();
        Optional<StructureTemplate> opt = loadTemplate(mgr, templateId);
        if (opt.isEmpty()) {
            return false;
        }
        
        StructureTemplate tpl = opt.get();
        Vec3i rawSize = tpl.getSize();
        
        // 计算锚点和结构中心
        // Minecraft 结构放置：从锚点开始，向 +X/+Z 方向延伸
        // 旋转时结构会围绕锚点旋转，导致实际位置偏移
        // 
        // 简化方案：直接计算让结构中心落在 position 的锚点位置
        // 不依赖 getZeroPositionWithTransform，而是手动处理四种旋转情况
        
        BlockPos anchor;
        int structureCenterX, structureCenterZ;
        
        if (centerMode) {
            // 根据旋转计算锚点偏移，使结构中心对齐到 position
            // 原始结构：锚点在 (0,0)，中心在 (sizeX/2, sizeZ/2)
            // 旋转后中心位置会变化，需要反推锚点
            anchor = calculateAnchorForCenteredPlacement(position, rawSize, rotation);
            structureCenterX = position.getX();
            structureCenterZ = position.getZ();
        } else {
            anchor = position;
            // 非中心模式：结构从锚点向正方向延伸
            Vec3i rotatedSize = rotatedSize(rawSize, rotation);
            structureCenterX = anchor.getX() + rotatedSize.getX() / 2;
            structureCenterZ = anchor.getZ() + rotatedSize.getZ() / 2;
        }
        
        // 先生成地形托盘（以结构中心为圆心）
        if (withTerrace) {
            int targetY = noBasement ? position.getY() - 1 : position.getY();
            Vec3i rotatedSize = rotatedSize(rawSize, rotation);
            buildTerraceAtCenter(world, structureCenterX, structureCenterZ, rotatedSize, targetY, random);
        }
        
        // 放置模板
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);
        tpl.placeInWorld(world, anchor, anchor, settings, random, 2);
        
        return true;
    }
    
    /**
     * 计算让结构中心对齐到目标位置的锚点
     * 
     * Minecraft 结构放置行为：
     * - 结构从锚点开始放置
     * - 旋转围绕锚点进行
     * - NONE: 结构占用 [anchor, anchor + size)
     * - CLOCKWISE_90: X轴变Z轴，Z轴变-X轴
     * - CLOCKWISE_180: X变-X，Z变-Z
     * - COUNTERCLOCKWISE_90: X轴变-Z轴，Z轴变X轴
     * 
     * 为了让结构中心对齐到目标位置，需要计算正确的锚点偏移。
     */
    private static BlockPos calculateAnchorForCenteredPlacement(BlockPos center, Vec3i rawSize, Rotation rotation) {
        int sizeX = rawSize.getX();
        int sizeZ = rawSize.getZ();
        
        // 根据旋转计算锚点偏移
        // 目标：让旋转后结构的几何中心落在 center
        return switch (rotation) {
            case NONE -> {
                // 结构占用 [anchor, anchor+size)，中心在 anchor + size/2
                yield new BlockPos(center.getX() - sizeX / 2, center.getY(), center.getZ() - sizeZ / 2);
            }
            case CLOCKWISE_90 -> {
                // 旋转90度后，原来的 +X 方向变成 +Z，原来的 +Z 方向变成 -X
                // 结构占用区域变成 [anchor.x - sizeZ + 1, anchor.x] x [anchor.z, anchor.z + sizeX)
                // 中心在 (anchor.x - sizeZ/2, anchor.z + sizeX/2)
                yield new BlockPos(center.getX() + sizeZ / 2, center.getY(), center.getZ() - sizeX / 2);
            }
            case CLOCKWISE_180 -> {
                // 旋转180度后，结构占用 [anchor.x - sizeX + 1, anchor.x] x [anchor.z - sizeZ + 1, anchor.z]
                // 中心在 (anchor.x - sizeX/2, anchor.z - sizeZ/2)
                yield new BlockPos(center.getX() + sizeX / 2, center.getY(), center.getZ() + sizeZ / 2);
            }
            case COUNTERCLOCKWISE_90 -> {
                // 旋转270度后，原来的 +X 方向变成 -Z，原来的 +Z 方向变成 +X
                // 结构占用区域变成 [anchor.x, anchor.x + sizeZ) x [anchor.z - sizeX + 1, anchor.z]
                // 中心在 (anchor.x + sizeZ/2, anchor.z - sizeX/2)
                yield new BlockPos(center.getX() - sizeZ / 2, center.getY(), center.getZ() + sizeX / 2);
            }
        };
    }
    
    /**
     * 轻量级结构放置（兼容旧调用，默认非中心模式）
     */
    public static boolean placeSimple(WorldGenLevel world,
                                       ServerLevel server,
                                       ResourceLocation templateId,
                                       BlockPos anchor,
                                       Rotation rotation,
                                       boolean withTerrace,
                                       boolean noBasement,
                                       RandomSource random) {
        return placeSimple(world, server, templateId, anchor, rotation, withTerrace, noBasement, false, random);
    }
    
    /**
     * 轻量级结构放置（ServerLevel 版本，用于运行时放置）
     */
    public static boolean placeSimple(ServerLevel level,
                                       ResourceLocation templateId,
                                       BlockPos anchor,
                                       Rotation rotation,
                                       boolean withTerrace,
                                       boolean noBasement) {
        StructureTemplateManager mgr = level.getStructureManager();
        Optional<StructureTemplate> opt = loadTemplate(mgr, templateId);
        if (opt.isEmpty()) {
            return false;
        }
        
        StructureTemplate tpl = opt.get();
        Vec3i size = tpl.getSize(rotation);
        
        if (withTerrace) {
            int targetY = noBasement ? anchor.getY() - 1 : anchor.getY();
            int centerX = anchor.getX() + size.getX() / 2;
            int centerZ = anchor.getZ() + size.getZ() / 2;
            int structureRadius = Math.max(size.getX(), size.getZ()) / 2;
            int innerRadius = structureRadius + DEFAULT_TERRACE_BUFFER;
            int outerRadius = innerRadius + DEFAULT_TERRACE_TRANSITION;

            BeardedTerracePlacer.buildTerraceForLargeStructure(level, centerX, centerZ, targetY,
                    innerRadius, outerRadius, level.getRandom());
        }
        
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);
        tpl.placeInWorld(level, anchor, anchor, settings, level.getRandom(), 2);
        
        return true;
    }
    
    // ==================== 内部工具方法 ====================
    
    /**
     * 加载模板（尝试多种路径格式）
     */
    private static Optional<StructureTemplate> loadTemplate(StructureTemplateManager mgr, ResourceLocation templateId) {
        // 尝试原始 ID
        Optional<StructureTemplate> opt = mgr.get(templateId);
        if (opt.isPresent()) {
            return opt;
        }
        
        // 尝试添加 structures/ 前缀
        ResourceLocation altId = new ResourceLocation(templateId.getNamespace(), "structures/" + templateId.getPath());
        opt = mgr.get(altId);
        if (opt.isPresent()) {
            return opt;
        }
        
        // 尝试移除 structures/ 前缀
        String path = templateId.getPath();
        if (path.startsWith("structures/")) {
            ResourceLocation cleanId = new ResourceLocation(templateId.getNamespace(), path.substring("structures/".length()));
            return mgr.get(cleanId);
        }
        
        return Optional.empty();
    }
    
    /**
     * 以指定中心点生成地形托盘
     */
    private static void buildTerraceAtCenter(WorldGenLevel world, int centerX, int centerZ, Vec3i size, int targetY, RandomSource random) {
        // 计算半径
        double innerRadius = Math.max(size.getX(), size.getZ()) / 2.0 + DEFAULT_TERRACE_BUFFER;
        double outerRadius = innerRadius + DEFAULT_TERRACE_TRANSITION;
        BeardedTerracePlacer.buildTerraceByCenter(world, centerX, centerZ, targetY, (int) innerRadius, (int) outerRadius, random);
    }
}
