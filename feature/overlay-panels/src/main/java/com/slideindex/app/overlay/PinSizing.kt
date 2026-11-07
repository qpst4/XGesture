package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 区域截图时的坐标映射元数据（逻辑屏幕 ↔ takeScreenshot buffer）。 */
data class ScreenshotLayoutMeta(
    val screenWidth: Int,
    val screenHeight: Int,
    val captureWidth: Int,
    val captureHeight: Int
)

data class RegionalScreenshotCrop(
    val bitmap: Bitmap,
    val layoutMeta: ScreenshotLayoutMeta
)

private const val IMAGE_PIN_MAX_WIDTH_FRACTION = 0.55f
private const val IMAGE_PIN_MAX_HEIGHT_FRACTION = 0.55f

/** Maps pick [screenRect] into screenshot / overlay coordinates when sizes differ. */
fun resolvePinPlacementRect(
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?
): Rect? {
    if (screenRect == null || screenRect.isEmpty) return null
    if (layoutMeta == null ||
        (layoutMeta.screenWidth == layoutMeta.captureWidth &&
            layoutMeta.screenHeight == layoutMeta.captureHeight)
    ) {
        return Rect(screenRect)
    }
    return FloatBallOcrRegions.mapScreenRectToBitmap(
        screenRect = screenRect,
        screenWidth = layoutMeta.screenWidth,
        screenHeight = layoutMeta.screenHeight,
        bitmapWidth = layoutMeta.captureWidth,
        bitmapHeight = layoutMeta.captureHeight
    )
}

fun resolvePinImageDisplaySizePx(
    bitmap: Bitmap,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
    screenWidthPx: Int,
    screenHeightPx: Int
): Pair<Int, Int> {
    resolvePinPlacementRect(screenRect, layoutMeta)?.takeUnless { it.isEmpty }?.let { placement ->
        return placement.width().coerceAtLeast(1) to placement.height().coerceAtLeast(1)
    }
    val maxW = (screenWidthPx * IMAGE_PIN_MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
    val maxH = (screenHeightPx * IMAGE_PIN_MAX_HEIGHT_FRACTION).roundToInt().coerceAtLeast(1)
    val bmpW = bitmap.width.coerceAtLeast(1)
    val bmpH = bitmap.height.coerceAtLeast(1)
    val scale = min(maxW.toFloat() / bmpW, maxH.toFloat() / bmpH).coerceAtMost(1f)
    return max((bmpW * scale).roundToInt(), 1) to max((bmpH * scale).roundToInt(), 1)
}

fun buildScreenshotLayoutMeta(
    bitmap: Bitmap,
    screenWidthPx: Int,
    screenHeightPx: Int
): ScreenshotLayoutMeta {
    return ScreenshotLayoutMeta(
        screenWidth = screenWidthPx,
        screenHeight = screenHeightPx,
        captureWidth = bitmap.width.coerceAtLeast(1),
        captureHeight = bitmap.height.coerceAtLeast(1)
    )
}
