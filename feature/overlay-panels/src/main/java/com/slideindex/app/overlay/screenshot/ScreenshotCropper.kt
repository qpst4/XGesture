package com.slideindex.app.overlay.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.roundToInt

object ScreenshotCropper {

    /**
     * 按当前选区裁图。
     *
     * - Rectangle：直接裁；
     * - Oval / Freeform：按路径填充，路径外保持透明（PNG 能存 alpha）；
     * - Freeform 还没画出路径时退化成矩形裁，调用方本来也会禁用导出按钮。
     */
    fun crop(
        bitmap: Bitmap,
        selectionRect: Rect,
        shape: ScreenshotShape,
        freeformPath: ComposePath? = null
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

        val path = when (shape) {
            ScreenshotShape.Rectangle -> return cropped
            ScreenshotShape.Oval -> Path().apply {
                addOval(
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    Path.Direction.CW
                )
            }

            ScreenshotShape.Freeform -> {
                val freeform = freeformPath ?: return cropped
                Path(freeform.asAndroidPath()).apply {
                    offset(-left.toFloat(), -top.toFloat())
                }
            }
        }
        return fillPath(cropped, path)
    }

    /**
     * 用位图着色器填充路径，而不是 `clipPath`。
     *
     * clip 不做抗锯齿，套索的斜边会出现明显台阶；`drawPath` + `BitmapShader` 走的是
     * 带 AA 的填充，路径外天然是透明像素。代价只是一次普通绘制，比先做 mask 再 DST_IN 省一张位图。
     */
    private fun fillPath(source: Bitmap, path: Path): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, paint)
        source.recycle()
        return output
    }
}
