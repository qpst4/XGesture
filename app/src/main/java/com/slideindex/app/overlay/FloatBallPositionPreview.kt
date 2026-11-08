package com.slideindex.app.overlay

import android.os.Looper

/**
 * 悬浮球"位置预览"（设置页拖动预览）行为（从 `FloatBallOverlay` 拆出的第 2 类）。
 *
 * 状态仍由 [FloatBallOverlay] 持有（`internal` 可见）。
 */
internal object FloatBallPositionPreview {

    fun setStripZonePreviewActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { setStripZonePreviewActive(active) }
            return
        }
        FloatBallOverlay.stripZonePreviewState?.value = active
    }

    fun previewPositionYFraction(fraction: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { previewPositionYFraction(fraction) }
            return
        }
        val state = FloatBallOverlay.settingsState ?: return
        FloatBallOverlay.setBallForceRevealed(true)
        // 新的拖动接管，之前那次"待确认"的值作废。
        cancelPendingPositionY()
        if (FloatBallOverlay.positionYPreviewRestore == null) {
            FloatBallOverlay.positionYPreviewRestore = state.value.floatBallPositionYFraction
        }
        val coerced = FloatBallLayout.coercePositionYFraction(fraction)
        val updated = state.value.let {
            it.copy(floatBall = it.floatBall.copy(floatBallPositionYFraction = coerced))
        }
        state.value = updated
        FloatBallOverlay.applyAllLayouts(updated)
    }

    fun endPositionYPreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { endPositionYPreview(restoreIfNeeded) }
            return
        }
        val baseline = FloatBallOverlay.positionYPreviewRestore
        FloatBallOverlay.positionYPreviewRestore = null
        FloatBallOverlay.setBallForceRevealed(false)
        val state = FloatBallOverlay.settingsState ?: return
        if (!restoreIfNeeded || baseline == null) {
            // 提交：保留当前预览值，等设置回流到同一个值再放手。
            FloatBallOverlay.pendingPositionYFraction = state.value.floatBallPositionYFraction
            FloatBallOverlay.mainHandler.removeCallbacks(FloatBallOverlay.clearPendingPositionYRunnable)
            FloatBallOverlay.mainHandler.postDelayed(
                FloatBallOverlay.clearPendingPositionYRunnable,
                PENDING_POSITION_Y_TIMEOUT_MS,
            )
            return
        }
        cancelPendingPositionY()
        val restored = state.value.let {
            it.copy(floatBall = it.floatBall.copy(floatBallPositionYFraction = baseline))
        }
        state.value = restored
        FloatBallOverlay.applyAllLayouts(restored)
    }

    fun clearPositionYPreviewRestore() {
        FloatBallOverlay.positionYPreviewRestore = null
        cancelPendingPositionY()
        FloatBallOverlay.setBallForceRevealed(false)
    }

    fun cancelPendingPositionY() {
        FloatBallOverlay.pendingPositionYFraction = null
        FloatBallOverlay.mainHandler.removeCallbacks(FloatBallOverlay.clearPendingPositionYRunnable)
    }

    private const val PENDING_POSITION_Y_TIMEOUT_MS = 2_000L
}
