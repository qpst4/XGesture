package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 应用分身的统一角标：图标右上角一个黄点（与索引 / 搜索面板 / 挑应用列表同色）。
 *
 * 两种用法：
 * - **绘制期** [draw]：图标会被圆形 / 圆角裁剪的场合（各启动器面板）。在裁剪之外调用，
 *   圆形图标用 `circular = true`（黄点落在 45° 圆内安全位，不会被裁掉）。
 * - **烘焙** [decorate] / [wrap]：不会被形状裁剪的普通列表 / 控件继续用，行为不变。
 *
 * 历史坑：早期只有烘焙版，圆形启动器把方角上的黄点裁掉一半——所以面板类渲染器一律改用 [draw]。
 */
object CloneBadge {

    private const val DOT_COLOR = 0xFFFFC107.toInt()
    private const val RING_COLOR = 0xB3000000.toInt()

    /** 黄点半径占图标边长的比例。 */
    private const val DOT_RADIUS_RATIO = 0.11f

    // lazy：纯位置计算（badgeCenter）不该在类加载时就碰 android.graphics.Paint，
    // 否则普通 JVM 单测会 ExceptionInInitializerError。
    private val ringPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RING_COLOR } }
    private val dotPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT_COLOR } }

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

    /**
     * 绘制期画黄点（UI 线程）。调用方必须已经结束图标的形状裁剪，否则仍会被裁。
     *
     * @param iconSize 图标边长（不是半径）
     * @param circular true = 圆形图标，黄点放 45° 圆内；false = 方形图标，贴右上角
     */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        iconSize: Float,
        alpha: Float = 1f,
        circular: Boolean = false,
    ) {
        if (iconSize <= 1f || alpha <= 0f) return
        val (cx, cy) = badgeCenter(centerX, centerY, iconSize, circular)
        val resolvedAlpha = (255f * alpha).coerceIn(0f, 255f).roundToInt()
        val r = iconSize * DOT_RADIUS_RATIO
        ringPaint.alpha = resolvedAlpha
        dotPaint.alpha = resolvedAlpha
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(cx, cy, r * 0.78f, dotPaint)
    }

    /**
     * 黄点圆心（纯函数，单测在此）。
     *
     * 方形：贴右上角，与烘焙版位置完全一致（`size - 1.3r, 1.3r`）。
     * 圆形：沿 45° 放在圆内，圆心距 `0.5D - 1.35r`，保证黄点外缘（含描边）不越界。
     */
    fun badgeCenter(
        centerX: Float,
        centerY: Float,
        iconSize: Float,
        circular: Boolean,
    ): Pair<Float, Float> {
        val r = iconSize * DOT_RADIUS_RATIO
        return if (circular) {
            val distance = iconSize * 0.5f - r * 1.35f
            val offset = distance / 1.41421356f
            (centerX + offset) to (centerY - offset)
        } else {
            val half = iconSize / 2f
            (centerX + half - r * 1.3f) to (centerY - half + r * 1.3f)
        }
    }

    private fun drawDot(canvas: Canvas, size: Float, left: Float, top: Float) {
        val r = size * DOT_RADIUS_RATIO
        val cx = left + size - r * 1.3f
        val cy = top + r * 1.3f
        // 烘焙版可能在后台线程调用：不复用 draw() 的静态 Paint
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
