/* 文件职责：集中定义道路地图界面的颜色、尺寸与样式常量。 */
package net.shiroha233.roadweaver.client.map;

/**
 * 地图界面主题配置 - 集中管理所有颜色、尺寸、样式常量
 * Xaero 风格深黑主题
 */
public final class MapTheme {
    private MapTheme() {}

    // 纹理配置
    public static final int TEX_WIDTH = 1536;
    public static final int TEX_HEIGHT = 1024;

    // 布局尺寸 - 全屏无边框
    public static final int OUTER_PADDING = 0;
    public static final int INNER_PADDING = 0;
    public static final int GRID_TARGET_PX = 32;

    // 背景
    public static final int COLOR_BACKGROUND = 0xFF0A0A0A;

    // 主色调 - Xaero 风格深黑主题
    public static final int COLOR_TEXT = 0xFFCCCCCC;
    public static final int COLOR_STRUCTURE = 0xFF000000;
    public static final int STRUCTURE_MARKER_SIZE = 8;

    // 连接线颜色
    public static final int COLOR_PLANNED = 0xFF66CC66;
    public static final int COLOR_GENERATING = 0xFFFFCC00;
    public static final int COLOR_COMPLETED = 0xFF44BBFF;
    public static final int COLOR_FAILED = 0xE0FF4444;

    // 网格 - 低对比度暗灰
    public static final int COLOR_GRID = 0x20444444;

    // 右键菜单 - 深色半透明
    public static final int MENU_BG = 0xE0202020;
    public static final int MENU_BORDER = 0x80CCCCCC;
    public static final int MENU_HOVER = 0x40FFFFFF;
    public static final int MENU_TEXT = 0xFFFFFFFF;
    public static final int MENU_ITEM_HEIGHT = 14;
    public static final int MENU_PADDING_X = 6;
    public static final int MENU_PADDING_Y = 4;

    // 交互高亮
    public static final int COLOR_HOVER_HIGHLIGHT = 0x30FFFF00;
    public static final int COLOR_SELECTED = 0xFFFF3B30;
    public static final int COLOR_PREVIEW_LINE = 0xCCFF3B30;

    // 玩家标记 - 使用头像图标
    public static final int PLAYER_AVATAR_SIZE = 12;
    public static final int COLOR_PLAYER_AVATAR_FRAME = 0xB0000000;
    public static final int COLOR_PLAYER_DIRECTION = 0xFFFFFFFF;
    public static final int COLOR_PLAYER_DIRECTION_OUTLINE = 0xFF000000;
    public static final int PLAYER_DIRECTION_TIP_LEN = 15;
    public static final int PLAYER_DIRECTION_BASE_OFFSET = 8;
    public static final int PLAYER_DIRECTION_HALF_WIDTH = 5;

    // 玩家箭头 - 旧样式保留
    public static final int COLOR_PLAYER_ARROW = 0xFFFFFFFF;
    public static final int COLOR_PLAYER_OUTLINE = 0xFF000000;
    public static final int PLAYER_ARROW_TIP_LEN = 10;
    public static final int PLAYER_ARROW_BASE_LEN = 6;
    public static final int PLAYER_ARROW_HALF_WIDTH = 4;

    // 工具栏按钮 - 浮动半透明
    public static final int TOOLBAR_BUTTON_BG = 0x60202020;
    public static final int TOOLBAR_BUTTON_DISABLED_BG = 0x40101010;
    public static final int TOOLBAR_BUTTON_DISABLED_TEXT = 0xFF777777;
    public static final int TOOLBAR_BUTTON_GAP = 4;
    public static final int TOOLBAR_BUTTON_HEIGHT = 16;

    // 主动地图采样叠加层
    public static final int COLOR_MAP_SAMPLING_FRAME = 0xFFFFC857;
    public static final int COLOR_MAP_SAMPLING_TEXT = 0xFFFFFFFF;
    public static final int MAP_SAMPLING_LABEL_BG = 0xD0101010;

    // 图例 - 浮动半透明背景
    public static final int LEGEND_BG = 0x60101010;

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
