package com.slideindex.app.overlay.screenshot

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

@Stable
class SmartScreenshotState {

    var visible: Boolean by mutableStateOf(false)
        private set

    var screenshot: Bitmap? by mutableStateOf(null)
        private set

    var shape: ScreenshotShape by mutableStateOf(ScreenshotShape.Rectangle)
        private set

    var selectionRect: Rect by mutableStateOf(Rect.Zero)
        private set

    var forceSquareCrop: Boolean by mutableStateOf(false)
        private set

    var isCapturing: Boolean by mutableStateOf(false)
        private set

    private var minSelectionSizePx by mutableIntStateOf(0)
    val imageBounds: Rect
        get() {
            val bitmap = screenshot ?: return Rect.Zero
            return Rect(Offset.Zero, Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
        }
    val minSelectionSize: Float
        get() = minSelectionSizePx.toFloat()

    fun startCapture() {
        visible = false
        screenshot = null
        selectionRect = Rect.Zero
        forceSquareCrop = false
        isCapturing = true
    }

    fun cancelCapture() {
        isCapturing = false
    }

    fun show(
        screenshot: Bitmap,
        minSelectionSizePx: Int
    ) {
        this.screenshot = screenshot
        this.minSelectionSizePx = minSelectionSizePx
        shape = ScreenshotShape.Rectangle
        selectionRect = defaultSelectionRect(screenshot.width.toFloat(), screenshot.height.toFloat())
        forceSquareCrop = false
        visible = true
        isCapturing = false
    }

    fun dismiss() {
        visible = false
        screenshot = null
        selectionRect = Rect.Zero
        shape = ScreenshotShape.Rectangle
        forceSquareCrop = false
        isCapturing = false
    }

    fun updateShape(shape: ScreenshotShape) {
        this.shape = shape
    }

    fun updateSelection(rect: Rect) {
        selectionRect = clampRect(rect)
    }

    fun updateForceSquareCrop(enabled: Boolean) {
        if (forceSquareCrop == enabled) {
            return
        }
        forceSquareCrop = enabled
        if (enabled) {
            selectionRect = clampSquareRect(squareRectByCenter(selectionRect))
        }
    }

    private fun defaultSelectionRect(width: Float, height: Float): Rect {
        val targetWidth = width * 0.6f
        val targetHeight = height * 0.35f
        val left = (width - targetWidth) / 2f
        val top = (height - targetHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + targetWidth,
            bottom = top + targetHeight
        )
    }

    private fun squareRectByCenter(rect: Rect): Rect {
        val side = min(rect.width, rect.height)
        val half = side / 2f
        val center = rect.center
        return Rect(
            left = center.x - half,
            top = center.y - half,
            right = center.x + half,
            bottom = center.y + half
        )
    }

    private fun clampSquareRect(rect: Rect): Rect {
        val bitmap = screenshot ?: return rect
        val maxWidth = bitmap.width.toFloat()
        val maxHeight = bitmap.height.toFloat()
        val side = rect.width
            .coerceAtLeast(minSelectionSizePx.toFloat())
            .coerceAtMost(min(maxWidth, maxHeight))
        val center = rect.center
        val left = (center.x - side / 2f).coerceIn(0f, maxWidth - side)
        val top = (center.y - side / 2f).coerceIn(0f, maxHeight - side)
        return Rect(
            left = left,
            top = top,
            right = left + side,
            bottom = top + side
        )
    }

    private fun clampRect(rect: Rect): Rect {
        val bitmap = screenshot ?: return rect
        val minSize = minSelectionSizePx.toFloat()
        val maxWidth = bitmap.width.toFloat()
        val maxHeight = bitmap.height.toFloat()

        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom

        if (right - left < minSize) {
            right = left + minSize
        }
        if (bottom - top < minSize) {
            bottom = top + minSize
        }

        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (right > maxWidth) {
            val overflow = right - maxWidth
            left -= overflow
            right = maxWidth
        }
        if (bottom > maxHeight) {
            val overflow = bottom - maxHeight
            top -= overflow
            bottom = maxHeight
        }

        left = left.coerceAtLeast(0f)
        top = top.coerceAtLeast(0f)
        right = right.coerceAtMost(maxWidth)
        bottom = bottom.coerceAtMost(maxHeight)

        if (right - left < minSize) {
            right = (left + minSize).coerceAtMost(maxWidth)
            left = (right - minSize).coerceAtLeast(0f)
        }
        if (bottom - top < minSize) {
            bottom = (top + minSize).coerceAtMost(maxHeight)
            top = (bottom - minSize).coerceAtLeast(0f)
        }

        return Rect(left, top, right, bottom)
    }
}
