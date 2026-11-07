package com.slideindex.app.overlay.filebrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * 文件拖拽快照：把当前行画成一张圆角卡片，避免系统默认的整行矩形阴影。
 */
class FileBrowserDragShadow(
    private val source: Bitmap,
    private val touchX: Float,
    private val touchY: Float,
    private val count: Int = 1,
) : View.DragShadowBuilder() {

    override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
        outShadowSize.set(source.width, source.height)
        outShadowTouchPoint.set(
            touchX.coerceIn(0f, source.width.toFloat()).toInt(),
            touchY.coerceIn(0f, source.height.toFloat()).toInt(),
        )
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = 18f
        val rect = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        if (count > 1) {
            val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                setShadowLayer(10f, 0f, 5f, 0x33000000)
            }
            canvas.drawRoundRect(
                RectF(12f, 12f, source.width + 12f, source.height + 12f),
                radius,
                radius,
                backPaint,
            )
            canvas.drawRoundRect(
                RectF(6f, 6f, source.width + 6f, source.height + 6f),
                radius,
                radius,
                backPaint,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            setShadowLayer(14f, 0f, 7f, 0x55000000)
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.restore()
        if (count > 1) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xE6207CF6.toInt()
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 34f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val cx = source.width - 42f
            val cy = 42f
            canvas.drawCircle(cx, cy, 30f, badgePaint)
            canvas.drawText(count.toString(), cx, cy + 12f, textPaint)
        }
    }
}
