package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import com.slideindex.app.settings.AppSettings

/**
 * 拖拽期间 chrome（球 + 取词光标）的单帧布局调度
 * —— 从 `FloatBallOverlay` 拆出的第 9 类。
 */
internal object FloatBallDragChromeLayout {

    fun cancelDragChromeLayoutFrame() {
        FloatBallOverlay.dragChromeLayoutFrameScheduled = false
        FloatBallOverlay.pendingCursorFrameAnchor = null
    }

    fun scheduleDragChromeLayoutOnNextFrame() {
        val view = FloatBallOverlay.displayView ?: FloatBallOverlay.cursorPreviewView ?: return
        if (FloatBallOverlay.dragChromeLayoutFrameScheduled) return
        FloatBallOverlay.dragChromeLayoutFrameScheduled = true
        view.postOnAnimation {
            FloatBallOverlay.dragChromeLayoutFrameScheduled = false
            if (!FloatBallOverlay.isDraggingNow()) return@postOnAnimation
            commitDragChromeLayoutFrame()
        }
    }

    fun flushDragChromeLayout(syncAnchorState: Boolean = false) {
        cancelDragChromeLayoutFrame()
        if (!FloatBallOverlay.isDraggingNow() && !syncAnchorState) return
        commitDragChromeLayoutFrame(forceAnchorState = syncAnchorState)
    }

    /** Ball + cursor chrome in one animation tick (FV-style single-frame move). */
    fun commitDragChromeLayoutFrame(forceAnchorState: Boolean = false) {
        val pick = FloatBallOverlay.pendingCursorFrameAnchor ?: FloatBallOverlay.currentDragPickAnchor
        FloatBallOverlay.pendingCursorFrameAnchor = null
        if (!FloatBallOverlay.moveModeActive) {
            FloatBallOverlay.syncCursorPreviewAppearance()
        }
        FloatBallOverlay.settingsState?.value?.let { applyDragBallLayout(it) }
        // 移动模式下不维护取词光标锚点/预览边界：这一趟拖动只是为了挪球。
        if (FloatBallOverlay.moveModeActive) return
        val needsPreviewAnchor = forceAnchorState ||
            FloatBallOverlay.cursorPausedState?.value == true ||
            FloatBallOverlay.selectionStartState?.value != null
        if (needsPreviewAnchor) {
            FloatBallOverlay.cursorAnchorState?.value = pick
        }
        if (FloatBallOverlay.isDraggingNow() && FloatBallOverlay.cursorVisibleState?.value == true) {
            FloatBallOverlay.applyPreviewBoundsFromCache()
        }
    }

    fun cancelCursorCommitFrame() {
        FloatBallOverlay.cursorCommitFrameScheduled = false
        FloatBallOverlay.pendingPickAnchor = null
    }

    fun scheduleCursorCommitOnNextFrame() {
        val view = FloatBallOverlay.displayView ?: return
        if (FloatBallOverlay.cursorCommitFrameScheduled) return
        FloatBallOverlay.cursorCommitFrameScheduled = true
        view.postOnAnimation {
            FloatBallOverlay.cursorCommitFrameScheduled = false
            if (!FloatBallOverlay.isDraggingNow()) return@postOnAnimation
            commitPickAnchor()
        }
    }

    fun commitPickAnchor() {
        val pick = FloatBallOverlay.pendingPickAnchor ?: return
        FloatBallOverlay.pendingPickAnchor = null
        FloatBallOverlay.cursorAnchorState?.value = pick
    }

    fun applyPickAnchor(pick: Offset) {
        if (FloatBallOverlay.isDraggingNow()) {
            FloatBallOverlay.currentDragPickAnchor = pick
            FloatBallOverlay.pendingCursorFrameAnchor = pick
            return
        }
        FloatBallOverlay.pendingPickAnchor = pick
        scheduleCursorCommitOnNextFrame()
    }

    fun applyDragBallLayout(settings: AppSettings) {
        val view = FloatBallOverlay.displayView ?: return
        val metrics = view.resources.displayMetrics
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        val marginPx = FloatBallLayout.marginPx(metrics.density)
        val screenBounds = FloatBallOverlay.dragScreenBounds ?: FloatBallScreenMetrics.bounds(view.context, FloatBallOverlay.windowManager)
            .also { FloatBallOverlay.dragScreenBounds = it }
        val center = FloatBallOverlay.dragSession.clampedBallCenter(
            ballSize = ballSize,
            marginPx = marginPx,
            screenWidth = screenBounds.width.roundToInt(),
            screenHeight = screenBounds.height.roundToInt()
        )
        FloatBallOverlay.syncDisplayBallLayout(settings, layoutOnly = true, overrideCenter = center)
    }
}
