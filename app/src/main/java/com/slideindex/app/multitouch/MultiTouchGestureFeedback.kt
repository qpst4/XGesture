package com.slideindex.app.multitouch

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 手势触发时的底部胶囊提示（类似系统手势提示的短时浮层）。
 *
 * 只在无障碍服务的 overlay context 下可用（TYPE_ACCESSIBILITY_OVERLAY）；
 * 没有 overlay context 时静默跳过，不影响动作执行。
 */
object MultiTouchGestureFeedback {
    private val handler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var windowManager: WindowManager? = null

    fun show(context: Context, text: String) {
        handler.post {
            runCatching {
                dismiss()
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@runCatching
                val density = context.resources.displayMetrics.density
                val label = TextView(context).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(
                        (20 * density).toInt(),
                        (10 * density).toInt(),
                        (20 * density).toInt(),
                        (10 * density).toInt(),
                    )
                    background = GradientDrawable().apply {
                        cornerRadius = 26f * density
                        setColor(Color.argb(224, 28, 28, 30))
                    }
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (64 * density).toInt()
                }
                wm.addView(label, params)
                view = label
                windowManager = wm
                handler.postDelayed({ dismiss() }, SHOW_DURATION_MS)
            }
        }
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        runCatching { windowManager?.removeView(current) }
        windowManager = null
    }

    private const val SHOW_DURATION_MS = 1_400L
}
