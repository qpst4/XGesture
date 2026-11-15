package com.slideindex.app.macro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.slideindex.app.overlay.OverlayWindowTypes

/**
 * 触摸位置指示器：**一个**蓝点跟着手指走（录制与回放都用）。
 *
 * 形态：42dp 窗口，两个同心蓝圆 —— 外圈半径 `0.48 × 宽`（`#6658A6FF`，40% 不透明）、
 * 内点半径 `0.18 × 宽`（`#CC58A6FF`，80% 不透明）。
 *
 * 只有一个窗口：移动它，不新建（不做拖尾）。
 * 纯视觉：不抢焦点、不接触摸；抬手后短暂停留再淡出。
 */
class MacroTouchIndicator(private val hostContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = hostContext.resources.displayMetrics.density

    private var windowManager: WindowManager? = null
    private var rootView: TouchDotView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hideRunnable: Runnable? = null

    val isShowing: Boolean get() = rootView != null

    /** 显示并移动到屏幕像素点 (x, y)。 */
    fun showAt(x: Float, y: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showAt(x, y) }
            return
        }
        cancelPendingHide()
        if (rootView == null && !createWindow()) return
        val params = layoutParams ?: return
        val view = rootView ?: return
        val half = dp(DOT_WINDOW_DP) / 2f
        params.x = (x - half).toInt()
        params.y = (y - half).toInt()
        view.alpha = 1f
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    /**
     * 回放时在步骤落点"闪一下"：多一个轻微的缩放脉冲，让用户看清落点。
     *
     * **动画必须在主线程启动**（宏执行在工作线程里）：否则
     * `Animators may only be run on Looper threads` 会把整条宏执行打断。
     */
    fun pulseAt(x: Float, y: Float) {
        showAt(x, y)
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { playPulse() }
            return
        }
        playPulse()
    }

    private fun playPulse() {
        val view = rootView ?: return
        view.animate().cancel()
        view.scaleX = PULSE_START_SCALE
        view.scaleY = PULSE_START_SCALE
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(PULSE_DURATION_MS)
            .start()
    }

    /** 延迟隐藏（抬手 / 回放一个步骤之后用）。 */
    fun hideAfter(delayMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideAfter(delayMs) }
            return
        }
        cancelPendingHide()
        val runnable = Runnable { fadeOutAndHide() }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        cancelPendingHide()
        removeWindow()
    }

    private fun cancelPendingHide() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    /**
     * 淡出："隐藏"只把窗口调到全透明，**不摘窗**。
     *
     * 以前是淡出后 `removeWindow()`，下一次按下再 `createWindow()` —— 每段手势都销毁/新建一次窗口，
     * 真机上会看到圆点在抬手后闪一下。窗口常驻后就没有创建/销毁的闪动了。
     */
    private fun fadeOutAndHide() {
        val view = rootView ?: return
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .start()
    }

    private fun removeWindow() {
        val view = rootView ?: return
        rootView = null
        layoutParams = null
        runCatching { windowManager?.removeView(view) }
        windowManager = null
    }

    private fun createWindow(): Boolean {
        val manager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val size = dp(DOT_WINDOW_DP)
        val view = TouchDotView(hostContext)
        val preferredType = OverlayWindowTypes.fullscreenLauncherWindowType(hostContext)
        var params = buildParams(preferredType, size)
        var added = runCatching { manager.addView(view, params) }
        if (added.isFailure && preferredType != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            params = buildParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, size)
            added = runCatching { manager.addView(view, params) }
        }
        if (added.isFailure) {
            Log.w(TAG, "无法添加触摸指示器", added.exceptionOrNull())
            return false
        }
        windowManager = manager
        rootView = view
        layoutParams = params
        return true
    }

    private fun buildParams(type: Int, size: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

    private fun dp(value: Float): Int = (value * density).toInt().coerceAtLeast(1)

    /** 圆点本体：两个同心圆。 */
    private class TouchDotView(context: Context) : View(context) {

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = OUTER_COLOR
        }

        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = INNER_COLOR
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            canvas.drawCircle(centerX, centerY, width * OUTER_RADIUS_RATIO, outerPaint)
            canvas.drawCircle(centerX, centerY, width * INNER_RADIUS_RATIO, innerPaint)
        }
    }

    private companion object {
        const val TAG = "MacroTouchIndicator"

        const val DOT_WINDOW_DP = 42f
        const val OUTER_RADIUS_RATIO = 0.48f
        const val INNER_RADIUS_RATIO = 0.18f

        const val OUTER_COLOR = 0x6658A6FF
        const val INNER_COLOR = 0xCC58A6FF.toInt()

        const val PULSE_START_SCALE = 0.72f
        const val PULSE_DURATION_MS = 180L
        const val FADE_OUT_MS = 140L
    }
}
