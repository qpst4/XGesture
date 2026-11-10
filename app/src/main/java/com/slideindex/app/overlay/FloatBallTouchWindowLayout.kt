package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide

/**
 * 悬浮球触摸窗口的扩窗/收缩/穿透（从 `FloatBallOverlay` 拆出的第 9 类）。
 */
internal object FloatBallTouchWindowLayout {

    fun setLineTouchHostEnabled(enabled: Boolean) {
        val view = FloatBallOverlay.lineTouchHost ?: return
        val wm = FloatBallOverlay.windowManager ?: return
        val params = FloatBallOverlay.lineTouchLayoutParams ?: return
        val state = FloatBallOverlay.sceneState
        val settings = FloatBallOverlay.settingsState?.value
        if (state != null && settings != null) {
            val metrics = view.resources.displayMetrics
            val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
            val inactiveSide = FloatBallSide.opposite(FloatBallOverlay.effectiveActiveSide(settings))
            val isLandscape = FloatBallOverlay.isLandscapeLayout()
            val bounds = state.lineHitRect(
                settings = settings,
                metrics = metrics,
                inactiveSide = inactiveSide,
                screenWidthPx = screenW,
                screenHeightPx = screenH,
                isLandscape = isLandscape,
            )
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width().coerceAtLeast(1)
            params.height = bounds.height().coerceAtLeast(1)
        } else {
            params.width = 1
            params.height = 1
        }
        view.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (view.isAttachedToWindow) {
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /** 球体拖出：全屏捕获手势，隐藏线条触摸窗。 */
    fun expandBallTouchCapture() {
        setLineTouchHostEnabled(false)
        expandTouchHostToFullscreen(FloatBallOverlay.touchHost, FloatBallOverlay.touchLayoutParams)
    }

    /**
     * 线条拖出：全屏扩展必须在 [FloatBallOverlay.lineTouchHost] 上（手势在此窗发起），
     * 不可改由球体窗接管，否则 MOVE/UP 丢失。
     */
    fun expandLineTouchCapture() {
        setBallTouchHostPassthrough(true)
        expandTouchHostToFullscreen(FloatBallOverlay.lineTouchHost, FloatBallOverlay.lineTouchLayoutParams)
    }

    fun expandTouchHostToFullscreen(
        view: View?,
        params: WindowManager.LayoutParams?
    ) {
        val wm = FloatBallOverlay.windowManager ?: return
        if (view == null || params == null) return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        FloatBallOverlay.fullscreenTouchBackup[params] = intArrayOf(
            params.width,
            params.height,
            params.x,
            params.y,
            params.flags,
        )
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(FloatBallOverlay.TAG, "expandTouchHostToFullscreen failed", it) }
    }

    fun collapseLineTouchHostFromFullscreen() {
        val view = FloatBallOverlay.lineTouchHost ?: return
        val wm = FloatBallOverlay.windowManager ?: return
        val params = FloatBallOverlay.lineTouchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        FloatBallOverlay.settingsState?.value?.let { FloatBallOverlay.syncLineTouchWindowLayout(it) }
        if (isTouchHostFullscreen(params)) {
            restoreTouchHostFromFullscreen(view, params, wm)
        } else {
            FloatBallOverlay.fullscreenTouchBackup.remove(params)
        }
    }

    fun collapseBallTouchHostFromFullscreen() {
        val view = FloatBallOverlay.touchHost ?: return
        val wm = FloatBallOverlay.windowManager ?: return
        val params = FloatBallOverlay.touchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        FloatBallOverlay.settingsState?.value?.let { FloatBallOverlay.syncBallTouchWindowLayout(it) }
        if (isTouchHostFullscreen(params)) {
            restoreTouchHostFromFullscreen(view, params, wm)
        } else {
            FloatBallOverlay.fullscreenTouchBackup.remove(params)
        }
    }

    fun isTouchHostFullscreen(params: WindowManager.LayoutParams): Boolean =
        params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT

    /**
     * 全屏捕获收尾的兜底还原。
     *
     * `sync*WindowLayout` 在 FloatBallOverlay.sceneState 为空或被拖动态挡住时会直接 return，
     * 光靠它收不回来；这里按撑大前的快照还原尺寸与 flags。没有快照（旧会话遗留的
     * 全屏窗）就缩到 1×1 并置为不可触摸，避免整屏窗常驻、每帧整屏重绘。
     */
    fun restoreTouchHostFromFullscreen(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        val backup = FloatBallOverlay.fullscreenTouchBackup.remove(params)
        if (backup != null) {
            params.width = backup[0]
            params.height = backup[1]
            params.x = backup[2]
            params.y = backup[3]
            params.flags = backup[4]
        } else {
            params.width = 1
            params.height = 1
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(FloatBallOverlay.TAG, "restoreTouchHostFromFullscreen failed", it) }
    }

    fun setBallTouchHostPassthrough(passthrough: Boolean) {
        val view = FloatBallOverlay.touchHost ?: return
        val wm = FloatBallOverlay.windowManager ?: return
        val params = FloatBallOverlay.touchLayoutParams ?: return
        // 音量条显示期间球必须让出触摸：这个条件要盖过 [restoreAfterDragEnd] 等处的 passthrough=false
        // （那些调用发生在条已经显示之后，见 FloatBallOverlay.isAdjustBarCoveringBall）。
        val shouldPassthrough = passthrough || FloatBallOverlay.isAdjustBarCoveringBall()
        if (shouldPassthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!FloatBallOverlay.captureSuppressed && !FloatBallOverlay.passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(FloatBallOverlay.TAG, "setBallTouchHostPassthrough failed", it) }
    }

    fun setLineTouchHostPassthrough(passthrough: Boolean) {
        val view = FloatBallOverlay.lineTouchHost ?: return
        val wm = FloatBallOverlay.windowManager ?: return
        val params = FloatBallOverlay.lineTouchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!FloatBallOverlay.captureSuppressed && !FloatBallOverlay.passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(FloatBallOverlay.TAG, "setLineTouchHostPassthrough failed", it) }
    }
}
