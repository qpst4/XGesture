package com.slideindex.app.overlay.quickmenu

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 屏幕取色要用的颜色换算（纯函数，可单测）。
 *
 * 抽出来的理由和 `QuickMenuScanFrame` 一样：这类换算**错了不会崩、只是静默把颜色显示错**，
 * 而"#RRGGBB 差一位"肉眼很难发现。
 */
object QuickMenuColorMath {

    /** `#RRGGBB`（不含 alpha；取色的场景里 alpha 恒为不透明）。 */
    fun toHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /** `#AARRGGBB`，用于需要透明度时。 */
    fun toHexWithAlpha(argb: Int): String = String.format("#%08X", argb)

    /** `rgb(r, g, b)`。 */
    fun toRgb(argb: Int): String =
        "rgb(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF})"

    /**
     * `hsl(h, s%, l%)`：h 取整到度、s/l 取整到百分比。
     *
     * 灰阶（r=g=b）没有色相，按 0 处理（这是通用做法，不显示 NaN 这种没意义的东西）。
     */
    fun toHsl(argb: Int): String {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val delta = maxChannel - minChannel
        val lightness = (maxChannel + minChannel) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        val hue = when {
            delta == 0f -> 0f
            maxChannel == r -> 60f * (((g - b) / delta) % 6f)
            maxChannel == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        return "hsl(${normalizedHue.roundToInt()}, ${(saturation * 100).roundToInt()}%, " +
            "${(lightness * 100).roundToInt()}%)"
    }

    /**
     * 相对亮度（WCAG 口径，0..1）。
     *
     * 用途：取色结果要在色块上叠文字，靠它决定用黑字还是白字（否则浅色块上白字看不见）。
     */
    fun relativeLuminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }

        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** 这个颜色上更适合放深色文字还是浅色文字。 */
    fun prefersDarkText(argb: Int): Boolean = relativeLuminance(argb) > 0.5
}
