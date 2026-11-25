package com.slideindex.app.settings

import com.slideindex.app.overlay.layout.FvIconShape

/**
 * 引擎区（搜索面板 dock / 取词面板引擎网格）里引擎图标的裁切形状。
 *
 * 默认 [FOLLOW_GLOBAL] 跟随主页外观中的全局图标形状，老配置支持显式指定特定形状。
 */
enum class SearchEngineIconShape(val cornerFraction: Float) {
    /** 跟随全局图标形状设置（默认推荐）。 */
    FOLLOW_GLOBAL(0.35f),

    /** 平滑方圆 / 超椭圆。 */
    SQUIRCLE(0.35f),

    /** 圆形。 */
    CIRCLE(0.50f),

    /** 经典圆角矩形。 */
    ROUNDED_RECT(0.22f),

    /** 原始自适应（无二次几何裁切）。 */
    ORIGINAL(0f),

    /** @deprecated 保持历史反序列化兼容；建议切至 [FOLLOW_GLOBAL] 或 [ROUNDED_RECT]。 */
    @Deprecated("Use FOLLOW_GLOBAL or ROUNDED_RECT instead")
    DEFAULT(0.24f),

    /** @deprecated 保持历史反序列化兼容。 */
    @Deprecated("Legacy square shape")
    SQUARE(0.08f);

    fun resolve(globalShape: FvIconShape): FvIconShape = when (this) {
        FOLLOW_GLOBAL -> globalShape
        SQUIRCLE -> FvIconShape.SQUIRCLE
        CIRCLE -> FvIconShape.CIRCLE
        ROUNDED_RECT -> FvIconShape.ROUNDED_RECT
        ORIGINAL -> FvIconShape.ORIGINAL
        DEFAULT -> FvIconShape.ROUNDED_RECT
        SQUARE -> FvIconShape.SQUARE
    }

    companion object {
        fun fromId(id: String?): SearchEngineIconShape =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: FOLLOW_GLOBAL
    }
}
