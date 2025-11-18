package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;
import java.util.function.IntUnaryOperator;

final class MapRenderers {
    private MapRenderers() {}

    interface SegmentInView { boolean test(int x1, int z1, int x2, int z2); }

    static void renderGrid(GuiGraphics g,
                           Font font,
                           int mapX, int mapY, int mapW, int mapH,
                           int innerPad,
                           double viewMinX, double viewMaxX, double viewMinZ, double viewMaxZ,
                           int colorGrid, int gridTargetPx, int colorText) {
        GridRenderer.render(g, font, mapX, mapY, mapW, mapH, innerPad,
                viewMinX, viewMaxX, viewMinZ, viewMaxZ, colorGrid, gridTargetPx, colorText);
    }

    static void renderStructures(GuiGraphics g,
                                 List<BlockPos> points,
                                 IntUnaryOperator toScreenX,
                                 IntUnaryOperator toScreenY,
                                 java.util.function.BiPredicate<Integer, Integer> isInViewWorld,
                                 int size,
                                 int color,
                                 int left, int top, int right, int bottom) {
        for (BlockPos p : points) {
            int bx = p.getX();
            int bz = p.getZ();
            if (!isInViewWorld.test(bx, bz)) continue;
            int x = toScreenX.applyAsInt(bx);
            int y = toScreenY.applyAsInt(bz);
            RenderUtils.drawPoint(g, x, y, size, color, left, top, right, bottom);
        }
    }

    static void renderConnections(GuiGraphics g,
                                  List<Records.StructureConnection> connections,
                                  SegmentInView segmentInView,
                                  IntUnaryOperator toScreenX,
                                  IntUnaryOperator toScreenY,
                                  int thickness,
                                  int colorPlanned, int colorGenerating, int colorCompleted, int colorFailed,
                                  int left, int top, int right, int bottom) {
        for (Records.StructureConnection c : connections) {
            int fx = c.from().getX();
            int fz = c.from().getZ();
            int tx = c.to().getX();
            int tz = c.to().getZ();
            if (!segmentInView.test(fx, fz, tx, tz)) continue;
            int x1 = toScreenX.applyAsInt(fx);
            int y1 = toScreenY.applyAsInt(fz);
            int x2 = toScreenX.applyAsInt(tx);
            int y2 = toScreenY.applyAsInt(tz);
            switch (c.status()) {
                case PLANNED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorPlanned, thickness, left, top, right, bottom);
                }
                case GENERATING -> {
                    RenderUtils.drawThickDashedLine(g, x1, y1, x2, y2, colorGenerating, thickness, 8, 6, left, top, right, bottom);
                }
                case COMPLETED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorCompleted, thickness, left, top, right, bottom);
                }
                case FAILED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorFailed, thickness, left, top, right, bottom);
                }
            }
        }
    }

    static void renderRoadPolylines(GuiGraphics g,
                                    List<List<BlockPos>> polylines,
                                    SegmentInView segmentInView,
                                    IntUnaryOperator toScreenX,
                                    IntUnaryOperator toScreenY,
                                    int thickness,
                                    int color,
                                    int left, int top, int right, int bottom,
                                    int lodStepBlocks) {
        for (List<BlockPos> pl : polylines) {
            if (pl == null || pl.size() < 2) continue;
            BlockPos keep = pl.get(0);
            for (int i = 1; i < pl.size(); i++) {
                BlockPos curr = pl.get(i);
                int dx = curr.getX() - keep.getX();
                int dz = curr.getZ() - keep.getZ();
                int adx = Math.abs(dx);
                int adz = Math.abs(dz);
                int manhattan = adx + adz;
                if (manhattan < lodStepBlocks) continue;
                int x1w = keep.getX();
                int z1w = keep.getZ();
                int x2w = curr.getX();
                int z2w = curr.getZ();
                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    int x1 = toScreenX.applyAsInt(x1w);
                    int y1 = toScreenY.applyAsInt(z1w);
                    int x2 = toScreenX.applyAsInt(x2w);
                    int y2 = toScreenY.applyAsInt(z2w);
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom);
                }
                keep = curr;
            }
            BlockPos tail = pl.get(pl.size() - 1);
            if (tail != keep) {
                int x1w = keep.getX();
                int z1w = keep.getZ();
                int x2w = tail.getX();
                int z2w = tail.getZ();
                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    int x1 = toScreenX.applyAsInt(x1w);
                    int y1 = toScreenY.applyAsInt(z1w);
                    int x2 = toScreenX.applyAsInt(x2w);
                    int y2 = toScreenY.applyAsInt(z2w);
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom);
                }
            }
        }
    }

    static void renderLegend(GuiGraphics g,
                             Font font,
                             int rightBound,
                             int startY,
                             int gap,
                             int colorText,
                             int colorStruct,
                             int colorPlanned,
                             int colorGenerating,
                             int colorCompleted,
                             int colorFailed,
                             int structuresCount,
                             int plannedCount,
                             int generatingCount,
                             int completedCount,
                             int failedCount) {
        int y = startY;
        Component l1 = Component.translatable("gui.roadweaver.map.legend.structures").append(": ").append(Integer.toString(structuresCount));
        int w1 = font.width(l1);
        int x1 = rightBound - w1;
        int sr = x1 - gap;
        g.fill(sr - 5, y + 1, sr, y + 6, colorStruct);
        g.drawString(font, l1, x1, y, colorText, false);

        y += 16;
        Component l2 = Component.translatable("gui.roadweaver.map.legend.planned").append(": ").append(Integer.toString(plannedCount));
        int w2 = font.width(l2);
        int x2 = rightBound - w2;
        sr = x2 - gap;
        g.fill(sr - 28, y + 2, sr, y + 7, colorPlanned);
        g.drawString(font, l2, x2, y, colorText, false);

        y += 16;
        Component l3 = Component.translatable("gui.roadweaver.map.legend.generating").append(": ").append(Integer.toString(generatingCount));
        int w3 = font.width(l3);
        int x3 = rightBound - w3;
        sr = x3 - gap;
        int cy = y + 4;
        RenderUtils.drawThickDashedLine(g, sr - 28, cy, sr, cy, colorGenerating, 5, 8, 6, sr - 28, y + 1, sr, y + 8);
        g.drawString(font, l3, x3, y, colorText, false);

        y += 16;
        Component l4 = Component.translatable("gui.roadweaver.map.legend.completed").append(": ").append(Integer.toString(completedCount));
        int w4 = font.width(l4);
        int x4 = rightBound - w4;
        sr = x4 - gap;
        g.fill(sr - 28, y + 2, sr, y + 7, colorCompleted);
        g.drawString(font, l4, x4, y, colorText, false);

        y += 16;
        Component l5 = Component.translatable("gui.roadweaver.map.legend.failed").append(": ").append(Integer.toString(failedCount));
        int w5 = font.width(l5);
        int x5 = rightBound - w5;
        sr = x5 - gap;
        g.fill(sr - 28, y + 2, sr, y + 7, colorFailed);
        g.drawString(font, l5, x5, y, colorText, false);
    }

    static void drawPlayerArrow(GuiGraphics g,
                                int sx, int sy,
                                float yawDeg,
                                int tipLen,
                                int baseLen,
                                int baseHalfWidth,
                                int color,
                                int left, int top, int right, int bottom,
                                double pxPerBlockX,
                                double pxPerBlockZ) {
        double rx = Math.toRadians(yawDeg);
        double vx = -Math.sin(rx);
        double vz = Math.cos(rx);
        double dirX = vx * pxPerBlockX;
        double dirY = vz * pxPerBlockZ;
        double mag = Math.hypot(dirX, dirY);
        if (mag < 1e-6) { dirX = 1; dirY = 0; mag = 1; }
        dirX /= mag; dirY /= mag;
        double px = sx + dirX * tipLen;
        double py = sy + dirY * tipLen;
        double bx = sx - dirX * baseLen;
        double by = sy - dirY * baseLen;
        double perpX = -dirY;
        double perpY = dirX;
        double bx1 = bx + perpX * baseHalfWidth;
        double by1 = by + perpY * baseHalfWidth;
        double bx2 = bx - perpX * baseHalfWidth;
        double by2 = by - perpY * baseHalfWidth;
        int ipx = (int)Math.round(px);
        int ipy = (int)Math.round(py);
        int ibx1 = (int)Math.round(bx1);
        int iby1 = (int)Math.round(by1);
        int ibx2 = (int)Math.round(bx2);
        int iby2 = (int)Math.round(by2);
        int outline = 0xFFFFFFFF;
        RenderUtils.fillTriangle(g, ipx - 1, ipy,     ibx1 - 1, iby1,     ibx2 - 1, iby2,     outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx + 1, ipy,     ibx1 + 1, iby1,     ibx2 + 1, iby2,     outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy - 1, ibx1,     iby1 - 1, ibx2,     iby2 - 1, outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy + 1, ibx1,     iby1 + 1, ibx2,     iby2 + 1, outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy,     ibx1,     iby1,     ibx2,     iby2,     color,   left, top, right, bottom);
    }
}
