package com.slideindex.app.gesture.multitouch

/**
 * 触摸点在**触摸面板自然坐标**里的位置（0..1）。
 *
 * 面板（触摸屏）上报的原始坐标是「面板自身的自然方向」（手机一般是竖屏：u 沿短边、v 沿长边），
 * 与当前屏幕方向无关。这里只做面板内的归一化，真正换算成屏幕坐标由 app 侧完成
 * （只有它知道 `Display` 的旋转），见 [mapTouchAnchorToScreen]。
 *
 * `u` 对应 `rawX / maxX`，`v` 对应 `rawY / maxY`。
 */
data class TouchAnchor(val u: Float, val v: Float) {
    companion object {
        val CENTER = TouchAnchor(0.5f, 0.5f)
    }
}

/**
 * 把识别器给的基准质心换算成面板自然坐标。
 *
 * 识别器为了让横竖方向的阈值一致，把 **x、y 都除以了屏高**（见 [TouchPointer]）：
 * `anchorX = rawX / maxY`。这里恢复成「x 除以面板宽」：
 * `u = anchorX * maxY / maxX`；`v` 本来就是 `rawY / maxY`，直接取。
 *
 * 面板尺寸取 `getevent -p` 报的 ABS 范围（`EvdevTouchObserver` 里的 `AbsRanges`）。
 */
fun touchAnchorOf(
    anchorX: Float,
    anchorY: Float,
    panelWidth: Float,
    panelHeight: Float,
): TouchAnchor {
    if (!anchorX.isFinite() || !anchorY.isFinite()) return TouchAnchor.CENTER
    if (panelWidth <= 0f || panelHeight <= 0f) return TouchAnchor.CENTER
    return TouchAnchor(
        u = (anchorX * panelHeight / panelWidth).coerceIn(0f, 1f),
        v = anchorY.coerceIn(0f, 1f),
    )
}
