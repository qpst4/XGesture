package com.slideindex.app.overlay.corner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.slideindex.app.settings.CornerGestureSettings

@SuppressLint("ViewConstructor")
class CornerZonePreviewView(context: Context) : View(context) {
    private var cornerSettings = CornerGestureSettings()
    private var zoneLayout = CornerZoneLayout()
    private var density = 1f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val zonePath = Path()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
    }

    fun update(
        zoneLayout: CornerZoneLayout,
        settings: CornerGestureSettings,
        density: Float,
    ) {
        this.zoneLayout = zoneLayout
        this.cornerSettings = settings
        this.density = density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = 6f * density
        strokePaint.strokeWidth = 1.5f * density
        val screenW = width.toFloat()
        val screenH = height.toFloat()

        if (cornerSettings.leftEnabled && cornerSettings.hasActiveTriggerZone()) {
            drawShape(
                canvas = canvas,
                anchor = CornerAnchor.LEFT,
                shape = zoneLayout.zoneShape(CornerAnchor.LEFT),
                screenW = screenW,
                screenH = screenH,
                cornerRadius = cornerRadius,
                fillArgb = 0x240D9488,
                strokeArgb = 0xCC2DD4BF.toInt(),
            )
        }
        if (cornerSettings.rightEnabled && cornerSettings.hasActiveTriggerZone()) {
            drawShape(
                canvas = canvas,
                anchor = CornerAnchor.RIGHT,
                shape = zoneLayout.zoneShape(CornerAnchor.RIGHT),
                screenW = screenW,
                screenH = screenH,
                cornerRadius = cornerRadius,
                fillArgb = 0x240D9488,
                strokeArgb = 0xCC14B8A6.toInt(),
            )
        }
    }

    private fun drawShape(
        canvas: Canvas,
        anchor: CornerAnchor,
        shape: CornerZoneShape,
        screenW: Float,
        screenH: Float,
        cornerRadius: Float,
        fillArgb: Int,
        strokeArgb: Int,
    ) {
        fillPaint.color = fillArgb
        strokePaint.color = strokeArgb
        drawZoneRect(canvas, shape.verticalRect, cornerRadius, anchor, screenW, screenH)
        drawZoneRect(canvas, shape.horizontalRect, cornerRadius, anchor, screenW, screenH)
    }

    private fun drawZoneRect(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        anchor: CornerAnchor,
        screenW: Float,
        screenH: Float,
    ) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        zonePath.reset()
        zonePath.addRoundRect(
            rect,
            edgeAwareCornerRadii(rect, cornerRadius, anchor, screenW, screenH),
            Path.Direction.CW,
        )
        canvas.drawPath(zonePath, fillPaint)
        canvas.drawPath(zonePath, strokePaint)
    }

    /** 贴屏幕边缘的角不做圆角，避免预览区看起来离物理边角有间隙。 */
    private fun edgeAwareCornerRadii(
        rect: RectF,
        radius: Float,
        anchor: CornerAnchor,
        screenW: Float,
        screenH: Float,
    ): FloatArray {
        val r = radius.coerceAtLeast(0f)
        val onBottom = rect.bottom >= screenH - 0.5f
        val onLeft = rect.left <= 0.5f
        val onRight = rect.right >= screenW - 0.5f
        val tl = if (onLeft && anchor == CornerAnchor.LEFT) 0f else r
        val tr = if (onRight && anchor == CornerAnchor.RIGHT) 0f else r
        val bl = if (onBottom && onLeft && anchor == CornerAnchor.LEFT) 0f else if (onBottom) 0f else r
        val br = if (onBottom && onRight && anchor == CornerAnchor.RIGHT) 0f else if (onBottom) 0f else r
        return floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    }
}
