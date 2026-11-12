package com.slideindex.app.settings

/**
 * 「显示贴边把手」的外观参数：粗细、长度、不透明度。
 *
 * 三个值都要**同时**参与几何计算（触摸区、流光窗、peek 定位），
 * 只改绘制不改几何会重演历史上的「设置调了没用」。
 */
object HistoryFloatHandleAppearance {
    /** 条粗细（视觉宽度），单位 dp。 */
    const val MIN_WIDTH_DP = 5f
    const val MAX_WIDTH_DP = 14f
    const val DEFAULT_WIDTH_DP = 9f

    /** 条长度（视觉高度），单位 dp。 */
    const val MIN_HEIGHT_DP = 16f
    const val MAX_HEIGHT_DP = 100f
    const val DEFAULT_HEIGHT_DP = 28f

    /** 静止态不透明度（按下仍会提到接近 1.0，作为按下反馈）。 */
    const val MIN_ALPHA = 0.3f
    const val MAX_ALPHA = 1f
    const val DEFAULT_ALPHA = 0.92f

    fun coerceWidthDp(value: Float): Float = value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun coerceHeightDp(value: Float): Float = value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun coerceAlpha(value: Float): Float = value.coerceIn(MIN_ALPHA, MAX_ALPHA)
}
