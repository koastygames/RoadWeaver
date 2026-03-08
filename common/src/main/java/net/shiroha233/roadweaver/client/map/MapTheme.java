package net.shiroha233.roadweaver.client.map;

/**
 * 地图界面主题配置 - 集中管理所有颜色、尺寸、样式常量
 */
public final class MapTheme {
    private MapTheme() {}

    // 纹理配置
    public static final int TEX_WIDTH = 1536;
    public static final int TEX_HEIGHT = 1024;
    // 布局尺寸
    public static final int OUTER_PADDING = 36;
    public static final int INNER_PADDING = 25;
    public static final int GRID_TARGET_PX = 32;

    // 主色调
    public static final int COLOR_TEXT = 0xFF5E3D1E;
    public static final int COLOR_STRUCTURE = 0xFF5E3D1E;

    // 连接线颜色
    public static final int COLOR_PLANNED = 0xFF4CAF50;
    public static final int COLOR_GENERATING = 0xFF000000;
    public static final int COLOR_COMPLETED = 0xFF000000;
    public static final int COLOR_FAILED = 0xE0E05B50;

    // 网格
    public static final int COLOR_GRID = 0x30999999;

    // 右键菜单
    public static final int MENU_BG = 0xF0101010;
    public static final int MENU_BORDER = 0xFFFFFFFF;
    public static final int MENU_HOVER = 0x40FFFFFF;
    public static final int MENU_TEXT = 0xFFFFFFFF;
    public static final int MENU_ITEM_HEIGHT = 14;
    public static final int MENU_PADDING_X = 6;
    public static final int MENU_PADDING_Y = 4;

    // 交互高亮
    public static final int COLOR_HOVER_HIGHLIGHT = 0x40FFFF00;
    public static final int COLOR_SELECTED = 0xFFFF3B30;
    public static final int COLOR_PREVIEW_LINE = 0xCCFF3B30;

    // 玩家箭头
    public static final int COLOR_PLAYER_ARROW = 0xFF000000;
    public static final int COLOR_PLAYER_OUTLINE = 0xFFFFFFFF;
    public static final int PLAYER_ARROW_TIP_LEN = 10;
    public static final int PLAYER_ARROW_BASE_LEN = 6;
    public static final int PLAYER_ARROW_HALF_WIDTH = 4;

    // 工具栏按钮
    public static final int TOOLBAR_BUTTON_GAP = 4;
    public static final int TOOLBAR_BUTTON_HEIGHT = 16;

    // 动画与防抖
    public static final long ZOOM_DEBOUNCE_MS = 500;
    public static final int STRUCTURE_CLICK_RADIUS_SQ = 64;

    // 虚线参数
    public static final int DASH_LENGTH = 8;
    public static final int DASH_GAP = 6;

    // 线条粗细计算
    public static final int MIN_THICKNESS = 1;
    public static final int MAX_THICKNESS = 4;
    public static final int BASE_POINT_SIZE = 2;
}
