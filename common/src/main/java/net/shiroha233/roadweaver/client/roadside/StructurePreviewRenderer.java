package net.shiroha233.roadweaver.client.roadside;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.structures.roadside.RoadsideDecorationSpec;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 结构模板预览渲染器：在 GUI 中绘制一个小型 3D 预览。
 * 职责单一：加载模板 -> 缓存 -> 在指定矩形内渲染。
 */
public final class StructurePreviewRenderer {
    private final Minecraft mc = Minecraft.getInstance();
    private final BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
    private final Map<ResourceLocation, StructureTemplate> cache = new HashMap<>();

    /**
     * 在 GUI 中渲染指定结构的 3D 预览。
     *
     * @param spec        路边结构规格
     * @param gfx         GUI 画布
     * @param x           左上角 X
     * @param y           左上角 Y
     * @param width       预览区域宽度
     * @param height      预览区域高度
     * @param partialTick 渲染帧插值
     * @return 若模板成功渲染返回 true，否则 false
     */
    public boolean render(RoadsideDecorationSpec spec, net.minecraft.client.gui.GuiGraphics gfx,
                          int x, int y, int width, int height, float partialTick) {
        StructureTemplate tpl = getTemplate(spec.templateId());
        List<StructureTemplate.Palette> palettes = tpl != null ? getPalettes(tpl) : List.of();
        if (tpl == null || palettes.isEmpty()) {
            return false;
        }

        var pose = gfx.pose();
        pose.pushPose();
        RenderSystem.enableDepthTest();

        // 视口：将原点移到区域正中心
        pose.translate(x + width / 2f, y + height / 2f, 200.0f);

        // 计算尺度：保证最大的维度适配到预览框
        var size = tpl.getSize();
        float maxDim = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        float scale = Math.min(width, height) / (maxDim * 2.0f);
        // GUI 坐标系 Y 轴向下，翻转 Y 使模型正立
        pose.scale(scale, -scale, scale);

        // 缓慢自转 + 俯视角
        float rotation = (mc.level != null ? (mc.level.getGameTime() + partialTick) : (mc.gui.getGuiTicks() + partialTick)) % 360;
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.mulPose(Axis.XP.rotationDegrees(25f));

        // 将结构几何中心对齐到原点
        float centerX = size.getX() / 2f;
        float centerY = size.getY() / 2f;
        float centerZ = size.getZ() / 2f;
        pose.translate(-centerX, -centerY, -centerZ);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        var palette = palettes.get(0);
        for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
            BlockState state = info.state();
            if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

            pose.pushPose();
            BlockPos pos = info.pos();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderer.renderSingleBlock(state, pose, buffer, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        buffer.endBatch();
        RenderSystem.disableDepthTest();
        pose.popPose();
        return true;
    }

    private StructureTemplate getTemplate(ResourceLocation id) {
        return cache.computeIfAbsent(id, key -> {
            // 单机模式可直接使用本地服务端的模板管理器；多人游戏时返回 null（预览不可用）
            if (mc.getSingleplayerServer() == null) {
                return null;
            }
            return mc.getSingleplayerServer().getStructureManager().get(key).orElse(null);
        });
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.Palette> getPalettes(StructureTemplate tpl) {
        try {
            Field f = StructureTemplate.class.getDeclaredField("palettes");
            f.setAccessible(true);
            Object val = f.get(tpl);
            if (val instanceof List<?> list) {
                return (List<StructureTemplate.Palette>) list;
            }
        } catch (Exception ignored) {}
        return List.of();
    }
}
