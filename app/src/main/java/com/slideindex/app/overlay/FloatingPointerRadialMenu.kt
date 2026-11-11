package com.slideindex.app.overlay

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.corner.CornerSlotIconBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerRadialMenuCodec
import com.slideindex.app.util.CloneBadge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal object FloatingPointerRadialMenu {
    const val SLOT_COUNT = FloatingPointerRadialMenuCodec.SLOT_COUNT

    fun sectorIndexAt(
        centerX: Float,
        centerY: Float,
        fingerX: Float,
        fingerY: Float,
        innerRadius: Float,
        outerRadius: Float
    ): Int? {
        val dx = fingerX - centerX
        val dy = fingerY - centerY
        val distance = hypot(dx, dy)
        if (distance < innerRadius || distance > outerRadius) return null
        val sweep = 360f / SLOT_COUNT
        var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        return ((angle + sweep / 2f) % 360f / sweep).toInt().coerceIn(0, SLOT_COUNT - 1)
    }

    fun iconCenterForSlot(
        center: Offset,
        slotIndex: Int,
        innerRadius: Float,
        outerRadius: Float
    ): Offset {
        val sweep = 360f / SLOT_COUNT
        val angleRad = Math.toRadians((slotIndex * sweep).toDouble())
        val ringMid = (innerRadius + outerRadius) / 2f
        return Offset(
            x = center.x + (sin(angleRad) * ringMid).toFloat(),
            y = center.y - (cos(angleRad) * ringMid).toFloat()
        )
    }
}

internal fun DrawScope.drawFloatingPointerRadialMenu(
    context: Context,
    center: Offset,
    settings: AppSettings,
    slots: List<GestureAction>,
    highlightedSlot: Int,
    visibilityProgress: Float = 1f
) {
    val alphaScale = visibilityProgress.coerceIn(0f, 1f)
    if (alphaScale <= 0.01f) return

    val outerRadius = settings.floatingPointerRadialOuterDiameterPx / 2f
    val innerRadius = settings.floatingPointerRadialInnerDiameterPx / 2f
    if (outerRadius <= innerRadius || outerRadius <= 0f) return

    val sweep = 360f / FloatingPointerRadialMenu.SLOT_COUNT
    val outerColor = Color(settings.floatingPointerRadialOuterColorArgb).scaledAlpha(alphaScale)
    val innerColor = Color(settings.floatingPointerRadialInnerColorArgb).scaledAlpha(alphaScale)
    val dividerColor = Color(settings.floatingPointerRadialDividerColorArgb).scaledAlpha(alphaScale)
    val dividerWidth = settings.floatingPointerRadialDividerThicknessPx.coerceAtLeast(1f)
    val highlightColor = outerColor.copy(alpha = (outerColor.alpha + 0.25f).coerceAtMost(1f))
    val iconSizePx = (
        (outerRadius - innerRadius) * settings.floatingPointerRadialIconSizeFraction
        ).coerceAtLeast(12f).roundToInt()
    val iconTint = settings.floatingPointerRadialIconColorArgb

    for (slot in 0 until FloatingPointerRadialMenu.SLOT_COUNT) {
        val startAngle = -90f - sweep / 2f + slot * sweep
        val fill = if (slot == highlightedSlot) highlightColor else outerColor
        drawRingSector(
            center = center,
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startAngle = startAngle,
            sweepAngle = sweep,
            color = fill
        )
        val dividerStart = polarOffset(center, outerRadius, startAngle)
        drawLine(
            color = dividerColor,
            start = polarOffset(center, innerRadius, startAngle),
            end = dividerStart,
            strokeWidth = dividerWidth
        )
    }

    drawCircle(
        color = innerColor,
        radius = innerRadius,
        center = center
    )

    drawIntoCanvas { canvas ->
        val androidCanvas = canvas.nativeCanvas
        val iconPaint = android.graphics.Paint().apply {
            alpha = (255f * alphaScale).toInt().coerceIn(0, 255)
        }
        for (slot in 0 until FloatingPointerRadialMenu.SLOT_COUNT) {
            val action = slots.getOrElse(slot) { GestureAction.None }
            if (action is GestureAction.None) continue
            val iconCenter = FloatingPointerRadialMenu.iconCenterForSlot(
                center = center,
                slotIndex = slot,
                innerRadius = innerRadius,
                outerRadius = outerRadius
            )
            val bitmap = CornerSlotIconBitmap.get(
                context = context,
                action = action,
                sizePx = iconSizePx,
                tintArgb = iconTint,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
                shape = com.slideindex.app.overlay.layout.FvIconShape.CIRCLE,
                plateFallback = false,
            )
            val left = iconCenter.x - bitmap.width / 2f
            val top = iconCenter.y - bitmap.height / 2f
            androidCanvas.drawBitmap(bitmap, left, top, iconPaint)

            val iconDiameter = bitmap.width.toFloat()
            val badges = action.actionIconBadges(
                context = context,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
            )
            when {
                badges.showShortcut -> ShortcutBadgeRenderer.draw(
                    androidCanvas,
                    iconCenter.x,
                    iconCenter.y,
                    iconDiameter,
                    alphaScale,
                    density,
                )
                badges.showShellCommand -> ShellCommandBadgeRenderer.draw(
                    androidCanvas,
                    iconCenter.x,
                    iconCenter.y,
                    iconDiameter,
                    alphaScale,
                    density,
                )
                badges.showMacro -> MacroBadgeRenderer.draw(
                    androidCanvas,
                    iconCenter.x,
                    iconCenter.y,
                    iconDiameter,
                    alphaScale,
                    density,
                )
            }
            if (badges.isClone) {
                CloneBadge.draw(
                    androidCanvas,
                    iconCenter.x,
                    iconCenter.y,
                    iconDiameter,
                    alphaScale,
                    circular = false,
                )
            }
        }
    }
}

private fun Color.scaledAlpha(scale: Float): Color = copy(alpha = alpha * scale.coerceIn(0f, 1f))

private fun DrawScope.drawRingSector(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
    color: Color
) {
    val path = Path().apply {
        addRingSector(
            center = center,
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startAngle = startAngle,
            sweepAngle = sweepAngle
        )
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = color.copy(alpha = 0.35f), style = Stroke(width = 1f))
}

private fun Path.addRingSector(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float
) {
    val outerRect = Rect(
        left = center.x - outerRadius,
        top = center.y - outerRadius,
        right = center.x + outerRadius,
        bottom = center.y + outerRadius
    )
    val innerRect = Rect(
        left = center.x - innerRadius,
        top = center.y - innerRadius,
        right = center.x + innerRadius,
        bottom = center.y + innerRadius
    )
    arcTo(
        rect = outerRect,
        startAngleDegrees = startAngle,
        sweepAngleDegrees = sweepAngle,
        forceMoveTo = true
    )
    arcTo(
        rect = innerRect,
        startAngleDegrees = startAngle + sweepAngle,
        sweepAngleDegrees = -sweepAngle,
        forceMoveTo = false
    )
    close()
}

private fun polarOffset(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = center.x + (cos(radians) * radius).toFloat(),
        y = center.y + (sin(radians) * radius).toFloat()
    )
}
