package com.slideindex.app.overlay

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared enter/exit motion used by edge overlay panels and [OhoQuickToolsPanel]. */
object OverlayPanelEnterAnimation {
    const val DURATION_MS = 180
    val OFFSCREEN_MARGIN: Dp = 16.dp

    val enterSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val exitSpec = tween<Float>(
        durationMillis = DURATION_MS,
        easing = FastOutLinearInEasing
    )

    /** @param progress 0 = hidden/off-screen, 1 = fully shown */
    fun slideOffsetPx(
        progress: Float,
        panelWidthPx: Float,
        marginPx: Float,
        side: PanelSide?
    ): Float {
        if (side == null || panelWidthPx <= 0f) return 0f
        val delta = 1f - progress.coerceIn(0f, 1f)
        val slide = panelWidthPx + marginPx
        return when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> -slide * delta
            PanelSide.RIGHT -> slide * delta
        }
    }

    fun alpha(progress: Float): Float = progress.coerceIn(0f, 1f)
}
