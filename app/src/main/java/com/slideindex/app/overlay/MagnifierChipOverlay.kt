package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.slideindex.app.R

/**
 * 放大镜的关闭入口：屏幕顶部一个小胶囊，点它关闭放大镜，长按切换倍率。
 *
 * 系统放大镜本身没有任何按钮/窗口，这个胶囊是我们自己画的；它也会被系统一起放大，
 * 所以做得小、带描边。
 */
class MagnifierChipOverlay(
    private val context: Context,
    private val onClose: () -> Unit,
    private val onCycleScale: () -> Unit,
) {

    private val density = context.resources.displayMetrics.density
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(scaleLabel: String) {
        if (view != null) {
            update(scaleLabel)
            return
        }
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val text = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(190, 28, 28, 30))
                setStroke(dp(1f), Color.argb(150, 255, 255, 255))
            }
            text = "×"
            alpha = 0.85f
            setOnClickListener { onClose() }
            setOnLongClickListener {
                onCycleScale()
                true
            }
        }
        val layout = WindowManager.LayoutParams(
            dp(40f),
            dp(40f),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(16f)
            y = dp(16f)
        }
        val added = runCatching { wm.addView(text, layout) }.isSuccess
        if (added) {
            view = text
            params = layout
        }
    }

    fun update(scaleLabel: String) {
        // 小圆点不再显示倍率；切换倍率由控制器的 Toast 反馈。
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        params = null
        runCatching { context.getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    private fun dp(value: Float): Int = (value * density).toInt().coerceAtLeast(1)

    /** 让胶囊保持可见（部分 ROM 上窗口被系统临时隐藏后需要重新确认）。 */
    fun isShowing(): Boolean = view?.isAttachedToWindow == true

    @Suppress("unused")
    private fun noop(v: View) = Unit
}
