package com.slideindex.app.overlay.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 全局图标形状规范引擎。
 *
 * 统一处理 7 大启动面板（应用索引、边角轮盘、快速启动器、小组件面板、圆环启动器、蜂窝启动器、全息启动器）
 * 的图标轮廓裁切，实现：
 * - 方圆（Squircle，35% 圆角超椭圆）
 * - 正圆（Circle，50% 圆形）
 * - 圆角矩形（RoundedRect，22% 标准圆角）
 * - 原始自适应（Original，不进行二次几何遮罩，原样呈现）
 */
object IconShapeNormalizer {

    /**
     * 构建裁切路径。若形状为 [FvIconShape.ORIGINAL] 则返回 null（调用方无需 clip）。
     */
    @JvmStatic
    fun createClipPath(
        shape: FvIconShape,
        bounds: RectF,
        out: Path = Path(),
    ): Path? {
        if (shape.isOriginal) return null
        out.reset()
        val width = bounds.width()
        val height = bounds.height()
        val minDim = minOf(width, height)
        when (shape) {
            FvIconShape.CIRCLE -> {
                out.addCircle(bounds.centerX(), bounds.centerY(), minDim / 2f, Path.Direction.CW)
            }
            FvIconShape.SQUIRCLE -> {
                val cr = minDim * 0.35f
                out.addRoundRect(bounds, cr, cr, Path.Direction.CW)
            }
            FvIconShape.ROUNDED_RECT -> {
                val cr = minDim * 0.22f
                out.addRoundRect(bounds, cr, cr, Path.Direction.CW)
            }
            FvIconShape.SQUARE -> {
                val cr = minDim * 0.08f
                out.addRoundRect(bounds, cr, cr, Path.Direction.CW)
            }
            FvIconShape.ORIGINAL -> return null
        }
        return out
    }

    /**
     * 将位图按目标形状规范裁切。若为 [FvIconShape.ORIGINAL]，直接返回原图。
     */
    @JvmStatic
    fun normalizeBitmap(source: Bitmap, shape: FvIconShape): Bitmap {
        if (shape.isOriginal) return source
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val path = createClipPath(shape, bounds) ?: return source
        canvas.clipPath(path)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
