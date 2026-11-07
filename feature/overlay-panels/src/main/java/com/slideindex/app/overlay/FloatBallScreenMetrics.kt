package com.slideindex.app.overlay

import android.content.Context
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * 悬浮球布局与触摸命中统一使用的屏幕尺寸（与 WM 触摸窗坐标系一致）。
 */
object FloatBallScreenMetrics {
    fun bounds(
        context: Context,
        windowManager: WindowManager? = null
    ): OverlayScreenBounds {
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            val rect = com.slideindex.app.perf.PerfProbe.probe("WM.currentWindowMetrics") {
                runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
            }
            if (rect != null) {
                return OverlayScreenBounds(
                    width = rect.width().toFloat(),
                    height = rect.height().toFloat()
                )
            }
        }
        val metrics = context.resources.displayMetrics
        return OverlayScreenBounds(
            width = metrics.widthPixels.toFloat(),
            height = metrics.heightPixels.toFloat()
        )
    }

    fun sizePx(
        context: Context,
        windowManager: WindowManager? = null
    ): Pair<Int, Int> {
        val b = bounds(context, windowManager)
        return b.width.roundToInt() to b.height.roundToInt()
    }
}
