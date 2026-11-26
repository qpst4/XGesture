package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.Path
import androidx.core.graphics.withClip
import androidx.core.graphics.withScale
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.launcher.isCloneTarget
import com.slideindex.app.launcher.isShellActivityShortcut
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.HintRingRenderer
import com.slideindex.app.overlay.MacroBadgeRenderer
import com.slideindex.app.overlay.QuickLauncherIconMask
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandLabelResolver
import com.slideindex.app.util.CloneBadge
import kotlin.math.min

object CornerRadialMenuRenderer {
    private val slotIconClipPath = Path()
    private val slotShapePath = Path()
    private val slotShapeRect = RectF()
    /** 提示卡片里图标外圈的进度用矩形（复用，避免每帧分配）。 */
    private val hintRingRect = RectF()
    private const val ICON_TINT = 0xFF374151.toInt()
    private const val ICON_TINT_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val BUBBLE_FILL = 0xFFFFFFFF.toInt()
    private const val BUBBLE_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val BUBBLE_FILL_EMPTY = 0xFFF8FAFC.toInt()
    private const val BUBBLE_STROKE = 0x1A000000
    private const val BUBBLE_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()
    private const val BUBBLE_STROKE_EMPTY = 0x33000000
    private const val SHADOW_COLOR = 0x33000000
    private const val EDIT_FILL = 0xFFFFFFFF.toInt()
    private const val EDIT_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val EDIT_STROKE = 0x33000000
    private const val EDIT_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()
    private const val PLUS_COLOR = 0xFF64748B.toInt()
    private const val PLUS_COLOR_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val NAME_PILL_FILL = 0xDD20263F.toInt()
    private const val NAME_TEXT = 0xFFFFFFFF.toInt()
private const val NAME_PILL_FILL_LIGHT = 0xE6FFFFFF.toInt()
private const val NAME_TEXT_LIGHT = 0xFF1E293B.toInt()
    private const val SUBMENU_PILL_FILL = 0xFFFDFDFD.toInt()
    private const val SUBMENU_PILL_FILL_HIGHLIGHT = 0xFFEFFCF9.toInt()
    private const val SUBMENU_TEXT = 0xFF1E293B.toInt()
    private const val SUBMENU_TEXT_HIGHLIGHT = 0xFF0F766E.toInt()
    private const val SUBMENU_STROKE = 0x14000000
    private const val SUBMENU_STROKE_HIGHLIGHT = 0xFF5EEAD4.toInt()
    private const val SUBMENU_SHADOW = 0x28000000

    fun draw(
        context: Context,
        canvas: Canvas,
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        slots: List<GestureAction>,
        highlightedSlot: Int,
        highlightedEditButton: Boolean,
        editMode: Boolean,
        activeLayerCount: Int,
        density: Float,
        revealProgress: Float,
        globalIconShape: FvIconShape = FvIconShape.SQUIRCLE,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
        appRepository: AppRepository? = null,
        hintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
        shortcutSubMenuLayout: CornerShortcutSubMenuLayout? = null,
        shortcutSubMenuItems: List<GestureAction> = emptyList(),
        highlightedShortcutIndex: Int = -1,
        shortcutSubMenuRevealProgress: Float = 1f,
        /**
         * 提示卡片里图标外圈的进度（长按倒计时）：0..1；**-1 = 不画**。
         */
        longPressRingProgress: Float = -1f,
        /** 长按到时间后拼到名字后面的后缀（如 "-小窗" / "-全屏"）；空串表示不加。 */
        hintLabelSuffix: String = "",
        /** 长按到时间后是否在图标右下角画"小窗"（画中画）角标。 */
        hintPipArmed: Boolean = false,
        /** 当前是否深色主题：命中提示卡片在日间要用浅色底（反馈 #9）。 */
        isDark: Boolean = true,
    ) {
        val progress = revealProgress.coerceIn(0f, 1f)
        if (progress <= 0.01f) return
        val effectiveShape = settings.slotIconShape.resolve(globalIconShape)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHADOW_COLOR }
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

        // 编辑模式由调用方把 activeLayerCount 传成"已启用的层数"，因此空槽位只在启用层内渲染。
        val lastSlot = CornerRadialMenuGeometry.lastVisibleSlotIndex(activeLayerCount)

        for (slot in 0..lastSlot) {
            val action = slots.getOrElse(slot) { GestureAction.None }
            val isEmpty = action is GestureAction.None
            if (!editMode && isEmpty) continue

            val target = CornerRadialMenuGeometry.bubbleCenterForSlot(
                anchor = anchor,
                anchorX = anchorX,
                anchorY = anchorY,
                slotIndex = slot,
                settings = settings,
                density = density,
            )
            val centerX = anchorX + (target.x - anchorX) * progress
            val centerY = anchorY + (target.y - anchorY) * progress
            val highlighted = slot == highlightedSlot
            val bubbleRadius = CornerRadialMenuGeometry.bubbleRadiusPx(settings, density, slot)
            val scale = if (highlighted) 1.06f else 1f
            val radius = bubbleRadius * scale

            if (isEmpty && editMode) {
                drawBubble(
                    canvas = canvas,
                    centerX = centerX,
                    centerY = centerY,
                    radius = radius,
                    highlighted = highlighted,
                    empty = true,
                    density = density,
                    progress = progress,
                    shadowPaint = shadowPaint,
                    fillPaint = fillPaint,
                    strokePaint = strokePaint,
                    shape = effectiveShape,
                )
                plusPaint.color = if (highlighted) PLUS_COLOR_HIGHLIGHT else PLUS_COLOR
                plusPaint.textSize = radius * 1.1f
                val textY = centerY - (plusPaint.descent() + plusPaint.ascent()) / 2f
                canvas.drawText("+", centerX, textY, plusPaint)
            } else if (!isEmpty) {
                drawFilledSlotIcon(
                    canvas = canvas,
                    context = context,
                    centerX = centerX,
                    centerY = centerY,
                    radius = radius,
                    highlighted = highlighted,
                    action = action,
                    density = density,
                    progress = progress,
                    shadowPaint = shadowPaint,
                    strokePaint = strokePaint,
                    iconPaint = iconPaint,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    appRepository = appRepository,
                    shape = effectiveShape,
                )
            }
        }

        if (settings.showEditButton) {
            drawEditButton(
                context = context,
                canvas = canvas,
                anchor = anchor,
                anchorX = anchorX,
                anchorY = anchorY,
                settings = settings,
                highlighted = highlightedEditButton,
                density = density,
                progress = progress,
                fillPaint = fillPaint,
                strokePaint = strokePaint,
            )
        }

        if (settings.showSelectedName && !editMode && highlightedSlot >= 0 &&
            shortcutSubMenuLayout == null
        ) {
            val action = slots.getOrElse(highlightedSlot) { GestureAction.None }
            if (action !is GestureAction.None) {
                drawSelectedHint(
                    context = context,
                    canvas = canvas,
                    action = action,
                    density = density,
                    progress = progress,
                    hintIconSizeDp = hintIconSizeDp,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    appRepository = appRepository,
                    shape = effectiveShape,
                    longPressRingProgress = longPressRingProgress,
                    hintLabelSuffix = hintLabelSuffix,
                    hintPipArmed = hintPipArmed,
                    isDark = isDark,
                )
            }
        }

        if (shortcutSubMenuLayout != null && shortcutSubMenuItems.isNotEmpty()) {
            drawShortcutSubMenu(
                context = context,
                canvas = canvas,
                items = shortcutSubMenuItems,
                layout = shortcutSubMenuLayout,
                highlightedIndex = highlightedShortcutIndex,
                density = density,
                progress = progress,
                revealProgress = shortcutSubMenuRevealProgress,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
                appRepository = appRepository,
            )
        }
    }

    private fun drawShortcutSubMenu(
        context: Context,
        canvas: Canvas,
        items: List<GestureAction>,
        layout: CornerShortcutSubMenuLayout,
        highlightedIndex: Int,
        density: Float,
        progress: Float,
        revealProgress: Float,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        appRepository: AppRepository?,
    ) {
        val menuAlpha = (255f * progress).toInt().coerceIn(0, 255)
        val reveal = revealProgress.coerceIn(0f, 1f)
        if (menuAlpha <= 0 || reveal <= 0.01f) return

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.75f * density
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUBMENU_SHADOW }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            textSize = 13f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val iconLeading = 10f * density
        val iconSize = 20f * density
        val iconGap = 8f * density
        val paddingRight = 12f * density
        val iconContentWidth = CornerShortcutSubMenuLayoutCalculator.iconContentWidthPx(density)
        val itemCount = items.size

        items.forEachIndexed { index, shortcut ->
            val rect = layout.itemRects.getOrNull(index) ?: return@forEachIndexed
            val highlighted = index == highlightedIndex
            val stagger = ((reveal * itemCount) - index).coerceIn(0f, 1f)
            val itemAlpha = (menuAlpha * stagger).toInt().coerceIn(0, 255)
            if (itemAlpha <= 0) return@forEachIndexed

            val scale = 0.94f + 0.06f * stagger + if (highlighted) 0.03f else 0f
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val drawWidth = rect.width() * scale
            val drawHeight = rect.height() * scale
            val drawRect = RectF(
                centerX - drawWidth / 2f,
                centerY - drawHeight / 2f,
                centerX + drawWidth / 2f,
                centerY + drawHeight / 2f,
            )
            val radius = drawHeight / 2f

            fillPaint.color = if (highlighted) SUBMENU_PILL_FILL_HIGHLIGHT else SUBMENU_PILL_FILL
            fillPaint.alpha = itemAlpha
            strokePaint.color = if (highlighted) SUBMENU_STROKE_HIGHLIGHT else SUBMENU_STROKE
            strokePaint.alpha = (itemAlpha * 0.85f).toInt().coerceIn(0, 255)

            for (layer in 2 downTo 1) {
                val offset = (layer * 1.2f * density * reveal)
                shadowPaint.alpha = (itemAlpha * (0.14f * layer)).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(
                    drawRect.left + offset,
                    drawRect.top + offset,
                    drawRect.right + offset,
                    drawRect.bottom + offset,
                    radius,
                    radius,
                    shadowPaint,
                )
            }
            canvas.drawRoundRect(drawRect, radius, radius, fillPaint)
            canvas.drawRoundRect(drawRect, radius, radius, strokePaint)

            canvas.withClip(drawRect) {
            val iconCenterX = drawRect.left + iconLeading + iconSize / 2f
            val iconCenterY = drawRect.centerY()
            val iconBitmap = CornerSlotIconBitmap.get(
                context = context,
                action = shortcut,
                sizePx = iconSize.toInt().coerceAtLeast(12),
                tintArgb = ICON_TINT,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
                appRepository = appRepository,
            )
            val iconScale = iconSize / iconBitmap.width
            canvas.withScale(iconScale, iconScale, iconCenterX, iconCenterY) {
                drawBitmap(
                    iconBitmap,
                    iconCenterX - iconBitmap.width / 2f,
                    iconCenterY - iconBitmap.height / 2f,
                    iconPaint.apply { alpha = itemAlpha },
                )
            }
            // 角标只对「快捷方式」有意义（反馈 #385 之后二级菜单也能放应用/内置动作）。
            if (shortcut is GestureAction.LaunchShortcut) {
                when {
                    shortcut.showsShellActivityShortcutBadge(activityShortcuts) -> {
                        ShellCommandBadgeRenderer.draw(
                            canvas,
                            iconCenterX,
                            iconCenterY,
                            iconSize,
                            itemAlpha / 255f,
                            density,
                        )
                    }
                    !shortcut.isShellActivityShortcut(activityShortcuts) -> {
                        ShortcutBadgeRenderer.draw(
                            canvas,
                            iconCenterX,
                            iconCenterY,
                            iconSize,
                            itemAlpha / 255f,
                            density,
                        )
                    }
                }
            }

            val label = actionLabel(context, shortcut, shellCommands)
            val textStart = drawRect.left + iconContentWidth
            val textMaxWidth = (drawRect.right - paddingRight - textStart).coerceAtLeast(0f)
            val fitted = TextUtils.ellipsize(label, textPaint, textMaxWidth, TextUtils.TruncateAt.END)
            textPaint.color = if (highlighted) SUBMENU_TEXT_HIGHLIGHT else SUBMENU_TEXT
            textPaint.alpha = itemAlpha
            val metrics = textPaint.fontMetrics
            val baseline = drawRect.centerY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(fitted, 0, fitted.length, textStart, baseline, textPaint)
            }
        }
    }

    private fun drawSelectedHint(
        context: Context,
        canvas: Canvas,
        action: GestureAction,
        density: Float,
        progress: Float,
        hintIconSizeDp: Int,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        appRepository: AppRepository?,
        shape: FvIconShape,
        /** 提示卡片里图标外圈的进度：0..1；-1 = 不画。 */
        longPressRingProgress: Float = -1f,
        /** 长按到时间后拼到名字后面的后缀（如 "-小窗" / "-全屏"）；空串表示不加。 */
        hintLabelSuffix: String = "",
        /** 长按到时间后是否在图标右下角画"小窗"（画中画）角标。 */
        hintPipArmed: Boolean = false,
        /** 当前是否深色主题：命中提示卡片在日间要用浅色底（反馈 #9）。 */
        isDark: Boolean = true,
    ) {
        // 长按到时间 → 名字后面拼上目标形态（-小窗 / -全屏）
        val label = actionLabel(context, action, shellCommands) +
            if (longPressRingProgress >= 1f) hintLabelSuffix else ""
        if (label.isBlank()) return

        val iconSizeDp = SelectedHintMetrics.clampIconSizeDp(hintIconSizeDp)
        // 日间：浅色底 + 深色字；夜间：深藏青底 + 白字（反馈 #9）。
        val pillFill = if (isDark) NAME_PILL_FILL else NAME_PILL_FILL_LIGHT
        val labelColor = if (isDark) NAME_TEXT else NAME_TEXT_LIGHT
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = SelectedHintMetrics.textSizePx(iconSizeDp, density)
            textAlign = Paint.Align.LEFT
            alpha = (255f * progress).toInt().coerceIn(0, 255)
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pillFill
            // 日间底色本来就浅，用更高的不透明度才压得住背景。
            val baseAlpha = if (isDark) 221f else 240f
            alpha = (baseAlpha * progress).toInt().coerceIn(0, 255)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255f * progress).toInt().coerceIn(0, 255)
        }

        val maxTextWidth = min(180f * density, canvas.width * 0.48f)
        val fitted = TextUtils.ellipsize(label, namePaint, maxTextWidth, TextUtils.TruncateAt.END)
        val textWidth = namePaint.measureText(fitted, 0, fitted.length)
        val iconSize = iconSizeDp * density
        val paddingX = SelectedHintMetrics.paddingXPx(density)
        val gap = SelectedHintMetrics.gapPx(density)
        val boxHeight = SelectedHintMetrics.boxHeightPx(iconSizeDp, density)
        val boxWidth = paddingX * 2f + iconSize + gap + textWidth

        // 屏幕中上部偏中，略低于首屏列表标题区，避免贴着高亮气泡。
        val margin = 8f * density
        val centerX = (canvas.width * 0.5f).coerceIn(
            boxWidth * 0.5f + margin,
            canvas.width - boxWidth * 0.5f - margin,
        )
        val centerY = (canvas.height * 0.48f).coerceIn(
            boxHeight * 0.5f + 12f * density,
            canvas.height * 0.56f,
        )

        val left = centerX - boxWidth / 2f
        val top = centerY - boxHeight / 2f
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(rect, boxHeight / 2f, boxHeight / 2f, pillPaint)

        val iconBitmap = CornerSlotIconBitmap.get(
            context = context,
            action = action,
            sizePx = iconSize.toInt().coerceAtLeast(16),
            tintArgb = 0xFFFFFFFF.toInt(),
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
            appRepository = appRepository,
            shape = shape,
        )
        val iconLeft = left + paddingX
        val iconTop = centerY - iconSize / 2f
        val iconScale = iconSize / iconBitmap.width
        canvas.withScale(iconScale, iconScale, iconLeft + iconSize / 2f, centerY) {
            drawBitmap(
                iconBitmap,
                iconLeft + iconSize / 2f - iconBitmap.width / 2f,
                centerY - iconBitmap.height / 2f,
                iconPaint,
            )
        }
        // 图标外圈的进度线（长按倒计时）。外扩 = 半边笔宽 + 1dp 间隙，正好贴在图标外侧。
        if (longPressRingProgress >= 0f) {
            val inflate = HintRingRenderer.STROKE_DP * density * 0.5f + 1f * density
            hintRingRect.set(
                iconLeft - inflate,
                centerY - iconSize / 2f - inflate,
                iconLeft + iconSize + inflate,
                centerY + iconSize / 2f + inflate,
            )
            HintRingRenderer.draw(
                canvas = canvas,
                icon = iconBitmap,
                bounds = hintRingRect,
                progress = longPressRingProgress,
                density = density,
            )
        }
        // 长按到时间且目标是"小窗" → 图标右下角一个画中画角标
        if (hintPipArmed && longPressRingProgress >= 1f) {
            HintRingRenderer.drawPipBadge(
                canvas = canvas,
                iconBounds = hintRingRect,
                density = density,
            )
        }
        if (action is GestureAction.LaunchShortcut) {
            when {
                action.showsShellActivityShortcutBadge(activityShortcuts) -> {
                    ShellCommandBadgeRenderer.draw(
                        canvas,
                        iconLeft + iconSize / 2f,
                        centerY,
                        iconSize,
                        progress,
                        density,
                    )
                }
                !action.isShellActivityShortcut(activityShortcuts) -> {
                    ShortcutBadgeRenderer.draw(
                        canvas,
                        iconLeft + iconSize / 2f,
                        centerY,
                        iconSize,
                        progress,
                        density,
                    )
                }
            }
        } else if (action.showsShellCommandBadge(shellCommands)) {
            ShellCommandBadgeRenderer.draw(
                canvas,
                iconLeft + iconSize / 2f,
                centerY,
                iconSize,
                progress,
                density,
            )
        } else if (action is GestureAction.RunMacro) {
            MacroBadgeRenderer.draw(
                canvas,
                iconLeft + iconSize / 2f,
                centerY,
                iconSize,
                progress,
                density,
            )
        }

        val metrics = namePaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(fitted, 0, fitted.length, iconLeft + iconSize + gap, baseline, namePaint)
    }

    private fun drawSlotShape(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        shape: FvIconShape,
        paint: Paint,
    ) {
        slotShapePath.rewind()
        slotShapeRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        QuickLauncherIconMask.outlinePathFor(shape, slotShapeRect, slotShapePath)
        canvas.drawPath(slotShapePath, paint)
    }

    private fun buildSlotShapePath(
        centerX: Float,
        centerY: Float,
        radius: Float,
        shape: FvIconShape,
        path: Path,
    ) {
        path.rewind()
        slotShapeRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        QuickLauncherIconMask.outlinePathFor(shape, slotShapeRect, path)
    }

    private fun drawFilledSlotIcon(
        canvas: Canvas,
        context: Context,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        action: GestureAction,
        density: Float,
        progress: Float,
        shadowPaint: Paint,
        strokePaint: Paint,
        iconPaint: Paint,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        appRepository: AppRepository?,
        shape: FvIconShape,
    ) {
        val diameterPx = (radius * 2f).toInt().coerceAtLeast(12)
        val bitmap = CornerSlotIconBitmap.get(
            context = context,
            action = action,
            sizePx = diameterPx,
            tintArgb = ICON_TINT,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
            appRepository = appRepository,
            shape = shape,
            plateFallback = true,
        )
        val drawSize = radius * 2f
        val left = centerX - drawSize / 2f
        val top = centerY - drawSize / 2f
        if (shape != FvIconShape.ORIGINAL) {
            slotIconClipPath.rewind()
            buildSlotShapePath(centerX, centerY, radius, shape, slotIconClipPath)
            canvas.withClip(slotIconClipPath) {
                canvas.drawBitmap(bitmap, left, top, iconPaint)
            }
        } else {
            canvas.drawBitmap(bitmap, left, top, iconPaint)
        }

        if (highlighted) {
            strokePaint.color = BUBBLE_STROKE_HIGHLIGHT
            val strokeOffset = 1.5f * density
            drawSlotShape(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = radius + strokeOffset,
                shape = shape,
                paint = strokePaint,
            )
        }

        if (action is GestureAction.LaunchShortcut) {
            when {
                action.showsShellActivityShortcutBadge(activityShortcuts) -> {
                    ShellCommandBadgeRenderer.draw(
                        canvas,
                        centerX,
                        centerY,
                        drawSize,
                        progress,
                        density,
                    )
                }
                !action.isShellActivityShortcut(activityShortcuts) -> {
                    ShortcutBadgeRenderer.draw(
                        canvas,
                        centerX,
                        centerY,
                        drawSize,
                        progress,
                        density,
                    )
                }
            }
        } else if (action.showsShellCommandBadge(shellCommands)) {
            ShellCommandBadgeRenderer.draw(
                canvas,
                centerX,
                centerY,
                drawSize,
                progress,
                density,
            )
        } else if (action is GestureAction.RunMacro) {
            MacroBadgeRenderer.draw(
                canvas,
                centerX,
                centerY,
                drawSize,
                progress,
                density,
            )
        }

        // 分身黄点画在形状裁剪之外，圆形槽位也不会被裁（烘焙版会掉一半）
        if (action.isCloneTarget(context)) {
            CloneBadge.draw(
                canvas,
                centerX,
                centerY,
                drawSize,
                progress,
                circular = shape == FvIconShape.CIRCLE,
            )
        }
    }

    private fun drawBubble(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        empty: Boolean,
        density: Float,
        progress: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
        shape: FvIconShape,
    ) {
        val shadowOffset = 3f * density * progress
        drawSlotShape(
            canvas = canvas,
            centerX = centerX + shadowOffset,
            centerY = centerY + shadowOffset,
            radius = radius,
            shape = shape,
            paint = shadowPaint,
        )
        fillPaint.color = when {
            highlighted -> BUBBLE_FILL_HIGHLIGHT
            empty -> BUBBLE_FILL_EMPTY
            else -> BUBBLE_FILL
        }
        drawSlotShape(canvas, centerX, centerY, radius, shape, fillPaint)
        strokePaint.color = when {
            highlighted -> BUBBLE_STROKE_HIGHLIGHT
            empty -> BUBBLE_STROKE_EMPTY
            else -> BUBBLE_STROKE
        }
        drawSlotShape(canvas, centerX, centerY, radius, shape, strokePaint)
    }

    private var editPencilDrawable: android.graphics.drawable.Drawable? = null

    private fun drawEditButton(
        context: Context,
        canvas: Canvas,
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val target = CornerWheelLayout.editButtonCenter(anchor, anchorX, anchorY, settings, density)
        val centerX = anchorX + (target.x - anchorX) * progress
        val centerY = anchorY + (target.y - anchorY) * progress
        val radius = CornerWheelLayout.editButtonRadius(density) * (0.85f + 0.15f * progress)

        fillPaint.color = if (highlighted) EDIT_FILL_HIGHLIGHT else EDIT_FILL
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        strokePaint.color = if (highlighted) EDIT_STROKE_HIGHLIGHT else EDIT_STROKE
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        val drawable = editPencilDrawable ?: androidx.core.content.ContextCompat.getDrawable(
            context,
            com.slideindex.app.overlay.panels.R.drawable.ic_corner_edit_pencil
        )?.mutate()?.also { editPencilDrawable = it }

        if (drawable != null) {
            val tint = if (highlighted) ICON_TINT_HIGHLIGHT else ICON_TINT
            drawable.setTint(tint)
            val iconSize = radius * 1.05f
            val left = (centerX - iconSize / 2f).toInt()
            val top = (centerY - iconSize / 2f).toInt()
            val right = (centerX + iconSize / 2f).toInt()
            val bottom = (centerY + iconSize / 2f).toInt()
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
    }

    /**
     * 动作名称：Shell 命令优先用 Shell 命令面板里用户自定义的名字，
     * 其余动作仍走 `:app` 注入的通用标签（宏、打开链接、快捷方式等）。
     */
    private fun actionLabel(
        context: Context,
        action: GestureAction,
        shellCommands: List<ShellCommand>,
    ): String {
        val customShellLabel = (action as? GestureAction.ExecuteShellCommand)
            ?.let { ShellCommandLabelResolver.customLabelFor(it.command, shellCommands) }
        return customShellLabel
            ?: com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel
                ?.invoke(context, action)
                .orEmpty()
    }
}
