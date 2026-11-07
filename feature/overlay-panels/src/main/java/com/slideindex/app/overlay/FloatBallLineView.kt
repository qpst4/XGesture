package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/**
 * 边缘线条（纯 View 版，替代运行时的 Compose 线条层）。
 *
 * 对齐 FV 的做法：一个 View + `onDraw` 直接画一条竖向圆角线，
 * 放进一个「窄窗」里，合成面积从整屏降到一条边。
 *
 * 设置页的实时预览仍然走 Compose —— 那边不影响运行性能。
 */
@SuppressLint("ViewConstructor")
class FloatBallLineView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** 线宽（px）。 */
    var lineWidthPx: Float = 6f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 线的颜色（含 alpha）。 */
    var lineColor: Int = Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                paint.color = value
                invalidate()
            }
        }

    /** 线的竖直起点/长度（px，相对本窗）。 */
    var lineStartPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var lineLengthPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 触发区预览带的透明度（0 = 不画），宽度铺满整个窗口。 */
    var previewAlpha: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** true = 线贴在窗口右侧（左边缘的线），false = 贴在左侧。 */
    var alignRight: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        paint.color = lineColor
    }

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // 触发区预览带：铺满窗口，半透明
        if (previewAlpha > 0f) {
            bandPaint.color = (lineColor and 0x00FFFFFF) or
                ((previewAlpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
            val r = 3f * resources.displayMetrics.density
            canvas.drawRoundRect(0f, 0f, w, h, r, r, bandPaint)
        }
        val stroke = lineWidthPx.coerceIn(1f, w)
        val length = if (lineLengthPx > 0f) min(lineLengthPx, h) else h
        val top = lineStartPx.coerceIn(0f, (h - length).coerceAtLeast(0f))
        val left = if (alignRight) w - stroke else 0f
        val radius = stroke / 2f
        canvas.drawRoundRect(left, top, left + stroke, top + length, radius, radius, paint)
    }
}
