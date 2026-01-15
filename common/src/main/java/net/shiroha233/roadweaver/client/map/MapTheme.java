package net.shiroha233.roadweaver.client.map;

/**
 * 地图界面主题配置 - 集中管理所有颜色、尺寸、样式常量
 * 
 * 设计原理：
 * 1. 单一职责：所有视觉相关常量集中在一处，便于统一调整和主题化
 * 2. 可维护性：修改样式时只需改这一个文件
 * 3. 可扩展性：未来可支持多主题切换
 */
public final class MapTheme {
    private MapTheme() {}

    // ========== 纹理配置 ==========
    public static final int TEX_WIDTH = 1536;
    public static final int TEX_HEIGHT = 1024;

    // ========== 布局尺寸 ==========
    /** 地图外边距（像素） */
    public static final int OUTER_PADDING = 36;
    /** 地图内边距（像素） */
    public static final int INNER_PADDING = 25;
    /** 网格目标像素大小 */
    public static final int GRID_TARGET_PX = 32;

    // ========== 主色调 ==========
    /** 文字颜色（深棕色，羊皮纸风格） */
    public static final int COLOR_TEXT = 0xFF5E3D1E;
    /** 结构点颜色 */
    public static final int COLOR_STRUCTURE = 0xFF5E3D1E;

    // ========== 连接线颜色 ==========
    /** 计划中的连接（绿色） */
    public static final int COLOR_PLANNED = 0xFF4CAF50;
    /** 生成中的连接（黑色虚线） */
    public static final int COLOR_GENERATING = 0xFF000000;
    /** 已完成的连接（黑色实线） */
    public static final int COLOR_COMPLETED = 0xFF000000;
    /** 失败的连接（红色） */
    public static final int COLOR_FAILED = 0xE0E05B50;

    // ========== 网格 ==========
    /** 网格线颜色（半透明灰色） */
    public static final int COLOR_GRID = 0x30999999;

    // ========== 右键菜单 ==========
    public static final int MENU_BG = 0xF0101010;
    public static final int MENU_BORDER = 0xFFFFFFFF;
    public static final int MENU_HOVER = 0x40FFFFFF;
    public static final int MENU_TEXT = 0xFFFFFFFF;
    public static final int MENU_ITEM_HEIGHT = 14;
    public static final int MENU_PADDING_X = 6;
    public static final int MENU_PADDING_Y = 4;

    // ========== 交互高亮 ==========
    /** 悬停高亮颜色（淡黄色） */
    public static final int COLOR_HOVER_HIGHLIGHT = 0x40FFFF00;
    /** 选中高亮颜色（红色） */
    public static final int COLOR_SELECTED = 0xFFFF3B30;
    /** 预览线颜色（半透明红色） */
    public static final int COLOR_PREVIEW_LINE = 0xCCFF3B30;

    // ========== 玩家箭头 ==========
    public static final int COLOR_PLAYER_ARROW = 0xFF000000;
    public static final int COLOR_PLAYER_OUTLINE = 0xFFFFFFFF;
    public static final int PLAYER_ARROW_TIP_LEN = 10;
    public static final int PLAYER_ARROW_BASE_LEN = 6;
    public static final int PLAYER_ARROW_HALF_WIDTH = 4;

    // ========== 工具栏按钮 ==========
    public static final int TOOLBAR_BUTTON_GAP = 4;
    public static final int TOOLBAR_BUTTON_HEIGHT = 16;

    // ========== 动画与防抖 ==========
    /** 缩放防抖延迟（毫秒） */
    public static final long ZOOM_DEBOUNCE_MS = 500;
    /** 结构点点击检测半径平方 */
    public static final int STRUCTURE_CLICK_RADIUS_SQ = 64;

    // ========== 虚线参数 ==========
    public static final int DASH_LENGTH = 8;
    public static final int DASH_GAP = 6;

    // ========== 线条粗细计算 ==========
    public static final int MIN_THICKNESS = 1;
    public static final int MAX_THICKNESS = 4;
    public static final int BASE_POINT_SIZE = 2;
}
