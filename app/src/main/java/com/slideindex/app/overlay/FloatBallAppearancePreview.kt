package com.slideindex.app.overlay

import android.os.Looper
import com.slideindex.app.settings.FloatBallSettings

/**
 * 悬浮球"外观预览"（设置页调大小/透明度/线条）行为（从 `FloatBallOverlay` 拆出的第 3 类）。
 *
 * 状态仍由 [FloatBallOverlay] 持有（`internal` 可见）。
 */
internal object FloatBallAppearancePreview {

    data class Snapshot(
        val sizeDp: Float,
        val opacity: Float,
        val visibleFraction: Float,
        val lineHeightFraction: Float,
        val lineWidthDp: Float,
        val lineOpacity: Float,
    )

    fun previewAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthDp: Float? = null,
        lineOpacity: Float? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post {
                previewAppearance(
                    sizeDp,
                    opacity,
                    visibleFraction,
                    lineHeightFraction,
                    lineWidthDp,
                    lineOpacity,
                )
            }
            return
        }
        val state = FloatBallOverlay.settingsState ?: return
        if (FloatBallOverlay.appearancePreviewRestore == null) {
            val current = state.value
            FloatBallOverlay.appearancePreviewRestore = Snapshot(
                sizeDp = current.floatBallSizeDp,
                opacity = current.floatBallOpacity,
                visibleFraction = current.floatBallVisibleFraction,
                lineHeightFraction = current.floatBallLineHeightFraction,
                lineWidthDp = current.floatBallLineWidthDp,
                lineOpacity = current.floatBallLineOpacity,
            )
        }
        val current = state.value
        val updated = current.copy(
            floatBall = current.floatBall.copy(
                floatBallSizeDp = sizeDp?.coerceIn(36f, 96f) ?: current.floatBallSizeDp,
                floatBallOpacity = opacity?.coerceIn(0f, 1f) ?: current.floatBallOpacity,
                floatBallVisibleFraction = visibleFraction?.let(FloatBallLayout::coerceVisibleFraction)
                    ?: current.floatBallVisibleFraction,
                floatBallLineHeightFraction = lineHeightFraction?.coerceIn(0.04f, 0.4f)
                    ?: current.floatBallLineHeightFraction,
                floatBallLineWidthDp = lineWidthDp?.coerceIn(
                    FloatBallSettings.MIN_LINE_WIDTH_DP,
                    FloatBallSettings.MAX_LINE_WIDTH_DP,
                ) ?: current.floatBallLineWidthDp,
                floatBallLineOpacity = lineOpacity?.coerceIn(0f, 1f) ?: current.floatBallLineOpacity,
            ),
        )
        state.value = updated
        // 调外观时必须看得见球，否则改了大小/位置/不透明度也不知道结果。
        FloatBallOverlay.setBallForceRevealed(true)
        FloatBallOverlay.invalidateChrome()
        val layoutChanged = sizeDp != null || visibleFraction != null ||
            lineHeightFraction != null || lineWidthDp != null
        if (layoutChanged) {
            FloatBallOverlay.applyAllLayouts(updated)
            FloatBallOverlay.syncTouchWindowLayout(updated)
        }
    }

    fun endAppearancePreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { endAppearancePreview(restoreIfNeeded) }
            return
        }
        val baseline = FloatBallOverlay.appearancePreviewRestore
        FloatBallOverlay.appearancePreviewRestore = null
        FloatBallOverlay.setBallForceRevealed(false)
        if (!restoreIfNeeded || baseline == null) return
        val state = FloatBallOverlay.settingsState ?: return
        val restored = state.value.let { current ->
            current.copy(
                floatBall = current.floatBall.copy(
                    floatBallSizeDp = baseline.sizeDp,
                    floatBallOpacity = baseline.opacity,
                    floatBallVisibleFraction = baseline.visibleFraction,
                    floatBallLineHeightFraction = baseline.lineHeightFraction,
                    floatBallLineWidthDp = baseline.lineWidthDp,
                    floatBallLineOpacity = baseline.lineOpacity,
                ),
            )
        }
        state.value = restored
        FloatBallOverlay.invalidateChrome()
        FloatBallOverlay.bumpScreenLayoutGeneration()
        FloatBallOverlay.applyAllLayouts(restored)
        FloatBallOverlay.syncTouchWindowLayout(restored)
    }

    fun clearAppearancePreviewRestore() {
        FloatBallOverlay.appearancePreviewRestore = null
        FloatBallOverlay.setBallForceRevealed(false)
    }
}
