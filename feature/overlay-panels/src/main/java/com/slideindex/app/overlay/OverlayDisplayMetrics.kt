package com.slideindex.app.overlay

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import kotlin.math.max

/**
 * 悬浮窗布局用真实 dp→px 换算。Accessibility / Application Context 的 [DisplayMetrics.density]
 * 在部分机型上偏低，需结合 WindowMetrics 与屏宽反推。
 */
object OverlayDisplayMetrics {
    fun resolve(
        context: Context,
        windowManager: WindowManager? = null,
        densityHint: Float? = null
    ): DisplayMetrics {
        val wm = windowManager ?: context.getSystemService(WindowManager::class.java)
        val host = context.resources.displayMetrics
        val system = Resources.getSystem()
        val systemMetrics = system.displayMetrics
        val bounds = runCatching { wm?.currentWindowMetrics?.bounds }.getOrNull()
        val screenWidthPx = bounds?.width()?.toFloat()
            ?: host.widthPixels.toFloat().coerceAtLeast(1f)

        // host Context（尤其 Accessibility）density 偏低时，configuration.screenWidthDp 会被同步放大，
        // 导致 screenWidthPx / screenWidthDp 仍≈1；须用 System Resources 的 sw-dp 反推。
        val widthDp = system.configuration.screenWidthDp.coerceAtLeast(1)
        val fromWidth = screenWidthPx / widthDp

        val density = listOfNotNull(
            densityHint,
            max(systemMetrics.density, systemMetrics.densityDpi / 160f),
            fromWidth,
            max(host.density, host.densityDpi / 160f)
        ).max()

        return DisplayMetrics().apply {
            setTo(host)
            this.density = density
            densityDpi = (density * DisplayMetrics.DENSITY_DEFAULT).toInt()
            widthPixels = screenWidthPx.toInt()
            if (bounds != null) {
                heightPixels = bounds.height()
            }
        }
    }

    fun dpToPx(dp: Float, metrics: DisplayMetrics): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)

    fun screenWidthPx(
        context: Context,
        windowManager: WindowManager? = null,
        metrics: DisplayMetrics
    ): Float {
        val wm = windowManager ?: context.getSystemService(WindowManager::class.java)
        val bounds = runCatching { wm?.currentWindowMetrics?.bounds }.getOrNull()
        return bounds?.width()?.toFloat()
            ?: metrics.widthPixels.toFloat().coerceAtLeast(1f)
    }
}
