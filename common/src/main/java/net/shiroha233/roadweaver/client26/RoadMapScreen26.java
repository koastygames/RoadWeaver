package net.shiroha233.roadweaver.client26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.network.ClientNetBridge;

import java.util.List;

/** Minecraft 26.2 native RoadWeaver map screen. */
public final class RoadMapScreen26 extends Screen {
    private static final int MAP_BG = 0xE8181B20;
    private static final int MAP_BORDER = 0xFF6F7782;
    private static final int GRID = 0x384A5260;
    private static final int TEXT = 0xFFF1F3F5;
    private static final int MUTED = 0xFFADB5BD;
    private static final int STRUCTURE = 0xFF7FDBFF;
    private static final int PLAYER = 0xFFFFFFFF;
    private static final int PLANNED = 0xFFFFB347;
    private static final int GENERATING = 0xFFFFE066;
    private static final int COMPLETED = 0xFF69DB7C;
    private static final int FAILED = 0xFFFF6B6B;

    private static volatile boolean serverAllowsMap = true;

    private MapSnapshot snapshot = MapSnapshot.empty();
    private Identifier dimensionId;
    private int lastAcceptedRequest = -1;
    private int nextRequest = 1;
    private double centerX;
    private double centerZ;
    private double blocksPerPixel = 4.0D;
    private int mapLeft, mapTop, mapRight, mapBottom;

    public RoadMapScreen26() {
        super(Component.translatable("gui.roadweaver.map.title"));
    }

    public static boolean canOpen() { return serverAllowsMap; }
    public static void resetAccess() { serverAllowsMap = true; }

    public static void applyAccessAllowed(boolean allowed) {
        serverAllowsMap = allowed;
        Minecraft mc = Minecraft.getInstance();
        if (!allowed && mc.gui.screen() instanceof RoadMapScreen26) {
            mc.gui.hud.setOverlayMessage(Component.translatable("gui.roadweaver.map.access_denied"), false);
            mc.gui.setScreen(null);
        }
    }

    @Override
    protected void init() {
        mapLeft = 18;
        mapTop = 38;
        mapRight = Math.max(mapLeft + 120, width - 18);
        mapBottom = Math.max(mapTop + 90, height - 58);
        if (minecraft != null && minecraft.player != null && lastAcceptedRequest < 0) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
        if (minecraft != null && minecraft.level != null) dimensionId = minecraft.level.dimension().identifier();

        int y = Math.max(4, height - 48);
        int x = 18;
        addButton(x, y, 54, "Refresh", b -> requestSnapshot()); x += 58;
        addButton(x, y, 28, "-", b -> zoomOut()); x += 32;
        addButton(x, y, 28, "+", b -> zoomIn()); x += 32;
        addButton(x, y, 34, "←", b -> pan(-1, 0)); x += 38;
        addButton(x, y, 34, "↑", b -> pan(0, -1)); x += 38;
        addButton(x, y, 34, "↓", b -> pan(0, 1)); x += 38;
        addButton(x, y, 34, "→", b -> pan(1, 0)); x += 38;
        addButton(x, y, 54, "Player", b -> recenterPlayer()); x += 58;
        addButton(x, y, 54, "Config", b -> openConfig()); x += 58;
        addButton(x, y, 72, "Teleport", b -> teleportToCenter());
        addButton(Math.max(18, width - 72), 8, 54, "Done", b -> onClose());
        if (!serverAllowsMap) { onClose(); return; }
        requestSnapshot();
    }

    private void addButton(int x, int y, int w, String label, Button.OnPress press) {
        addRenderableWidget(Button.builder(Component.literal(label), press).bounds(x, y, w, 20).build());
    }

    private void zoomIn() { blocksPerPixel = Math.max(0.5D, blocksPerPixel / 1.5D); requestSnapshot(); }
    private void zoomOut() { blocksPerPixel = Math.min(64.0D, blocksPerPixel * 1.5D); requestSnapshot(); }
    private void pan(int dx, int dz) {
        centerX += dx * Math.max(64.0D, (mapRight - mapLeft) * blocksPerPixel * 0.30D);
        centerZ += dz * Math.max(64.0D, (mapBottom - mapTop) * blocksPerPixel * 0.30D);
        requestSnapshot();
    }
    private void recenterPlayer() {
        if (minecraft != null && minecraft.player != null) {
            centerX = minecraft.player.getX(); centerZ = minecraft.player.getZ(); requestSnapshot();
        }
    }
    private void openConfig() { if (minecraft != null) minecraft.gui.setScreen(new RoadWeaverConfigScreen26(this)); }
    private void teleportToCenter() { ClientNetBridge.requestTeleport((int)Math.round(centerX), 0, (int)Math.round(centerZ)); }

    private void requestSnapshot() {
        if (minecraft == null || minecraft.level == null || !serverAllowsMap) return;
        dimensionId = minecraft.level.dimension().identifier();
        int viewW = Math.max(120, mapRight - mapLeft), viewH = Math.max(90, mapBottom - mapTop);
        int radiusX = Math.max(128, Math.min(32768, (int)Math.ceil(viewW * blocksPerPixel * 0.58D)));
        int radiusZ = Math.max(128, Math.min(32768, (int)Math.ceil(viewH * blocksPerPixel * 0.58D)));
        int seq = nextRequest++;
        ClientNetBridge.requestSnapshot(seq, dimensionId,
                (int)Math.floor(centerX) - radiusX, (int)Math.floor(centerZ) - radiusZ,
                (int)Math.ceil(centerX) + radiusX, (int)Math.ceil(centerZ) + radiusZ);
    }

    public void acceptSnapshot(int requestSeq, Identifier dimension, MapSnapshot value) {
        if (requestSeq < lastAcceptedRequest) return;
        if (dimensionId != null && dimension != null && !dimensionId.equals(dimension)) return;
        lastAcceptedRequest = requestSeq;
        dimensionId = dimension;
        snapshot = value != null ? value : MapSnapshot.empty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(mapLeft - 1, mapTop - 1, mapRight + 1, mapBottom + 1, MAP_BORDER);
        graphics.fill(mapLeft, mapTop, mapRight, mapBottom, MAP_BG);
        drawGrid(graphics);
        drawConnections(graphics, snapshot.connections());
        drawRoadPolylines(graphics, snapshot.roadPolylines());
        drawStructures(graphics, snapshot.structures());
        drawPlayer(graphics);
        String titleText = getTitle().getString();
        graphics.text(font, titleText, Math.max(18, (width - font.width(titleText)) / 2), 12, TEXT, true);
        String dim = dimensionId == null ? "unknown" : dimensionId.toString();
        graphics.text(font, "Dimension: " + dim + "   Structures: " + snapshot.structuresCount()
                + "   Roads P/G/C/F: " + snapshot.plannedCount() + "/" + snapshot.generatingCount()
                + "/" + snapshot.completedCount() + "/" + snapshot.failedCount(), mapLeft + 4, mapTop + 4, MUTED, true);
        graphics.text(font, "Center " + (int)Math.round(centerX) + ", " + (int)Math.round(centerZ)
                + "   Scale " + String.format(java.util.Locale.ROOT, "%.2f", blocksPerPixel) + " blocks/px",
                mapLeft + 4, Math.max(mapTop + 15, mapBottom - 13), MUTED, true);
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int widthPx = mapRight - mapLeft, heightPx = mapBottom - mapTop;
        if (widthPx <= 0 || heightPx <= 0) return;
        double spanX = widthPx * blocksPerPixel, spanZ = heightPx * blocksPerPixel;
        double target = Math.max(spanX, spanZ) / 8.0D;
        int step = 16; while (step < target && step < 8192) step *= 2;
        double minX = centerX - spanX / 2.0D, minZ = centerZ - spanZ / 2.0D;
        long startX = (long)Math.floor(minX / step) * step, startZ = (long)Math.floor(minZ / step) * step;
        for (long wx = startX; wx <= minX + spanX; wx += step) { int sx = toScreenX(wx); if (sx >= mapLeft && sx < mapRight) graphics.fill(sx, mapTop, sx + 1, mapBottom, GRID); }
        for (long wz = startZ; wz <= minZ + spanZ; wz += step) { int sy = toScreenY(wz); if (sy >= mapTop && sy < mapBottom) graphics.fill(mapLeft, sy, mapRight, sy + 1, GRID); }
    }

    private void drawConnections(GuiGraphicsExtractor graphics, List<StructureConnection> connections) {
        int count = 0;
        for (StructureConnection c : connections) {
            if (++count > 2500) break;
            int color = switch (c.status()) { case PLANNED -> PLANNED; case GENERATING -> GENERATING; case COMPLETED -> COMPLETED; case FAILED -> FAILED; default -> PLANNED; };
            drawWorldLine(graphics, c.from(), c.to(), color, 1);
        }
    }

    private void drawRoadPolylines(GuiGraphicsExtractor graphics, List<List<BlockPos>> polylines) {
        int segments = 0;
        for (List<BlockPos> line : polylines) for (int i = 1; i < line.size(); i++) { if (++segments > 3500) return; drawWorldLine(graphics, line.get(i - 1), line.get(i), COMPLETED, 1); }
    }

    private void drawStructures(GuiGraphicsExtractor graphics, List<BlockPos> structures) {
        int count = 0;
        for (BlockPos pos : structures) { if (++count > 3000) break; int sx = toScreenX(pos.getX()), sy = toScreenY(pos.getZ()); if (insideMap(sx, sy)) graphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, STRUCTURE); }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        if (minecraft == null || minecraft.player == null) return;
        int sx = toScreenX(minecraft.player.getX()), sy = toScreenY(minecraft.player.getZ());
        if (!insideMap(sx, sy)) return;
        graphics.fill(sx - 3, sy - 3, sx + 4, sy + 4, PLAYER);
        graphics.fill(sx - 1, sy - 5, sx + 2, sy - 2, PLAYER);
    }

    private void drawWorldLine(GuiGraphicsExtractor graphics, BlockPos a, BlockPos b, int color, int thickness) {
        drawClippedLine(graphics, toScreenX(a.getX()), toScreenY(a.getZ()), toScreenX(b.getX()), toScreenY(b.getZ()), color, thickness);
    }
    private void drawClippedLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), steps = Math.max(dx, dy);
        if (steps == 0) { if (insideMap(x0, y0)) graphics.fill(x0, y0, x0 + thickness + 1, y0 + thickness + 1, color); return; }
        int stride = Math.max(1, steps / 512);
        for (int i = 0; i <= steps; i += stride) {
            int x = x0 + (int)Math.round((x1 - x0) * (i / (double)steps));
            int y = y0 + (int)Math.round((y1 - y0) * (i / (double)steps));
            if (insideMap(x, y)) graphics.fill(x, y, x + thickness + 1, y + thickness + 1, color);
        }
    }
    private int toScreenX(double worldX) { return mapLeft + (int)Math.round((worldX - (centerX - (mapRight - mapLeft) * blocksPerPixel / 2.0D)) / blocksPerPixel); }
    private int toScreenY(double worldZ) { return mapTop + (int)Math.round((worldZ - (centerZ - (mapBottom - mapTop) * blocksPerPixel / 2.0D)) / blocksPerPixel); }
    private boolean insideMap(int x, int y) { return x >= mapLeft && x < mapRight && y >= mapTop && y < mapBottom; }
    @Override public boolean isPauseScreen() { return false; }
}
