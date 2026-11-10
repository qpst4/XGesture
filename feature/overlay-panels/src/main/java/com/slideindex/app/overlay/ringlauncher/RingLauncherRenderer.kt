package com.slideindex.app.overlay.ringlauncher

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.HintRingRenderer
import com.slideindex.app.overlay.MacroBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.overlay.layout.FvRingLauncherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvPanelLayout
import com.slideindex.app.overlay.layout.FvToolbarButton
import com.slideindex.app.settings.FvRingLauncherSlotIconOverride
import com.slideindex.app.shell.ShellCommand
import kotlin.math.min

object RingLauncherRenderer {
    private const val ICON_PLATE = 0xFF303034.toInt()
    private const val ICON_SHADOW = 0x55000000
    private const val HIGHLIGHT_RING = 0xFFFFFFFF.toInt()
    private const val EMPTY_FILL = 0xEEFFFFFF.toInt()
    private const val EMPTY_FILL_HIGHLIGHT = 0xFFFFFFFF.toInt()
    private const val EMPTY_STROKE = 0xCCFFFFFF.toInt()
    private const val EMPTY_STROKE_HIGHLIGHT = 0xFFFFFFFF.toInt()
    private const val PLUS_COLOR = 0xEEFFFFFF.toInt()
    private const val PLUS_COLOR_HIGHLIGHT = 0xFFFFFFFF.toInt()
    private const val TOOLBAR_FILL = 0xF22B3137.toInt()
    private const val TOOLBAR_FILL_HIGHLIGHT = 0xFF3A4249.toInt()
    private const val TOOLBAR_GLYPH = 0xFFFFFFFF.toInt()
    private const val NAME_TEXT = 0xFFFFFFFF.toInt()
    private const val PREVIEW_BG = 0xF21E2328.toInt()
    private const val SELECTION_SCALE = 1.18f

    fun buildLayout(
        circleCount: Int,
        side: FvRingLauncherSide,
        anchorX: Float,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
        iconSizeDp: Float = FvCircleLayoutEngine.ICON_SIZE_DP,
        iconShape: com.slideindex.app.overlay.layout.FvIconShape = com.slideindex.app.overlay.layout.FvIconShape.ROUNDED_RECT,
        baseRadiusDp: Float = FvCircleLayoutEngine.DEFAULT_BASE_RADIUS_DP,
        layerGapDp: Float = FvCircleLayoutEngine.DEFAULT_LAYER_GAP_DP,
        endMarginDeg: Float = FvCircleLayoutEngine.DEFAULT_END_MARGIN_DEG,
    ): FvPanelLayout = FvCircleLayoutEngine.layout(
        circleCount = circleCount,
        side = side,
        anchorX = anchorX,
        anchorY = anchorY,
        screenWidth = screenWidth,
        density = density,
        iconSizeDp = iconSizeDp,
        iconShape = iconShape,
        baseRadiusDp = baseRadiusDp,
        layerGapDp = layerGapDp,
        endMarginDeg = endMarginDeg,
    )

    fun draw(
        context: Context,
        canvas: Canvas,
        layout: FvPanelLayout,
        targets: List<HoneycombRuntimeTarget?>,
        editMode: Boolean,
        highlightedSlot: Int,
        highlightedToolbarButton: FvToolbarButton?,
        showToolbar: Boolean,
        density: Float,
        revealProgress: Float,
        appsByPackage: Map<String, AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        appRepository: AppRepository? = null,
        editDragFromSlot: Int = -1,
        editDragX: Float = 0f,
        editDragY: Float = 0f,
        slotIconOverrides: Map<Int, FvRingLauncherSlotIconOverride> = emptyMap(),
        /**
         * 提示卡片里图标外圈的进度（长按倒计时）：0..1；**-1 = 不画**。
         */
        hintRingProgress: Float = -1f,
        /** 长按到时间后拼到名字后面的后缀（如 "-小窗" / "-全屏"）；空串表示不加。 */
        hintLabelSuffix: String = "",
        /** 长按到时间后是否在图标右下角画"小窗"（画中画）角标。 */
        hintPipArmed: Boolean = false,
    ) {
        val progress = revealProgress.coerceIn(0f, 1f)
        if (progress <= 0.01f) return

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ICON_SHADOW }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseRadius = layout.itemSizePx * 0.5f

        for (pass in 0..1) {
            for (slot in layout.slots.indices) {
                val highlighted = slot == highlightedSlot
                if (pass == 0 && highlighted) continue
                if (pass == 1 && !highlighted) continue

                val target = targets.getOrNull(slot)
                val isEmpty = target == null
                if (!editMode && isEmpty) continue
                if (editDragFromSlot >= 0 && slot == editDragFromSlot) continue

                val slotLayout = layout.slots[slot]
                val centerX = layout.anchorX + (slotLayout.centerX - layout.anchorX) * progress
                val centerY = layout.anchorY + (slotLayout.centerY - layout.anchorY) * progress
                val scale = if (highlighted) SELECTION_SCALE else 1f
                val radius = baseRadius * scale

                if (isEmpty && editMode) {
                    drawEmptySlot(
                        canvas = canvas,
                        centerX = centerX,
                        centerY = centerY,
                        radius = radius,
                        highlighted = highlighted,
                        density = density,
                        progress = progress,
                        cornerRadiusRatio = layout.cornerRadiusRatio,
                        shadowPaint = shadowPaint,
                        fillPaint = fillPaint,
                        strokePaint = strokePaint,
                        plusPaint = plusPaint,
                    )
                } else if (target != null) {
                    val iconSizePx = (radius * 2f).toInt().coerceAtLeast(12)
                    val bitmap = RingLauncherSlotIconBitmap.get(
                        context = context,
                        item = target.item,
                        sizePx = iconSizePx,
                        appsByPackage = appsByPackage,
                        activityShortcuts = activityShortcuts,
                        shellCommands = shellCommands,
                        resolvedIcon = target.icon,
                        appRepository = appRepository,
                        iconOverride = slotIconOverrides[slot],
                    )
                    drawAppIcon(
                        canvas = canvas,
                        centerX = centerX,
                        centerY = centerY,
                        radius = radius,
                        bitmap = bitmap,
                        highlighted = highlighted,
                        density = density,
                        progress = progress,
                        cornerRadiusRatio = layout.cornerRadiusRatio,
                        shadowPaint = shadowPaint,
                        strokePaint = strokePaint,
                        iconPaint = iconPaint,
                        isClone = target.isClone,
                    )
                    val drawSize = min(bitmap.width.toFloat(), radius * 2f)
                    if (target.isShortcut) {
                        ShortcutBadgeRenderer.draw(canvas, centerX, centerY, drawSize, progress, density)
                    }
                    if (target.isShellCommandBadge) {
                        ShellCommandBadgeRenderer.draw(canvas, centerX, centerY, drawSize, progress, density)
                    }
                    if (target.isMacroBadge) {
                        MacroBadgeRenderer.draw(canvas, centerX, centerY, drawSize, progress, density)
                    }
                }
            }
        }

        if (showToolbar) {
            FvToolbarButton.entries.forEach { button ->
                val (cx, cy) = layout.toolbarButtonCenters.getOrNull(button.ordinal) ?: return@forEach
                drawToolbarButton(
                    canvas = canvas,
                    centerX = cx,
                    centerY = cy,
                    radius = layout.toolbarButtonRadiusPx,
                    highlighted = button == highlightedToolbarButton,
                    density = density,
                    fillPaint = fillPaint,
                    glyphPaint = plusPaint,
                    button = button,
                )
            }
        }

        if (editDragFromSlot >= 0) {
            val dragTarget = targets.getOrNull(editDragFromSlot)
            if (dragTarget != null) {
                val dragRadius = baseRadius * 1.12f
                val iconSizePx = (dragRadius * 2f).toInt().coerceAtLeast(12)
                val bitmap = RingLauncherSlotIconBitmap.get(
                    context = context,
                    item = dragTarget.item,
                    sizePx = iconSizePx,
                    appsByPackage = appsByPackage,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    resolvedIcon = dragTarget.icon,
                    appRepository = appRepository,
                    iconOverride = slotIconOverrides[editDragFromSlot],
                )
                drawAppIcon(
                    canvas = canvas,
                    centerX = editDragX,
                    centerY = editDragY,
                    radius = dragRadius,
                    bitmap = bitmap,
                    isClone = dragTarget.isClone,
                    highlighted = true,
                    density = density,
                    progress = progress,
                    cornerRadiusRatio = layout.cornerRadiusRatio,
                    shadowPaint = shadowPaint,
                    strokePaint = strokePaint,
                    iconPaint = iconPaint,
                )
            }
        }

        if (highlightedSlot >= 0 && editDragFromSlot < 0) {
            val target = targets.getOrNull(highlightedSlot)
            if (target != null) {
                val bigIconSizePx = (52f * density).toInt().coerceAtLeast(24)
                val bigBitmap = RingLauncherSlotIconBitmap.get(
                    context = context,
                    item = target.item,
                    sizePx = bigIconSizePx,
                    appsByPackage = appsByPackage,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    resolvedIcon = target.icon,
                    appRepository = appRepository,
                    iconOverride = slotIconOverrides[highlightedSlot],
                )
                val previewBadgeInfo = drawSelectionPreview(
                    canvas = canvas,
                    side = layout.side,
                    // 长按到时间 → 名字后面拼上目标形态（-小窗 / -全屏）
                    label = if (hintRingProgress >= 1f) target.label + hintLabelSuffix else target.label,
                    bitmap = bigBitmap,
                    density = density,
                    progress = progress,
                    cornerRadiusRatio = layout.cornerRadiusRatio,
                    hintRingProgress = hintRingProgress,
                    hintPipArmed = hintPipArmed,
                    isClone = target.isClone,
                )
                if (previewBadgeInfo != null) {
                    val (badgeCx, badgeCy, badgeSize) = previewBadgeInfo
                    if (target.isShortcut) {
                        ShortcutBadgeRenderer.draw(canvas, badgeCx, badgeCy, badgeSize, progress, density)
                    }
                    if (target.isShellCommandBadge) {
                        ShellCommandBadgeRenderer.draw(canvas, badgeCx, badgeCy, badgeSize, progress, density)
                    }
                    if (target.isMacroBadge) {
                        MacroBadgeRenderer.draw(canvas, badgeCx, badgeCy, badgeSize, progress, density)
                    }
                }
            }
        }
    }

    private val iconDstRect = RectF()
    private val iconHighlightRect = RectF()
    private val iconShadowRect = RectF()
    /** 提示卡片里图标外圈的进度用矩形（复用，避免每帧分配）。 */
    private val hintRingRect = RectF()
    private val iconPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconClipPath = Path()

    private fun drawAppIcon(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        bitmap: android.graphics.Bitmap,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
        shadowPaint: Paint,
        strokePaint: Paint,
        iconPaint: Paint,
        isClone: Boolean,
    ) {
        val diameter = radius * 2f
        val left = centerX - radius
        val top = centerY - radius
        val right = left + diameter
        val bottom = top + diameter
        iconDstRect.set(left, top, right, bottom)
        val cornerRadius = diameter * cornerRadiusRatio

        // 浅色模式下底板也要跟着变浅
        iconPlatePaint.color = if (com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconPlateCurrentDark?.invoke() == true) {
            ICON_PLATE
        } else {
            0xFFF1F3F7.toInt()
        }
        iconPlatePaint.alpha = 255
        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, centerY, radius, iconPlatePaint)
        } else {
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, iconPlatePaint)
        }

        if (highlighted) {
            val shadowOffset = 3f * density * progress
            shadowPaint.color = ICON_SHADOW
            iconShadowRect.set(left, top + shadowOffset, right, bottom + shadowOffset)
            if (cornerRadiusRatio >= 0.49f) {
                canvas.drawCircle(centerX, centerY + shadowOffset, radius, shadowPaint)
            } else {
                canvas.drawRoundRect(iconShadowRect, cornerRadius, cornerRadius, shadowPaint)
            }
        }

        iconClipPath.reset()
        if (cornerRadiusRatio >= 0.49f) {
            iconClipPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        } else {
            iconClipPath.addRoundRect(iconDstRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        val iconClipSaveCount = canvas.save()
        canvas.clipPath(iconClipPath)
        iconPaint.isFilterBitmap = true
        iconPaint.alpha = 255
        canvas.drawBitmap(bitmap, null, iconDstRect, iconPaint)
        canvas.restoreToCount(iconClipSaveCount)

        // 分身黄点：裁剪之外画，圆形图标用 45° 圆内安全位
        if (isClone) {
            com.slideindex.app.util.CloneBadge.draw(
                canvas,
                centerX,
                centerY,
                diameter,
                progress,
                circular = cornerRadiusRatio >= 0.49f,
            )
        }

        strokePaint.style = Paint.Style.STROKE
        strokePaint.pathEffect = null
        if (highlighted) {
            strokePaint.color = HIGHLIGHT_RING
            strokePaint.strokeWidth = 2.5f * density
            val strokeOffset = 1.5f * density
            iconHighlightRect.set(
                left - strokeOffset,
                top - strokeOffset,
                right + strokeOffset,
                bottom + strokeOffset,
            )
            val highlightCorner = cornerRadius + strokeOffset
            if (cornerRadiusRatio >= 0.49f) {
                canvas.drawCircle(centerX, centerY, radius + strokeOffset, strokePaint)
            } else {
                canvas.drawRoundRect(iconHighlightRect, highlightCorner, highlightCorner, strokePaint)
            }
        } else {
            strokePaint.color = 0x22FFFFFF.toInt()
            strokePaint.strokeWidth = 1f * density
            if (cornerRadiusRatio >= 0.49f) {
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
            } else {
                canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, strokePaint)
            }
        }
    }

    private fun drawEmptySlot(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
        plusPaint: Paint,
    ) {
        val shadowOffset = 2f * density * progress
        val diameter = radius * 2f
        val left = centerX - radius
        val top = centerY - radius
        val right = left + diameter
        val bottom = top + diameter
        val cornerRadius = diameter * cornerRadiusRatio

        fillPaint.color = if (highlighted) EMPTY_FILL_HIGHLIGHT else EMPTY_FILL
        strokePaint.style = Paint.Style.STROKE
        strokePaint.pathEffect = null
        strokePaint.color = if (highlighted) EMPTY_STROKE_HIGHLIGHT else EMPTY_STROKE
        strokePaint.strokeWidth = 1.75f * density

        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, centerY + shadowOffset, radius * 1.02f, shadowPaint)
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
        } else {
            iconShadowRect.set(left, top + shadowOffset, right, bottom + shadowOffset)
            canvas.drawRoundRect(iconShadowRect, cornerRadius, cornerRadius, shadowPaint)
            iconDstRect.set(left, top, right, bottom)
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, strokePaint)
        }

        plusPaint.color = if (highlighted) PLUS_COLOR_HIGHLIGHT else PLUS_COLOR
        plusPaint.textSize = radius * 0.82f
        val textY = centerY - (plusPaint.descent() + plusPaint.ascent()) / 2f
        canvas.drawText("+", centerX, textY, plusPaint)
    }

    private fun drawToolbarButton(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        density: Float,
        fillPaint: Paint,
        glyphPaint: Paint,
        button: FvToolbarButton,
    ) {
        val (baseColor, highlightColor) = when (button) {
            FvToolbarButton.MOVE -> 0xFF0288D1.toInt() to 0xFF039BE5.toInt()
            FvToolbarButton.PIN -> 0xFF43A047.toInt() to 0xFF4CAF50.toInt()
            FvToolbarButton.SETTINGS -> 0xFF607D8B.toInt() to 0xFF78909C.toInt()
            FvToolbarButton.KEYBOARD -> 0xFF455A64.toInt() to 0xFF546E7A.toInt()
        }
        val drawRadius = if (highlighted) radius * 1.14f else radius

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        canvas.drawCircle(centerX, centerY + 2f * density, drawRadius * 1.04f, shadowPaint)

        fillPaint.color = if (highlighted) highlightColor else baseColor
        canvas.drawCircle(centerX, centerY, drawRadius, fillPaint)

        if (highlighted) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 2f * density
            }
            canvas.drawCircle(centerX, centerY, drawRadius + 1f * density, ringPaint)
        }

        glyphPaint.color = Color.WHITE
        glyphPaint.textSize = drawRadius * 1.05f
        val glyph = when (button) {
            FvToolbarButton.MOVE -> "↕"
            FvToolbarButton.PIN -> "✎"
            FvToolbarButton.SETTINGS -> "⚙"
            FvToolbarButton.KEYBOARD -> "⌨"
        }
        val textY = centerY - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText(glyph, centerX, textY, glyphPaint)
    }

    private fun drawSelectionPreview(
        canvas: Canvas,
        side: FvRingLauncherSide,
        label: String,
        bitmap: android.graphics.Bitmap,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
        /** 提示卡片里图标外圈的进度：0..1；-1 = 不画。 */
        hintRingProgress: Float = -1f,
        /** 长按到时间且目标是"小窗"时，在图标右下角画画中画角标。 */
        hintPipArmed: Boolean = false,
        /** 分身条目：图标右上角补黄点（画在形状裁剪之外）。 */
        isClone: Boolean = false,
    ): Triple<Float, Float, Float>? {
        val centerX = canvas.width * 0.5f
        val iconSizePx = 54f * density
        val iconRadius = iconSizePx * 0.5f
        val previewInsetPx = 92f * density
        val iconCenterY = when (side) {
            FvRingLauncherSide.TOP -> canvas.height - previewInsetPx
            FvRingLauncherSide.BOTTOM,
            FvRingLauncherSide.LEFT,
            FvRingLauncherSide.RIGHT,
            -> previewInsetPx
        }
        val alpha = (255f * progress).toInt().coerceIn(0, 255)

        val iconRect = RectF(
            centerX - iconRadius,
            iconCenterY - iconRadius,
            centerX + iconRadius,
            iconCenterY + iconRadius,
        )
        val previewCornerRadius = iconSizePx * cornerRadiusRatio

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            this.alpha = (0x55 * progress).toInt().coerceIn(0, 255)
        }
        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, iconCenterY + 3f * density, iconRadius, shadowPaint)
        } else {
            canvas.drawRoundRect(
                iconRect.left,
                iconRect.top + 3f * density,
                iconRect.right,
                iconRect.bottom + 3f * density,
                previewCornerRadius,
                previewCornerRadius,
                shadowPaint,
            )
        }

        // 浅色模式下底板也要跟着变浅
        iconPlatePaint.color = if (com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconPlateCurrentDark?.invoke() == true) {
            ICON_PLATE
        } else {
            0xFFF1F3F7.toInt()
        }
        iconPlatePaint.alpha = 255
        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, iconCenterY, iconRadius, iconPlatePaint)
        } else {
            canvas.drawRoundRect(iconRect, previewCornerRadius, previewCornerRadius, iconPlatePaint)
        }

        iconClipPath.reset()
        if (cornerRadiusRatio >= 0.49f) {
            iconClipPath.addCircle(centerX, iconCenterY, iconRadius, Path.Direction.CW)
        } else {
            iconClipPath.addRoundRect(iconRect, previewCornerRadius, previewCornerRadius, Path.Direction.CW)
        }
        val previewClipSaveCount = canvas.save()
        canvas.clipPath(iconClipPath)
        val previewIconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(bitmap, null, iconRect, previewIconPaint)
        canvas.restoreToCount(previewClipSaveCount)

        if (isClone) {
            com.slideindex.app.util.CloneBadge.draw(
                canvas,
                centerX,
                iconCenterY,
                iconSizePx,
                alpha / 255f,
                circular = cornerRadiusRatio >= 0.49f,
            )
        }

        // 图标外圈的进度线（长按倒计时）：外扩 = 半边笔宽 + 1dp 间隙，贴在图标外侧。
        if (hintRingProgress >= 0f) {
            val inflate = HintRingRenderer.STROKE_DP * density * 0.5f + 1f * density
            hintRingRect.set(
                iconRect.left - inflate,
                iconRect.top - inflate,
                iconRect.right + inflate,
                iconRect.bottom + inflate,
            )
            HintRingRenderer.draw(
                canvas = canvas,
                icon = bitmap,
                bounds = hintRingRect,
                progress = hintRingProgress,
                density = density,
            )
        }
        // 长按到时间且目标是"小窗" → 图标右下角一个画中画角标
        if (hintPipArmed && hintRingProgress >= 1f) {
            HintRingRenderer.drawPipBadge(canvas = canvas, iconBounds = iconRect, density = density)
        }

        if (label.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f * density
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                this.alpha = alpha
                setShadowLayer(3.5f * density, 0f, 1.5f * density, Color.BLACK)
            }
            val maxWidth = canvas.width * 0.7f
            val fitted = TextUtils.ellipsize(label, textPaint, maxWidth, TextUtils.TruncateAt.END)
            val textY = when (side) {
                FvRingLauncherSide.TOP ->
                    iconCenterY - iconRadius - 16f * density - textPaint.descent() * 0.4f
                FvRingLauncherSide.BOTTOM,
                FvRingLauncherSide.LEFT,
                FvRingLauncherSide.RIGHT,
                ->
                    iconCenterY + iconRadius + 16f * density - textPaint.ascent() * 0.4f
            }
            canvas.drawText(fitted, 0, fitted.length, centerX, textY, textPaint)
        }

        return Triple(centerX, iconCenterY, iconSizePx)
    }
}
