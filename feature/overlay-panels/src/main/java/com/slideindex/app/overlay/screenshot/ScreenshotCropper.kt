package com.slideindex.app.overlay.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

object ScreenshotCropper {

    fun crop(
        bitmap: Bitmap,
        selectionRect: Rect,
        shape: ScreenshotShape
    ): Bitmap {
        val left = selectionRect.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = selectionRect.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val right = selectionRect.right.roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = selectionRect.bottom.roundToInt().coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top

        val source = Bitmap.createBitmap(bitmap, left, top, width, height)
        val cropped = source.copy(Bitmap.Config.ARGB_8888, false)
        if (cropped !== source) {
            source.recycle()
        }
        if (shape == ScreenshotShape.Rectangle) {
            return cropped
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = Path().apply {
            addOval(
                android.graphics.RectF(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat()
                ),
                Path.Direction.CW
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        canvas.restore()
        cropped.recycle()
        return output
    }
}
