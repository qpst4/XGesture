package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 「运行宏」角标：右下角小圆 + 播放三角。
 *
 * 位置 / 尺寸与 [ShortcutBadgeRenderer]、[ShellCommandBadgeRenderer] 完全一致
 * （圆心偏移 0.34×图标、直径 max(9dp, 0.27×图标)），只换配色和字形：
 * 快捷方式 = 蓝色闪电，Shell = 深蓝灰 `$`，宏 = 紫色播放键。
 */
object MacroBadgeRenderer {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt() }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val playPath = Path()

    @JvmStatic
    fun draw(
        canvas: Canvas,
        iconCenterX: Float,
        iconCenterY: Float,
        iconDiameter: Float,
        alpha: Float,
        density: Float,
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

        playPath.reset()
        playPath.moveTo(centerX - radius * 0.26f, centerY - radius * 0.40f)
        playPath.lineTo(centerX + radius * 0.42f, centerY)
        playPath.lineTo(centerX - radius * 0.26f, centerY + radius * 0.40f)
        playPath.close()
        canvas.drawPath(playPath, glyphPaint)
    }
}
