package net.shiroha233.roadweaver.client.map

/**
 * 地图界面主题配置 - 集中管理所有颜色、尺寸、样式常量
 *
 * 设计原理：
 * 1. 单一职责：所有视觉相关常量集中在一处，便于统一调整和主题化
 * 2. 可维护性：修改样式时只需改这一个文件
 * 3. 可扩展性：未来可支持多主题切换
 */
object MapTheme {
    // ========== 纹理配置 ==========
    const val TEX_WIDTH: Int = 1536
    const val TEX_HEIGHT: Int = 1024

    // ========== 布局尺寸 ==========
    /** 地图外边距（像素） */
    const val OUTER_PADDING: Int = 36

    /** 地图内边距（像素） */
    const val INNER_PADDING: Int = 25

    /** 网格目标像素大小 */
    const val GRID_TARGET_PX: Int = 32

    // ========== 主色调 ==========
    /** 文字颜色（深棕色，羊皮纸风格） */
    const val COLOR_TEXT: Int = 0xFF5E3D1E.toInt()

    /** 结构点颜色 */
    const val COLOR_STRUCTURE: Int = 0xFF5E3D1E.toInt()

    // ========== 连接线颜色 ==========
    /** 计划中的连接（绿色） */
    const val COLOR_PLANNED: Int = 0xFF4CAF50.toInt()

    /** 生成中的连接（黑色虚线） */
    const val COLOR_GENERATING: Int = 0xFF000000.toInt()

    /** 已完成的连接（黑色实线） */
    const val COLOR_COMPLETED: Int = 0xFF000000.toInt()

    /** 失败的连接（红色） */
    const val COLOR_FAILED: Int = 0xE0E05B50.toInt()

    // ========== 网格 ==========
    /** 网格线颜色（半透明灰色） */
    const val COLOR_GRID: Int = 0x30999999

    // ========== 右键菜单 ==========
    const val MENU_BG: Int = 0xF0101010.toInt()
    const val MENU_BORDER: Int = 0xFFFFFFFF.toInt()
    const val MENU_HOVER: Int = 0x40FFFFFF
    const val MENU_TEXT: Int = 0xFFFFFFFF.toInt()
    const val MENU_ITEM_HEIGHT: Int = 14
    const val MENU_PADDING_X: Int = 6
    const val MENU_PADDING_Y: Int = 4

    // ========== 交互高亮 ==========
    /** 悬停高亮颜色（淡黄色） */
    const val COLOR_HOVER_HIGHLIGHT: Int = 0x40FFFF00

    /** 选中高亮颜色（红色） */
    const val COLOR_SELECTED: Int = 0xFFFF3B30.toInt()

    /** 预览线颜色（半透明红色） */
    const val COLOR_PREVIEW_LINE: Int = 0xCCFF3B30.toInt()

    // ========== 玩家箭头 ==========
    const val COLOR_PLAYER_ARROW: Int = 0xFF000000.toInt()
    const val COLOR_PLAYER_OUTLINE: Int = 0xFFFFFFFF.toInt()
    const val PLAYER_ARROW_TIP_LEN: Int = 10
    const val PLAYER_ARROW_BASE_LEN: Int = 6
    const val PLAYER_ARROW_HALF_WIDTH: Int = 4

    // ========== 工具栏按钮 ==========
    const val TOOLBAR_BUTTON_GAP: Int = 4
    const val TOOLBAR_BUTTON_HEIGHT: Int = 16

    // ========== 动画与防抖 ==========
    /** 缩放防抖延迟（毫秒） */
    const val ZOOM_DEBOUNCE_MS: Long = 500

    /** 结构点点击检测半径平方 */
    const val STRUCTURE_CLICK_RADIUS_SQ: Int = 64

    // ========== 虚线参数 ==========
    const val DASH_LENGTH: Int = 8
    const val DASH_GAP: Int = 6

    // ========== 线条粗细计算 ==========
    const val MIN_THICKNESS: Int = 1
    const val MAX_THICKNESS: Int = 4
    const val BASE_POINT_SIZE: Int = 2
}
