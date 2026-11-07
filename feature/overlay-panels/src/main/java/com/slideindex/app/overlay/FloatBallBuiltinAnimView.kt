package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withClip
import com.slideindex.app.settings.FloatBallStyleType

/**
 * Circular clipped canvas animation surface (~20fps Handler tick, no AVD/Lottie).
 */
class FloatBallBuiltinAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val circlePath = Path()
    private val circleRect = RectF()
    private var styleType: FloatBallStyleType? = null
    private var paused = false
    private var startUptimeMs = SystemClock.uptimeMillis()

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (paused || styleType == null) return
            invalidate()
            mainHandler.postDelayed(this, TICK_MS)
        }
    }

    fun setStyle(type: FloatBallStyleType) {
        if (styleType == type) return
        styleType = type
        startUptimeMs = SystemClock.uptimeMillis()
        invalidate()
        if (!paused) {
            startTicking()
        }
    }

    fun setPaused(pause: Boolean) {
        if (paused == pause) return
        paused = pause
        if (pause) {
            stopTicking()
        } else {
            startUptimeMs = SystemClock.uptimeMillis()
            startTicking()
            invalidate()
        }
    }

    fun releaseAnimation() {
        stopTicking()
        styleType = null
        invalidate()
    }

    fun snapshotCurrentFrame(): Bitmap? {
        val type = styleType ?: return null
        val sizePx = width.coerceAtLeast(1)
        return FloatBallBuiltinAnimRenderer.snapshot(
            sizePx = sizePx,
            alpha = viewOpacity(),
            styleType = type,
            timeMs = elapsedMs()
        )
    }

    private fun elapsedMs(): Long = SystemClock.uptimeMillis() - startUptimeMs

    private fun startTicking() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    private fun stopTicking() {
        mainHandler.removeCallbacks(tickRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circleRect.set(0f, 0f, w.toFloat(), h.toFloat())
        circlePath.reset()
        circlePath.addOval(circleRect, Path.Direction.CW)
        if (w > 0 && h > 0) {
            invalidate()
        }
    }

    private fun viewOpacity(): Float = alpha.coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val type = styleType ?: return
        if (width <= 0 || height <= 0) return
        canvas.withClip(circlePath) {
            FloatBallBuiltinAnimRenderer.draw(
                canvas = this,
                sizePx = width.coerceAtMost(height).coerceAtLeast(1),
                alpha = viewOpacity(),
                styleType = type,
                timeMs = elapsedMs()
            )
        }
    }

    override fun onDetachedFromWindow() {
        // ⚠ 这里**不能**调 releaseAnimation()（它会把 styleType 清成 null）。
        // 悬浮球窗口在取词/截图期间会被 removeViewImmediate 摘掉、结束后再 addView 挂回来，而且是
        // **同一个 View 实例**（FloatBallOverlay.detachChromeWindowsForCapture / reattachChromeWindowsAfterCapture）。
        // 一旦在 detach 时清掉样式，重新挂上时唯一的重启入口 FloatBallIconView.showBuiltin() → setStyle()
        // 又会因为 bindKey 未变而在 FloatBallIconView.bind() 里早退 → styleType 永远为 null →
        // onDraw 第一行直接 return → 球变成完全透明（用户实测：换成纸飞机/呼吸光圈/星轨后，取词一次球就"消失"且点不到）。
        // 静态图/自定义图片/GIF 不受影响，是因为它们的画面不依赖 View 内部这份"当前样式"。
        stopTicking()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 重新挂回窗口：把动画接着放下去（样式仍在），暂停状态（拖动中）保持。
        if (styleType != null && !paused) {
            startUptimeMs = SystemClock.uptimeMillis()
            startTicking()
            invalidate()
        }
    }

    companion object {
        private const val TICK_MS = 50L
    }
}
