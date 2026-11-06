package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager

data class ScreenMetricsSnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val rotation: Int = Surface.ROTATION_0,
) {
    val isLandscape: Boolean
        get() = isDisplayLandscapeRotation(rotation) || widthPx > heightPx
}

/** 覆盖层布局用真实屏幕尺寸（含导航栏区域），与 SideGesture ScreenUtils 一致。 */
object OverlayScreenMetrics {
    fun snapshot(context: Context): ScreenMetricsSnapshot {
        val (w, h) = sizePx(context)
        return ScreenMetricsSnapshot(w, h, displayRotation(context))
    }

    fun snapshot(wm: WindowManager, fallback: DisplayMetrics): ScreenMetricsSnapshot {
        val (w, h) = sizePx(wm, fallback)
        val rotation = runCatching {
            wm.defaultDisplay.rotation
        }.getOrDefault(Surface.ROTATION_0)
        return ScreenMetricsSnapshot(w, h, rotation)
    }

    fun sizePx(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val fallback = context.resources.displayMetrics
        return if (wm != null) sizePx(wm, fallback) else fallback.widthPixels to fallback.heightPixels
    }

    fun sizePx(wm: WindowManager, fallback: DisplayMetrics): Pair<Int, Int> {
        val bounds = runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            return bounds.width() to bounds.height()
        }
        return fallback.widthPixels to fallback.heightPixels
    }

    fun displayRotation(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        val fromWm = wm?.let { runCatching { it.defaultDisplay.rotation }.getOrNull() }
        if (fromWm != null) return fromWm
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }
}

/** 横屏判定：仅按旋转角，不看宽高比（模块内与 :app 共用）。 */
fun isDisplayLandscapeRotation(rotation: Int): Boolean =
    rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

