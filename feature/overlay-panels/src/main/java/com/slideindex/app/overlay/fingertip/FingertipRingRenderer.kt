package com.slideindex.app.overlay.fingertip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withClip
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.isShellActivityShortcut
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.settings.FingertipRingCodec
import com.slideindex.app.shell.ShellCommand
import kotlin.math.roundToInt

internal object FingertipRingRenderer {
    private const val ICON_TINT = 0xFFFFFFFF.toInt()
    private const val BG_NORMAL = 0xB3282830.toInt()
    private const val BG_HIGHLIGHT = 0xE63D8BFD.toInt()
    private const val HIGHLIGHT_STROKE_PX = 2.5f
    private val iconClipPath = Path()

    fun draw(
        context: Context,
        canvas: Canvas,
        slots: List<GestureAction>,
        centerX: Float,
        centerY: Float,
        highlightedSlot: Int,
        orbitRadiusPx: Float,
        iconSizePx: Float,
        density: Float,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        alphaScale: Float = 1f,
    ) {
        val slotCount = slots.size
        if (slotCount <= 0) return
        val scale = alphaScale.coerceIn(0f, 1f)
        if (scale <= 0.01f) return

        val orbitRadius = FingertipRingCodec.effectiveOrbitRadiusPx(orbitRadiusPx)
        val iconSize = FingertipRingCodec.effectiveIconSizePx(iconSizePx)
        val iconSizeInt = iconSize.roundToInt().coerceAtLeast(12)
        val bgRadius = FingertipRingCodec.iconBackgroundRadiusPx(iconSize)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = HIGHLIGHT_STROKE_PX
            color = 0xFFFFFFFF.toInt()
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255f * scale).toInt().coerceIn(0, 255)
        }

        for (slot in 0 until slotCount) {
            val action = slots.getOrElse(slot) { GestureAction.None }
            if (action is GestureAction.None) continue
            val (iconX, iconY) = FingertipRingGeometry.iconCenterForSlot(
                centerX, centerY, slot, slotCount, orbitRadius,
            )
            val highlighted = slot == highlightedSlot
            fillPaint.color = applyAlpha(if (highlighted) BG_HIGHLIGHT else BG_NORMAL, scale)
            canvas.drawCircle(iconX, iconY, bgRadius, fillPaint)
            if (highlighted) {
                strokePaint.alpha = (255f * scale).toInt().coerceIn(0, 255)
                canvas.drawCircle(iconX, iconY, bgRadius + 2f, strokePaint)
            }

            val drawDiameter = bgRadius * 2f
            val bitmap = resolveSlotIconBitmap(
                context = context,
                action = action,
                iconSizeInt = iconSizeInt,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
            ) ?: continue
            val left = iconX - drawDiameter / 2f
            val top = iconY - drawDiameter / 2f
            iconClipPath.rewind()
            iconClipPath.addCircle(iconX, iconY, bgRadius * 0.96f, Path.Direction.CW)
            canvas.withClip(iconClipPath) {
                drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + drawDiameter, top + drawDiameter), iconPaint)
            }

            when {
                action is GestureAction.LaunchShortcut -> {
                    when {
                        action.showsShellActivityShortcutBadge(activityShortcuts) -> {
                            ShellCommandBadgeRenderer.draw(canvas, iconX, iconY, drawDiameter, scale, density)
                        }
                        !action.isShellActivityShortcut(activityShortcuts) -> {
                            ShortcutBadgeRenderer.draw(canvas, iconX, iconY, drawDiameter, scale, density)
                        }
                    }
                }
                action.showsShellCommandBadge(shellCommands) -> {
                    ShellCommandBadgeRenderer.draw(canvas, iconX, iconY, drawDiameter, scale, density)
                }
            }
        }
    }

    private fun resolveSlotIconBitmap(
        context: Context,
        action: GestureAction,
        iconSizeInt: Int,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ) = when (action) {
        is GestureAction.LaunchApp,
        is GestureAction.LaunchShortcut,
        is GestureAction.ExecuteShellCommand,
        -> com.slideindex.app.overlay.panels.PanelRuntimeHooks.cornerSlotIconBitmap?.invoke(
            context,
            action,
            iconSizeInt,
            ICON_TINT,
            activityShortcuts,
            shellCommands,
        )
        else -> com.slideindex.app.overlay.panels.PanelRuntimeHooks.actionIconBitmap
            ?.invoke(context, action, iconSizeInt, ICON_TINT)
    }

    private fun applyAlpha(argb: Int, scale: Float): Int {
        val alpha = ((argb ushr 24) and 0xFF) * scale
        return (argb and 0x00FFFFFF) or (alpha.roundToInt().coerceIn(0, 255) shl 24)
    }
}
