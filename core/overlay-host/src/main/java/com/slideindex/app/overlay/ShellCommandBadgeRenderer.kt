package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.roundToInt

object ShellCommandBadgeRenderer {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF455A64.toInt() }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    @JvmStatic
    fun draw(
        canvas: Canvas,
        iconCenterX: Float,
        iconCenterY: Float,
        iconDiameter: Float,
        alpha: Float,
        density: Float
    ) {
        if (alpha <= 0f || iconDiameter <= 1f) return
        val badgeDiameter = max(9f * density, iconDiameter * 0.27f)
        val radius = badgeDiameter / 2f
        val centerX = iconCenterX + iconDiameter * 0.34f
        val centerY = iconCenterY + iconDiameter * 0.34f
        val resolvedAlpha = (255f * alpha).coerceIn(0f, 255f).roundToInt()
        borderPaint.alpha = resolvedAlpha
        backgroundPaint.alpha = resolvedAlpha
        glyphPaint.alpha = resolvedAlpha
        canvas.drawCircle(centerX, centerY, radius + 1.5f * density, borderPaint)
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        glyphPaint.textSize = badgeDiameter * 0.72f
        canvas.drawText(
            "$",
            centerX,
            centerY - (glyphPaint.descent() + glyphPaint.ascent()) / 2f,
            glyphPaint
        )
    }
}
