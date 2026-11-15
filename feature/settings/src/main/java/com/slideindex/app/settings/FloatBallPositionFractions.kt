package com.slideindex.app.settings

object FloatBallPositionFractions {
    const val MIN_VISIBLE = 0.5f
    /** 键盘「收窄」时允许露出比例低于 [MIN_VISIBLE]（与变窄比例滑条一致）。 */
    const val MIN_KEYBOARD_NARROW_VISIBLE = 0.001f
    const val MAX_VISIBLE = 1f
    const val MIN_Y = 0f
    const val MAX_Y = 1f
    /** Legacy CUSTOM-mode ball center X as fraction of screen width. */
    const val MIN_CUSTOM_CENTER_X = 0f
    const val MAX_CUSTOM_CENTER_X = 1f

    /**
     * 悬浮球"距边缘距离"（dp）：负值往屏幕外挪（半隐藏），正值往屏幕内挪（离边）。
     *
     * 存储只做宽范围兜底；真正的上下限按屏幕宽度和球直径算：
     * [maxInsetDp]（最多挪到屏幕中央）、[minInsetDp]（最多露出半个球）。
     */
    const val MIN_EDGE_INSET_DP = -400
    const val MAX_EDGE_INSET_DP = 400

    /** 往屏内最多能挪多少：球心到达屏幕正中。 */
    fun maxInsetDp(screenWidthDp: Float, ballSizeDp: Float): Int =
        (((screenWidthDp - ballSizeDp) / 2f).toInt()).coerceIn(0, MAX_EDGE_INSET_DP)

    /** 往屏外最多能挪多少：只露出半个球（旧版 50%）。 */
    fun minInsetDp(ballSizeDp: Float): Int =
        (-(ballSizeDp / 2f).toInt()).coerceIn(MIN_EDGE_INSET_DP, 0)

    fun coerceVisible(fraction: Float): Float = fraction.coerceIn(MIN_VISIBLE, MAX_VISIBLE)

    fun coerceEdgeInsetDp(value: Int): Int =
        value.coerceIn(MIN_EDGE_INSET_DP, MAX_EDGE_INSET_DP)

    fun coerceKeyboardNarrowVisible(fraction: Float): Float =
        fraction.coerceIn(MIN_KEYBOARD_NARROW_VISIBLE, MAX_VISIBLE)

    fun coerceCustomCenterX(fraction: Float): Float =
        fraction.coerceIn(MIN_CUSTOM_CENTER_X, MAX_CUSTOM_CENTER_X)

    fun coerceY(fraction: Float): Float = fraction.coerceIn(MIN_Y, MAX_Y)
}
