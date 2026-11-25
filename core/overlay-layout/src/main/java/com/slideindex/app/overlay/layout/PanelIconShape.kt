package com.slideindex.app.overlay.layout

/**
 * 各独立面板针对图标形状的设置档位。
 *
 * 默认均为 [FOLLOW_GLOBAL]（跟随主页外观中的全局图标形状），
 * 也支持独立覆盖为特定形状。
 */
enum class PanelIconShape(val id: Int) {
    /** 跟随全局外观设置（默认）。 */
    FOLLOW_GLOBAL(0),

    /** 强制方圆 / 超椭圆。 */
    SQUIRCLE(1),

    /** 强制正圆。 */
    CIRCLE(2),

    /** 强制圆角矩形。 */
    ROUNDED_RECT(3),

    /** 强制原始自适应。 */
    ORIGINAL(4);

    fun resolve(globalShape: FvIconShape): FvIconShape = when (this) {
        FOLLOW_GLOBAL -> globalShape
        SQUIRCLE -> FvIconShape.SQUIRCLE
        CIRCLE -> FvIconShape.CIRCLE
        ROUNDED_RECT -> FvIconShape.ROUNDED_RECT
        ORIGINAL -> FvIconShape.ORIGINAL
    }

    companion object {
        val DEFAULT = FOLLOW_GLOBAL

        fun fromId(id: Int?): PanelIconShape =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        fun fromName(name: String?): PanelIconShape =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
