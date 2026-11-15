package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide

/** Minimal host surface for anchored overlay panel geometry. */
interface OverlayPanelLayoutHost {
    fun side(): PanelSide
    fun activeTriggerZoneRect(): RectF
    fun viewWidth(): Int
    fun viewHeight(): Int
    fun dp(value: Float): Float

    /** 面板宽度（dp）：默认沿用旧固定值 226，宿主可覆盖成设置值。 */
    fun panelWidthDp(): Float = 226f
}
