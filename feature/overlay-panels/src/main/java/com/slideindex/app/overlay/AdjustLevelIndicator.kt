package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF

/**
 * Floating vertical level pill shown while adjusting volume or brightness.
 */
object AdjustLevelIndicator {
    const val PILL_WIDTH_DP = 52f
    const val PILL_HEIGHT_DP = 196f
    const val EXPANDED_PILL_HEIGHT_DP = 112f
    const val EXPANDED_PILL_GAP_DP = 6f
    const val BOTTOM_PILL_HEIGHT_DP = 76f
    const val BOTTOM_PILL_GAP_DP = 8f
    const val TOP_PILL_HEIGHT_DP = 48f
    const val TOP_PILL_GAP_DP = 8f
    const val PILL_CORNER_DP = 18f
    const val PANEL_TOUCH_SLOP_DP = 32f

    typealias Layout = AdjustLevelIndicatorLayout

    fun panelWindowWidthPx(density: Float): Int =
        ((PILL_WIDTH_DP + PANEL_TOUCH_SLOP_DP * 2f) * density).toInt()
            .coerceAtLeast((16f * density).toInt())

    fun layout(
        viewWidth: Int,
        viewHeight: Int,
        side: PanelSide,
        anchorY: Float,
        density: Float,
        viewScreenX: Int = 0,
        screenWidthPx: Int = viewWidth,
        chrome: AdjustPanelChrome = AdjustPanelChrome.NONE,
        volumeExpanded: Boolean = false
    ): Layout = AdjustLevelIndicatorLayoutEngine.layout(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        side = side,
        anchorY = anchorY,
        density = density,
        viewScreenX = viewScreenX,
        screenWidthPx = screenWidthPx,
        chrome = chrome,
        volumeExpanded = volumeExpanded
    )

    fun hitBounds(layout: Layout, side: PanelSide, density: Float): RectF =
        AdjustLevelIndicatorLayoutEngine.hitBounds(layout, side, density)

    fun containsTouch(
        layout: Layout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): Boolean = AdjustLevelIndicatorLayoutEngine.containsTouch(layout, side, localX, localY, density)

    fun hitVolumeTarget(
        layout: Layout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): VolumeHitTarget = AdjustLevelIndicatorLayoutEngine.hitVolumeTarget(layout, side, localX, localY, density)

    fun hitBrightnessTarget(
        layout: Layout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): BrightnessHitTarget = AdjustLevelIndicatorLayoutEngine.hitBrightnessTarget(layout, side, localX, localY, density)

    fun draw(
        canvas: Canvas,
        layout: Layout,
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        fraction: Float,
        enterProgress: Float,
        density: Float,
        side: PanelSide,
        recede: Boolean = false,
        volumePanel: VolumePanelVisual? = null,
        brightnessPanel: BrightnessPanelVisual? = null,
        context: Context? = null
    ) = AdjustLevelIndicatorRenderer.draw(
        canvas = canvas,
        layout = layout,
        mode = mode,
        fraction = fraction,
        enterProgress = enterProgress,
        density = density,
        side = side,
        recede = recede,
        volumePanel = volumePanel,
        brightnessPanel = brightnessPanel,
        context = context
    )
}
