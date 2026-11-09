package com.slideindex.app.overlay

import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 悬浮球的销毁路径（从 `FloatBallOverlay` 拆出的窗口生命周期第一半）。
 */
internal object FloatBallDismiss {

    fun dismiss(destroyPickPanelShell: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { dismiss(destroyPickPanelShell) }
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { dismiss(destroyPickPanelShell) }
            return
        }
        FloatBallOverlay.cancelPassiveLineRestore()
        FloatBallOverlay.cancelDeferredGifResume()
        FloatBallOverlay.cancelDeferredDragStart()
        FloatBallOverlay.moveModeActive = false
        FloatBallOverlay.touchHost?.setMoveMode(false)
        FloatBallOverlay.moveModeExitRunnable?.let { FloatBallOverlay.mainHandler.removeCallbacks(it) }
        FloatBallOverlay.moveModeExitRunnable = null
        FloatBallOverlay.hideCursor(restorePassive = false)
        FloatBallOverlay.ballIconView?.release()
        FloatBallOverlay.cancelPauseTimer()
        FloatBallOverlay.cancelPendingChromeRaise()
        val wm = FloatBallOverlay.windowManager
        FloatBallOverlay.stripLineView = null
        FloatBallOverlay.lineWindowLeft?.dispose()
        FloatBallOverlay.lineWindowLeft = null
        FloatBallOverlay.lineWindowRight?.dispose()
        FloatBallOverlay.lineWindowRight = null
        FloatBallOverlay.displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        FloatBallOverlay.touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        FloatBallOverlay.lineTouchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        FloatBallOverlay.destroySplitIdleChrome()
        FloatBallOverlay.cancelBallAlphaAnimation()
        FloatBallOverlay.cancelBallIdleHide()
        FloatBallOverlay.ballAlphaOverrideValue = null
        FloatBallOverlay.ballRevealed = true
        FloatBallOverlay.ballPressed = false
        FloatBallOverlay.setBallForceRevealed(false)
        FloatBallOverlay.gestureHintWindow.detach()
        FloatBallOverlay.displayView?.dispose()
        FloatBallOverlay.displayOwner?.destroy()
        if (FloatBallPickResultPanel.isShowing) {
            Log.i(FloatBallOverlay.TAG, "dismiss: skip pick result panel destroy while panel is showing")
        } else if (destroyPickPanelShell) {
            FloatBallPickResultPanel.destroy()
        }
        FloatBallOverlay.displayOwner = null
        FloatBallOverlay.windowGeneration = -1
        FloatBallOverlay.displayView = null
        FloatBallOverlay.displayLayoutParams = null
        FloatBallOverlay.touchHost = null
        FloatBallOverlay.touchLayoutParams = null
        FloatBallOverlay.lineTouchHost = null
        FloatBallOverlay.lineTouchLayoutParams = null
        FloatBallOverlay.ballIconView = null
        FloatBallOverlay.cursorPreviewView = null
        FloatBallOverlay.windowManager = null
        FloatBallOverlay.sceneState = null
        FloatBallOverlay.dragActiveSideOverrideState = null
        FloatBallOverlay.cancelPendingPositionY()
        FloatBallOverlay.onPositionPersisted = null
        FloatBallOverlay.onActiveSidePersisted = null
        FloatBallOverlay.screenOffDismissReceiver.unregister()
        FloatBallOverlay.appContext = null
        FloatBallOverlay.setDragging(false)
        FloatBallOverlay.dragOriginatedFromLine = false
        FloatBallOverlay.overlayScope.cancel()
        FloatBallOverlay.overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        FloatBallOverlay.lineDragEndedWithGesture = false
        FloatBallOverlay.dragActiveSideOverride = null
        FloatBallOverlay.committedActiveSideUntilPersist = null
        FloatBallOverlay.activeSideAtDragStart = null
        FloatBallOverlay.cancelDragChromeLayoutFrame()
        FloatBallOverlay.cancelCursorCommitFrame()
        FloatBallOverlay.dragSession.reset()
        FloatBallOverlay.currentGestureHintType = null
        FloatBallOverlay.chromeZOrderFront = true
        FloatBallOverlay.cancelPendingChromeRaise()
        FloatBallOverlay.captureSuppressed = false
        FloatBallOverlay.chromeDetachedForCapture = false
    }
}
