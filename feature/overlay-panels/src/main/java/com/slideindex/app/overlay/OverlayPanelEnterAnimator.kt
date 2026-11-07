package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

class OverlayPanelEnterAnimator(
    private val side: PanelSide,
    private val dp: (Float) -> Float,
    private val invalidate: () -> Unit
) {
    var progress: Float = 1f
        private set

    private var animator: ValueAnimator? = null

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    fun resetToComplete() {
        cancel()
        progress = 1f
    }

    fun resetToHidden() {
        cancel()
        progress = 0f
    }

    fun enterOffsetX(panel: RectF): Float = offsetX(panel)

    fun drawWithAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit) {
        if (progress >= 1f || contentRect.isEmpty) {
            drawContent()
            return
        }
        val offsetX = offsetX(contentRect)
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        val layer = canvas.saveLayerAlpha(null, alpha)
        canvas.translate(offsetX, 0f)
        drawContent()
        canvas.restoreToCount(layer)
    }

    fun adjustedX(localX: Float, panel: RectF): Float =
        if (progress >= 1f || panel.isEmpty) localX else localX - offsetX(panel)

    fun startEnter(
        panelMode: OverlayPanelMode,
        onShellEnterEnded: () -> Unit,
        onQuickLauncherEnterEnded: () -> Unit
    ) {
        cancel()
        progress = 0f
        val duration = when (panelMode) {
            OverlayPanelMode.SHELL_COMMANDS -> SHELL_PANEL_ENTER_DURATION_MS
            else -> PANEL_ENTER_DURATION_MS
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                progress = valueAnimator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    when (panelMode) {
                        OverlayPanelMode.SHELL_COMMANDS -> onShellEnterEnded()
                        OverlayPanelMode.QUICK_LAUNCHER -> onQuickLauncherEnterEnded()
                        else -> Unit
                    }
                }
            })
            start()
        }
        if (panelMode == OverlayPanelMode.SHELL_COMMANDS) {
            onShellEnterEnded()
        }
        invalidate()
    }

    fun startExit(panelMode: OverlayPanelMode, onEnd: () -> Unit) {
        cancel()
        if (progress <= 0.01f) {
            progress = 0f
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = when (panelMode) {
                OverlayPanelMode.SHELL_COMMANDS -> SHELL_PANEL_ENTER_DURATION_MS
                else -> PANEL_ENTER_DURATION_MS
            }
            interpolator = AccelerateInterpolator()
            addUpdateListener { valueAnimator ->
                progress = valueAnimator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    progress = 0f
                    onEnd()
                }
            })
            start()
        }
        invalidate()
    }

    private fun offsetX(panel: RectF): Float {
        val delta = 1f - progress
        val slide = panel.width() + dp(PANEL_ENTER_OFFSCREEN_MARGIN_DP)
        return when (side) {
            PanelSide.LEFT -> -slide * delta
            PanelSide.RIGHT -> slide * delta
            PanelSide.BOTTOM, PanelSide.TOP -> -slide * delta
        }
    }

    companion object {
        private const val PANEL_ENTER_DURATION_MS = 180L
        private const val SHELL_PANEL_ENTER_DURATION_MS = 260L
        private const val PANEL_ENTER_OFFSCREEN_MARGIN_DP = 16f
    }
}
