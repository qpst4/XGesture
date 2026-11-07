package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

/** Screen-region helpers for float-ball OCR fallback. */
object FloatBallOcrRegions {
    private const val POINT_PICK_HALF_DP = 80f
    private const val MIN_CROP_SIDE_PX = 24
    const val CROP_EDGE_PADDING_PX = 3
    /** Extra margin around pick rects for QR quiet zone before bitmap crop. */
    const val PICK_CROP_EXPAND_FRACTION = 0.12f

    /** Matches [android.view.accessibility.AccessibilityNodeInfo.getBoundsInScreen] coordinate space. */
    fun accessibilityScreenSizePx(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            val metrics = context.resources.displayMetrics
            return metrics.widthPixels to metrics.heightPixels
        }
        val bounds = wm.maximumWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    fun padScreenRect(rect: Rect, paddingPx: Int = CROP_EDGE_PADDING_PX): Rect {
        if (paddingPx <= 0) return Rect(rect)
        return Rect(
            rect.left - paddingPx,
            rect.top - paddingPx,
            rect.right + paddingPx,
            rect.bottom + paddingPx
        )
    }

    fun expandScreenRect(
        rect: Rect,
        fraction: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Rect {
        if (fraction <= 0f) return Rect(rect)
        val padX = (rect.width() * fraction).roundToInt()
        val padY = (rect.height() * fraction).roundToInt()
        return clampToScreen(
            Rect(
                rect.left - padX,
                rect.top - padY,
                rect.right + padX,
                rect.bottom + padY
            ),
            screenWidth,
            screenHeight
        )
    }

    fun expandPoint(
        metrics: DisplayMetrics,
        x: Float,
        y: Float,
        screenWidth: Int = metrics.widthPixels,
        screenHeight: Int = metrics.heightPixels
    ): Rect {
        val halfPx = (POINT_PICK_HALF_DP * metrics.density).roundToInt()
        val cx = x.roundToInt()
        val cy = y.roundToInt()
        return clampToScreen(
            Rect(cx - halfPx, cy - halfPx, cx + halfPx, cy + halfPx),
            screenWidth,
            screenHeight
        )
    }

    /**
     * Maps logical screen coordinates to screenshot bitmap coordinates.
     * [takeScreenshot] buffer size can differ from the logical screen (e.g. gesture nav
     * excluded from capture while accessibility bounds still use full screen height).
     */
    fun mapScreenRectToBitmap(
        screenRect: Rect,
        screenWidth: Int,
        screenHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Rect {
        if (screenWidth <= 0 || screenHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return Rect(screenRect)
        }
        if (screenWidth == bitmapWidth && screenHeight == bitmapHeight) {
            return Rect(screenRect)
        }
        val scaleX = bitmapWidth.toFloat() / screenWidth
        val scaleY = bitmapHeight.toFloat() / screenHeight
        val left = (screenRect.left * scaleX).roundToInt()
        val top = (screenRect.top * scaleY).roundToInt()
        val right = (screenRect.right * scaleX).roundToInt().coerceAtLeast(left + 1)
        val bottom = (screenRect.bottom * scaleY).roundToInt().coerceAtLeast(top + 1)
        return Rect(left, top, right, bottom)
    }

    fun clampToScreen(rect: Rect, screenWidth: Int, screenHeight: Int): Rect {
        val left = rect.left.coerceIn(0, screenWidth)
        val top = rect.top.coerceIn(0, screenHeight)
        val right = rect.right.coerceIn(0, screenWidth)
        val bottom = rect.bottom.coerceIn(0, screenHeight)
        val safe = Rect(left, top, right, bottom)
        if (safe.width() < MIN_CROP_SIDE_PX) {
            val pad = (MIN_CROP_SIDE_PX - safe.width()) / 2 + 1
            safe.left = (safe.left - pad).coerceAtLeast(0)
            safe.right = (safe.right + pad).coerceAtMost(screenWidth)
        }
        if (safe.height() < MIN_CROP_SIDE_PX) {
            val pad = (MIN_CROP_SIDE_PX - safe.height()) / 2 + 1
            safe.top = (safe.top - pad).coerceAtLeast(0)
            safe.bottom = (safe.bottom + pad).coerceAtMost(screenHeight)
        }
        return safe
    }
}
