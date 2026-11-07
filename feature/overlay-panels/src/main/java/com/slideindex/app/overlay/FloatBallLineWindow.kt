package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 边缘线条独立窄窗（对齐 FV 的 `f4177h` + `l.onDraw`）：
 * 每条边一个「宽 = 线宽、高 = 屏幕高」的小窗，里面是纯 View 的 [FloatBallLineView]。
 *
 * 相比原来把线条画在全屏 Compose 层里，合成面积从整屏降到一条边。
 * 目前尚未接线（[FloatBallOverlay] 还没创建它），接线时按下面的方法调用即可。
 */
class FloatBallLineWindow(
    private val context: Context,
    private val alignRight: Boolean,
) {
    private val lineView = FloatBallLineView(context).apply {
        this.alignRight = this@FloatBallLineWindow.alignRight
    }

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var attached = false

    fun attach() {
        if (attached) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = if (alignRight) "XG:ball-line-right" else "XG:ball-line-left"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val added = runCatching { wm.addView(lineView, lp) }.isSuccess
        if (!added) return
        windowManager = wm
        layoutParams = lp
        attached = true
    }

    /**
     * 以"触发区条带"为准：窗口 = 条带矩形，线画在贴屏幕边的那一侧。
     *
     * @param stripLeft 条带左边界（屏幕坐标）
     * @param stripTop 条带上边界
     * @param stripWidth 条带宽度（= 触发区宽度）
     * @param stripHeight 条带高度（= 触发区高度）
     */
    fun apply(
        stripLeft: Int,
        stripTop: Int,
        stripWidth: Int,
        stripHeight: Int,
        lineWidthPx: Float,
        color: Int,
        previewAlpha: Float,
        visible: Boolean,
    ) {
        if (!attached) return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        lineView.lineWidthPx = lineWidthPx
        lineView.lineColor = color
        lineView.lineStartPx = 0f
        lineView.lineLengthPx = stripHeight.toFloat()
        lineView.previewAlpha = previewAlpha
        lineView.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        val width = stripWidth.coerceAtLeast(1)
        val height = stripHeight.coerceAtLeast(1)
        if (lp.x != stripLeft || lp.y != stripTop || lp.width != width || lp.height != height) {
            lp.x = stripLeft
            lp.y = stripTop
            lp.width = width
            lp.height = height
            runCatching { wm.updateViewLayout(lineView, lp) }
        }
    }

    fun isAttached(): Boolean = attached

    fun dispose() {
        if (!attached) return
        attached = false
        runCatching { windowManager?.removeViewImmediate(lineView) }
        windowManager = null
        layoutParams = null
    }
}
