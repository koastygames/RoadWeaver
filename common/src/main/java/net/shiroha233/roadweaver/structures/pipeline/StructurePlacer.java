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
            AABB b = (preBounds != null) ? preBounds : new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 1.0, anchor.getY() + 1.0, anchor.getZ() + 1.0).inflate(8, 5, 8);
            return new StructureInstance(UUID.randomUUID(), bp.id(), id, level.dimension().location(), anchor, b, level.getGameTime());
        }
        StructureTemplate tpl = opt.get();

        // 根据旋转计算放置尺寸
        Vec3i raw = tpl.getSize();
        Vec3i size = rotatedSize(raw, rotation);
        BlockPos min = anchor;
        BlockPos max = anchor.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);

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
        if (path.startsWith("structure/")) {
            return ResourceLocation.fromNamespaceAndPath(in.getNamespace(), path.substring("structure/".length()));
        }
        if (path.startsWith("structures/")) {
            return ResourceLocation.fromNamespaceAndPath(in.getNamespace(), path.substring("structures/".length()));
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

        // 计算锚点与结构中心
        BlockPos anchor;
        int structureCenterX;
        int structureCenterZ;

        if (centerMode) {
            // 使用精确的中心对齐计算，保证旋转后几何中心落在 position
            anchor = calculateAnchorForCenteredPlacement(position, rawSize, rotation);
            structureCenterX = position.getX();
            structureCenterZ = position.getZ();
        } else {
            anchor = position;
            Vec3i rotatedSize = rotatedSize(rawSize, rotation);
            structureCenterX = anchor.getX() + rotatedSize.getX() / 2;
            structureCenterZ = anchor.getZ() + rotatedSize.getZ() / 2;
        }

        // 生成地形托盘（以结构中心为圆心）
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
        // 先尝试原始 ID
        Optional<StructureTemplate> opt = mgr.get(templateId);
        if (opt.isPresent()) {
            return opt;
        }
        
        // 再尝试添加 "structure/" 前缀
        ResourceLocation altId = ResourceLocation.fromNamespaceAndPath(templateId.getNamespace(), "structure/" + templateId.getPath());
        opt = mgr.get(altId);
        if (opt.isPresent()) {
            return opt;
        }
        
        // 最后尝试移除 "structure/" 前缀
        String path = templateId.getPath();
        if (path.startsWith("structure/")) {
            ResourceLocation cleanId = ResourceLocation.fromNamespaceAndPath(templateId.getNamespace(), path.substring("structure/".length()));
            opt = mgr.get(cleanId);
            if (opt.isPresent()) {
                return opt;
            }
        }
        
        return Optional.empty();
    }
    
    private static BlockPos calculateAnchorForCenteredPlacement(BlockPos center, Vec3i rawSize, Rotation rotation) {
        int sizeX = rawSize.getX();
        int sizeZ = rawSize.getZ();

        return switch (rotation) {
            case NONE -> new BlockPos(center.getX() - sizeX / 2, center.getY(), center.getZ() - sizeZ / 2);
            case CLOCKWISE_90 -> new BlockPos(center.getX() + sizeZ / 2, center.getY(), center.getZ() - sizeX / 2);
            case CLOCKWISE_180 -> new BlockPos(center.getX() + sizeX / 2, center.getY(), center.getZ() + sizeZ / 2);
            case COUNTERCLOCKWISE_90 -> new BlockPos(center.getX() - sizeZ / 2, center.getY(), center.getZ() + sizeX / 2);
        };
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
