package com.slideindex.app.settings

/**
 * 引擎区（搜索面板 dock / 取词面板引擎网格）里引擎图标的裁切形状。
 *
 * [cornerFraction] 是圆角相对图标边长的比例；[DEFAULT] 保留历史观感（24% 圆角矩形），
 * 老用户升级后不会看到变化。
 */
enum class SearchEngineIconShape(val cornerFraction: Float) {
    /** 现状：24% 圆角矩形。 */
    DEFAULT(0.24f),

    /** 圆形。 */
    CIRCLE(0.50f),

    /** 平滑方圆（与圆环启动器同口径）。 */
    SQUIRCLE(0.35f),

    /** 微圆角方形。 */
    SQUARE(0.08f),
    ;

    companion object {
        fun fromId(id: String?): SearchEngineIconShape =
            entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
