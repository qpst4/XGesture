package com.slideindex.app.overlay

import android.os.Looper
import android.view.View

/**
 * 悬浮球 chrome 的截图/启动器抑制与恢复（从 4.3k 行的 `FloatBallOverlay` 拆出的第 1 类行为）。
 *
 * 本次只拆行为，不搬状态：状态仍由 [FloatBallOverlay] 持有（`internal` 可见），
 * 后续阶段再把状态搬进 `FloatBallState`，避免一次性大爆炸。
 */
internal object FloatBallCaptureChrome {

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (FloatBallOverlay.captureSuppressed) return
        FloatBallOverlay.captureSuppressed = true
        FloatBallOverlay.sceneState?.chromeVisible?.value = false
        FloatBallOverlay.sceneState?.ballVisible?.value = false
        FloatBallOverlay.sceneState?.lineVisible?.value = false
        FloatBallOverlay.clearSplitIdleChrome()
        FloatBallOverlay.hideGestureHintWindow()
        FloatBallOverlay.hideCursor()
        FloatBallOverlay.detachChromeWindowsForCapture()
    }

    fun hideChromeForEdgeRegionalPick() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { hideChromeForEdgeRegionalPick() }
            return
        }
        if (FloatBallOverlay.sceneState == null) return
        FloatBallOverlay.captureSuppressed = true
        FloatBallOverlay.sceneState?.chromeVisible?.value = false
        FloatBallOverlay.sceneState?.ballVisible?.value = false
        FloatBallOverlay.sceneState?.lineVisible?.value = false
        FloatBallOverlay.dragActiveSideOverride = null
        FloatBallOverlay.clearSplitIdleChrome()
        FloatBallOverlay.hideGestureHintWindow()
        FloatBallOverlay.hideCursor()
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!FloatBallOverlay.captureSuppressed) return
        FloatBallOverlay.captureSuppressed = false
        FloatBallOverlay.reattachChromeWindowsAfterCapture()
        val settings = FloatBallOverlay.settingsState?.value
        if (settings != null) {
            FloatBallOverlay.updateChromeVisibility(settings)
            FloatBallOverlay.syncSplitIdleChrome(settings)
        } else {
            FloatBallOverlay.sceneState?.chromeVisible?.value = true
        }
    }

    fun hideChromeForRingLauncher() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { hideChromeForRingLauncher() }
            return
        }
        if (FloatBallOverlay.sceneState == null) return
        FloatBallOverlay.captureSuppressed = true
        FloatBallOverlay.sceneState?.chromeVisible?.value = false
        FloatBallOverlay.sceneState?.ballVisible?.value = false
        FloatBallOverlay.sceneState?.lineVisible?.value = false
        FloatBallOverlay.dragActiveSideOverride = null
        FloatBallOverlay.clearSplitIdleChrome()
        FloatBallOverlay.hideGestureHintWindow()
        FloatBallOverlay.cancelCursorPickPreview()
        FloatBallOverlay.displayView?.visibility = View.GONE
        if (!FloatBallOverlay.isLauncherCaptureActive()) {
            FloatBallOverlay.suppressTouchHostsForLauncherOverlay()
        }
    }

    fun restoreChromeAfterRingLauncher() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { restoreChromeAfterRingLauncher() }
            return
        }
        FloatBallOverlay.displayView?.visibility = View.VISIBLE
        FloatBallOverlay.touchHost?.visibility = View.VISIBLE
        FloatBallOverlay.lineTouchHost?.visibility = View.VISIBLE
        restoreAfterScreenshotCapture()
        FloatBallOverlay.settingsState?.value?.let {
            FloatBallOverlay.recoverIdleTouchCaptureLayouts(it)
        }
    }

    fun suppressTouchHostsForActiveLauncherOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { suppressTouchHostsForActiveLauncherOverlay() }
            return
        }
        FloatBallOverlay.suppressTouchHostsForLauncherOverlay()
    }
}
