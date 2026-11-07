package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * 应用分身的统一角标：图标右上角一个黄点（与索引 / 搜索面板 / 挑应用列表同色）。
 *
 * 面板类渲染器（快捷启动器、圆环、蜂窝、全息、小组件面板……）各自画图标，
 * 以前只有挑应用列表有黄点。这里提供两种形态：
 * - [decorate]：Bitmap 版（渲染器已经拿到 Bitmap 的场合）；
 * - [wrap]：Drawable 版（图标还是 Drawable 的场合，零拷贝）。
 *
 * 两者都只在 `isClone` 为 true 时才动手，主实例完全走原路径。
 */
object CloneBadge {

    private const val DOT_COLOR = 0xFFFFC107.toInt()
    private const val RING_COLOR = 0xB3000000.toInt()

    fun decorate(bitmap: Bitmap?, isClone: Boolean): Bitmap? {
        if (!isClone || bitmap == null || bitmap.isRecycled) return bitmap
        return runCatching {
            val out = if (bitmap.isMutable) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
            }
            val size = min(out.width, out.height).toFloat()
            if (size <= 0f) return bitmap
            val canvas = Canvas(out)
            drawDot(canvas, size, 0f, 0f)
            out
        }.getOrDefault(bitmap)
    }

    fun wrap(drawable: Drawable?, isClone: Boolean): Drawable? {
        if (!isClone || drawable == null) return drawable
        return BadgeDrawable(drawable)
    }

    private fun drawDot(canvas: Canvas, size: Float, left: Float, top: Float) {
        val r = size * 0.11f
        val cx = left + size - r * 1.3f
        val cy = top + r * 1.3f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RING_COLOR }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT_COLOR }
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawCircle(cx, cy, r * 0.78f, dot)
    }

    private class BadgeDrawable(private val base: Drawable) : Drawable() {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RING_COLOR }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT_COLOR }

        override fun draw(canvas: Canvas) {
            base.setBounds(bounds)
            base.draw(canvas)
            val size = min(bounds.width(), bounds.height()).toFloat()
            if (size <= 0f) return
            val r = size * 0.11f
            val cx = bounds.right - r * 1.3f
            val cy = bounds.top + r * 1.3f
            canvas.drawCircle(cx, cy, r, ring)
            canvas.drawCircle(cx, cy, r * 0.78f, dot)
        }

        override fun setAlpha(alpha: Int) {
            base.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            base.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
            super.setBounds(left, top, right, bottom)
            base.setBounds(left, top, right, bottom)
        }
    }
}
