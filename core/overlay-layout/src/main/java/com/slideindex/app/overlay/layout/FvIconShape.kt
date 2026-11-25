package com.slideindex.app.overlay.layout

/**
 * 全局图标裁切形状规范枚举。
 *
 * 支持 4 种主流形状：
 * - [SQUIRCLE]：方圆 / 超椭圆（推荐默认，圆角 35%）；
 * - [CIRCLE]：正圆（圆角 50%）；
 * - [ROUNDED_RECT]：经典圆角矩形（圆角 22%）；
 * - [ORIGINAL]：原始自适应（系统桌面怎么显示就怎么显示，不进行二次几何遮罩）；
 * - [SQUARE]：微圆角方形（保留兼容旧配置，圆角 8%）。
 */
enum class FvIconShape(val cornerRadiusRatio: Float) {
    /** 方圆 / 超椭圆（推荐默认，圆角 35%）。 */
    SQUIRCLE(0.35f),

    /** 圆形（圆角 50%）。 */
    CIRCLE(0.50f),

    /** 经典圆角矩形（圆角 22%）。 */
    ROUNDED_RECT(0.22f),

    /** 原始自适应（系统原样呈现，不二次裁切）。 */
    ORIGINAL(0f),

    /** 微圆角方形（圆角 8%）。 */
    SQUARE(0.08f);

    val isOriginal: Boolean
        get() = this == ORIGINAL

    companion object {
        val DEFAULT = SQUIRCLE

        fun fromName(name: String?): FvIconShape =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
