package net.shiroha233.roadweaver.client.map.ui;

/**
 * 简单矩形记录类 - 替代 int[] bounds，提供更清晰的语义
 */
public record Rect(int x, int y, int width, int height) {
    
    public int right() { return x + width; }
    public int bottom() { return y + height; }
    
    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }
    
    /** 调整位置使矩形保持在屏幕内 */
    public Rect clampToScreen(int screenW, int screenH, int margin) {
        int nx = x;
        int ny = y;
        if (nx + width > screenW - margin) nx = screenW - margin - width;
        if (ny + height > screenH - margin) ny = screenH - margin - height;
        if (nx < margin) nx = margin;
        if (ny < margin) ny = margin;
        return new Rect(nx, ny, width, height);
    }
}
