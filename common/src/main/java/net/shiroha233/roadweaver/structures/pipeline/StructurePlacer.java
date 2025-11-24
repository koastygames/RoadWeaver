package net.shiroha233.roadweaver.structures.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.shiroha233.roadweaver.structures.api.BlendProfile;
import net.shiroha233.roadweaver.structures.api.StructureBlueprint;
import net.shiroha233.roadweaver.structures.model.StructureInstance;
import net.shiroha233.roadweaver.structures.terrain.NoiseTerracePlacer;

import java.util.Optional;
import java.util.UUID;

public final class StructurePlacer {
    private StructurePlacer() {}

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

        // 使用噪声生成圆形碗状托盘，自然融入地形
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
            
            NoiseTerracePlacer.buildNoisyTerrace(level, centerX, centerZ, targetY, 
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

    // 旧的托盘系统方法已移除，现使用 NoiseTerracePlacer 生成自然融入的圆形碗状托盘
}
