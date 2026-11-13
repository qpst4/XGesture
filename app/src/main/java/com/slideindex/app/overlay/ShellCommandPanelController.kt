package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver
import com.slideindex.app.shell.ShellCommandIconStorage
import com.slideindex.app.shell.ShellResultFeedback
import com.slideindex.app.shell.ShellResultFeedbackPolicy
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.ShellResultFormatter
import com.slideindex.app.util.TaskManagerUtil
import kotlin.math.ceil

class ShellCommandPanelController(
    private val host: Host
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun isDialogShowing(): Boolean
        fun requestEndSession()
        fun showEditorDialog(
            existing: ShellCommand?,
            shizukuGranted: Boolean,
            onDismissComplete: () -> Unit,
            onSave: (ShellCommand) -> Unit,
            onDelete: (() -> Unit)?,
            onTest: (ShellCommand, (Int, String) -> Unit) -> Unit
        )
        fun showResultDialog(
            label: String,
            command: String,
            exitCode: Int,
            output: String,
            onDismissComplete: () -> Unit
        )
        fun dismissDialogs()
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun invalidate()
        fun post(action: () -> Unit)
        fun hapticTick()
        fun hapticConfirm()
        fun onPersist(commands: List<ShellCommand>)
    }

    private data class CellLayout(
        val command: ShellCommand,
        val cellRect: RectF,
        val bodyRect: RectF,
        val runRect: RectF
    )

    val panelContentRect = RectF()
    private var cellLayouts = emptyList<CellLayout>()
    private var panelRect = RectF()
    private var addButtonRect = RectF()
    private var highlightedIndex = -1
    private var executing = false
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val iconBitmapCache = mutableMapOf<String, Bitmap>()
    private val iconClipPath = Path()
    private var localCommands: List<ShellCommand> = emptyList()

    fun syncSettings(settings: AppSettings) {
        if (!host.isDialogShowing()) {
            localCommands = settings.shellCommands
        }
    }

    fun reset() {
        highlightedIndex = -1
        executing = false
        iconBitmapCache.clear()
        host.dismissDialogs()
        cellLayouts = emptyList()
    }

    fun prepareForPanelExit() {
        highlightedIndex = -1
    }

    fun hasActiveUi(): Boolean = executing || host.isDialogShowing()

    fun handleBackPress(): Boolean {
        if (host.isDialogShowing()) {
            host.dismissDialogs()
            return true
        }
        host.requestEndSession()
        return true
    }

    fun draw(canvas: Canvas, panelEnterProgress: Float) {
        rebuildLayout()
        panelContentRect.set(panelRect)
        val theme = OverlayPanelTheme.colors(host.context)
        val progress = easeOutCubic(panelEnterProgress.coerceIn(0f, 1f))
        val scrimAlpha = (Color.alpha(theme.scrimHeavy) * progress).toInt().coerceIn(0, 255)
        canvas.drawRect(
            0f,
            0f,
            host.viewWidth().toFloat(),
            host.viewHeight().toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(scrimAlpha, 0, 0, 0)
            }
        )
        val corner = host.dp(24f)
        val scale = 0.9f + 0.1f * progress
        val slideY = host.dp(28f) * (1f - progress)
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.withTranslation(panelRect.centerX(), panelRect.centerY() + slideY) {
            scale(scale, scale)
            translate(-panelRect.centerX(), -panelRect.centerY())
            val layer = saveLayerAlpha(null, alpha)
            drawPanel(this, panelRect, corner, theme)
            restoreToCount(layer)
        }
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    fun handleTouch(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        releaseImmediateLock: () -> Boolean
    ): Boolean {
        if (host.isDialogShowing()) return true
        rebuildLayout()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!panelRect.contains(localX, localY)) {
                    return true
                }
                when {
                    addButtonRect.contains(localX, localY) -> return true
                    else -> {
                        highlightedIndex = indexAt(localX, localY, runOnly = false)
                        if (highlightedIndex >= 0) host.hapticTick()
                    }
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val prev = highlightedIndex
                highlightedIndex = indexAt(localX, localY, runOnly = false)
                if (highlightedIndex != prev && highlightedIndex >= 0) host.hapticTick()
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (releaseImmediateLock()) {
                    host.invalidate()
                    return true
                }
                when {
                    addButtonRect.contains(localX, localY) -> openEditor(null)
                    else -> {
                        val runIndex = indexAt(localX, localY, runOnly = true)
                        if (runIndex >= 0) {
                            executeCommand(localCommands[runIndex])
                        } else {
                            val editIndex = indexAt(localX, localY, runOnly = false)
                            if (editIndex >= 0) {
                                openEditor(localCommands[editIndex])
                            } else if (!panelRect.contains(localX, localY)) {
                                host.requestEndSession()
                            }
                        }
                    }
                }
                highlightedIndex = -1
                host.invalidate()
                return true
            }
        }
        return false
    }

    private fun executeCommand(command: ShellCommand) {
        if (executing) return
        executing = true
        host.hapticConfirm()
        Thread {
            val outcome = ShellCommandRunner.execute(host.context, command)
            host.post {
                executing = false
                // 这条面板是挂在无障碍层的全屏浮窗，系统 Toast 会被它自己盖住，
                // 所以 TOAST 模式走 OverlayToastWindow（拿不到悬浮窗时它内部会退回系统 Toast）。
                when (ShellResultFeedbackPolicy.forResult(command.resultDisplay, outcome.exitCode)) {
                    ShellResultFeedback.DIALOG -> host.showResultDialog(
                        label = command.label,
                        command = outcome.expandedCommand,
                        exitCode = outcome.exitCode,
                        output = outcome.output,
                        onDismissComplete = { host.invalidate() }
                    )
                    ShellResultFeedback.TOAST -> OverlayToastWindow.show(
                        host.context,
                        ShellResultFormatter.toast(host.context, outcome.exitCode, outcome.output)
                    )
                    ShellResultFeedback.SILENT -> Unit
                }
            }
        }.start()
    }

    private fun openEditor(existing: ShellCommand?) {
        host.showEditorDialog(
            existing = existing,
            shizukuGranted = TaskManagerUtil.hasPermission(),
            onDismissComplete = { host.invalidate() },
            onSave = { saved ->
                localCommands = if (existing == null) {
                    localCommands + saved
                } else {
                    localCommands.map { if (it.id == saved.id) saved else it }
                }
                host.onPersist(localCommands)
                host.invalidate()
            },
            onDelete = existing?.let { target ->
                {
                    ShellCommandIconStorage.deleteIconIfOwned(host.context, target.iconPath)
                    localCommands = localCommands.filterNot { it.id == target.id }
                    host.onPersist(localCommands)
                    host.invalidate()
                }
            },
            onTest = { command, callback ->
                Thread {
                    val outcome = ShellCommandRunner.execute(host.context, command)
                    host.post { callback(outcome.exitCode, outcome.output) }
                }.start()
            }
        )
    }

    private fun indexAt(x: Float, y: Float, runOnly: Boolean): Int {
        cellLayouts.forEachIndexed { index, layout ->
            val rect = if (runOnly) layout.runRect else layout.bodyRect
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    private fun rebuildLayout() {
        val commands = localCommands
        val cols = COLUMNS
        val rows = if (commands.isEmpty()) 1 else ceil(commands.size / cols.toFloat()).toInt()
        val headerH = host.dp(56f)
        val footerH = host.dp(44f)
        val cellH = commandCellHeight()
        val rowGap = host.dp(8f)
        val gridTopPad = host.dp(8f)
        val gridH = rows * cellH + (rows - 1).coerceAtLeast(0) * rowGap
        val panelH = headerH + gridTopPad + gridH + footerH
        val panelW = host.dp(320f).coerceAtMost(host.viewWidth() - host.dp(32f))
        val left = (host.viewWidth() - panelW) / 2f
        val top = ((host.viewHeight() - panelH) / 2f).coerceAtLeast(host.dp(24f))
        panelRect = RectF(left, top, left + panelW, top + panelH)

        addButtonRect = RectF(
            panelRect.left + host.dp(16f),
            panelRect.bottom - host.dp(40f),
            panelRect.right - host.dp(16f),
            panelRect.bottom - host.dp(10f)
        )

        val gridTop = panelRect.top + headerH + gridTopPad
        val colGap = host.dp(8f)
        val runW = host.dp(36f)
        val usableW = panelRect.width() - host.dp(16f) * 2
        val cellW = (usableW - colGap * (cols - 1)) / cols
        cellLayouts = commands.mapIndexed { index, command ->
            val row = index / cols
            val col = index % cols
            val cellLeft = panelRect.left + host.dp(16f) + col * (cellW + colGap)
            val cellTop = gridTop + row * (cellH + rowGap)
            val cellRect = RectF(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH)
            CellLayout(
                command = command,
                cellRect = cellRect,
                bodyRect = RectF(cellLeft, cellTop, cellLeft + cellW - runW, cellTop + cellH),
                runRect = RectF(cellRect.right - runW, cellTop, cellRect.right, cellTop + cellH)
            )
        }
    }

    private fun commandCellHeight(): Float {
        val cmdLineHeight = host.sp(10.5f) + host.dp(2f)
        return host.dp(12f) + host.sp(13f) + host.dp(4f) +
            cmdLineHeight * COMMAND_PREVIEW_LINES + host.dp(10f)
    }

    private fun drawPanel(canvas: Canvas, panelRect: RectF, corner: Float, theme: OverlayPanelTheme.Colors) {
        drawElevatedRoundRect(canvas, panelRect, corner, theme.panelBackground, theme.panelShadow)
        drawHeader(canvas, panelRect, theme)
        val gridTop = panelRect.top + host.dp(56f) + host.dp(8f)
        if (localCommands.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textMuted
                textSize = host.sp(12.5f)
            }
            canvas.drawText(
                host.context.getString(R.string.shell_panel_overlay_empty),
                panelRect.left + host.dp(16f),
                gridTop + host.dp(24f) - hintPaint.ascent(),
                hintPaint
            )
        } else {
            cellLayouts.forEachIndexed { index, layout ->
                drawCell(canvas, layout, index == highlightedIndex, theme)
            }
        }
        drawAddButton(canvas, theme)
    }

    private fun drawHeader(canvas: Canvas, panelRect: RectF, theme: OverlayPanelTheme.Colors) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleAccent
            textSize = host.sp(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textSize = host.sp(15f)
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            host.context.getString(PrivilegeUiStrings.shellPanelLabelRes()),
            panelRect.left + host.dp(16f),
            panelRect.top + host.dp(22f) - titlePaint.ascent(),
            titlePaint
        )
        val privileged = TaskManagerUtil.hasPermission()
        val status = if (privileged) {
            host.context.getString(PrivilegeUiStrings.shellPanelActiveRes())
        } else {
            host.context.getString(PrivilegeUiStrings.shellPanelInactiveRes())
        }
        canvas.drawText(
            status,
            panelRect.left + host.dp(16f),
            panelRect.top + host.dp(40f) - statusPaint.ascent(),
            statusPaint
        )
    }

    private fun drawAddButton(canvas: Canvas, theme: OverlayPanelTheme.Colors) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            textSize = host.sp(13f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentSoft }
        canvas.drawRoundRect(addButtonRect, host.dp(12f), host.dp(12f), bg)
        canvas.drawText(
            host.context.getString(R.string.shell_panel_add),
            addButtonRect.centerX(),
            addButtonRect.centerY() - (paint.descent() + paint.ascent()) / 2f,
            paint
        )
    }

    private fun drawCell(canvas: Canvas, layout: CellLayout, highlighted: Boolean, theme: OverlayPanelTheme.Colors) {
        val cell = layout.cellRect
        val inner = RectF(
            cell.left + host.dp(2f),
            cell.top + host.dp(2f),
            cell.right - host.dp(2f),
            cell.bottom - host.dp(2f)
        )
        val corner = host.dp(14f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.panelShadow }
        canvas.drawRoundRect(
            RectF(inner.left, inner.top + host.dp(1.5f), inner.right, inner.bottom + host.dp(1.5f)),
            corner,
            corner,
            shadow
        )
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.cardBackground }
        canvas.drawRoundRect(inner, corner, corner, cardBg)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.cardBorder
            style = Paint.Style.STROKE
            strokeWidth = host.dp(1f)
        }
        canvas.drawRoundRect(inner, corner, corner, border)
        if (highlighted) {
            val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.cardHighlight }
            canvas.drawRoundRect(inner, corner, corner, highlight)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleAccent
            textSize = host.sp(13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val iconSize = host.dp(28f)
        val contentLeft = cell.left + host.dp(10f)
        val labelLeft = if (layout.command.hasCustomIcon()) {
            val iconLeft = contentLeft
            val iconTop = cell.top + host.dp(10f)
            resolveCellIcon(layout.command, iconSize.toInt())?.let { bitmap ->
                val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                val iconCorner = iconSize * 0.24f
                canvas.withSave {
                    iconClipPath.reset()
                    iconClipPath.addRoundRect(iconRect, iconCorner, iconCorner, Path.Direction.CW)
                    clipPath(iconClipPath)
                    drawBitmap(bitmap, null, iconRect, iconBitmapPaint)
                }
            }
            contentLeft + iconSize + host.dp(8f)
        } else {
            contentLeft
        }
        val cmdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textSecondary
            textSize = host.sp(10.5f)
        }
        val bodyRight = layout.runRect.left - host.dp(6f)
        canvas.drawText(
            ellipsize(layout.command.label, bodyRight - labelLeft, labelPaint),
            labelLeft,
            cell.top + host.dp(16f) - labelPaint.ascent(),
            labelPaint
        )
        val cmdMaxWidth = bodyRight - labelLeft
        val cmdLineHeight = cmdPaint.textSize + host.dp(2f)
        val cmdLines = fitTextLines(layout.command.command, cmdMaxWidth, cmdPaint, COMMAND_PREVIEW_LINES)
        var cmdY = cell.top + host.dp(34f) - cmdPaint.ascent()
        cmdLines.forEach { line ->
            canvas.drawText(line, labelLeft, cmdY, cmdPaint)
            cmdY += cmdLineHeight
        }
        val runBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentSoft }
        val runInner = RectF(
            layout.runRect.left + host.dp(4f),
            layout.runRect.top + host.dp(6f),
            layout.runRect.right - host.dp(4f),
            layout.runRect.bottom - host.dp(6f)
        )
        canvas.drawRoundRect(runInner, host.dp(10f), host.dp(10f), runBg)
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            textSize = host.sp(18f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "›",
            layout.runRect.centerX(),
            layout.runRect.centerY() - (arrowPaint.descent() + arrowPaint.ascent()) / 2f,
            arrowPaint
        )
    }

    private fun resolveCellIcon(command: ShellCommand, sizePx: Int): Bitmap? {
        if (!command.hasCustomIcon()) return null
        val cacheKey = "${command.id}:${command.iconType}:${command.iconPath.orEmpty()}:${command.textIcon.orEmpty()}:$sizePx"
        iconBitmapCache[cacheKey]?.let { return it }
        val bitmap = ShellCommandIconResolver.resolveBitmap(host.context, command, sizePx) ?: return null
        iconBitmapCache[cacheKey] = bitmap
        return bitmap
    }

    private fun drawElevatedRoundRect(
        canvas: Canvas,
        rect: RectF,
        corner: Float,
        color: Int,
        shadowColor: Int
    ) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = shadowColor }
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + host.dp(2f), rect.right, rect.bottom + host.dp(2f)),
            corner,
            corner,
            shadow
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(rect, corner, corner, fill)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun fitTextLines(text: String, maxWidth: Float, paint: Paint, maxLines: Int): List<String> {
        val normalized = text.replace('\n', ' ').trim()
        if (normalized.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var index = 0
        while (index < normalized.length && lines.size < maxLines) {
            val isLastAllowedLine = lines.size == maxLines - 1
            var bestEnd = index
            var probe = index + 1
            while (probe <= normalized.length) {
                val segment = normalized.substring(index, probe)
                if (paint.measureText(segment) > maxWidth) break
                bestEnd = probe
                probe++
            }
            if (bestEnd == index) {
                bestEnd = (index + 1).coerceAtMost(normalized.length)
            } else if (!isLastAllowedLine && bestEnd < normalized.length) {
                val chunk = normalized.substring(index, bestEnd)
                val lastSpace = chunk.lastIndexOf(' ')
                if (lastSpace > 0) bestEnd = index + lastSpace
            }
            val line = if (isLastAllowedLine && bestEnd < normalized.length) {
                ellipsize(normalized.substring(index), maxWidth, paint)
            } else {
                normalized.substring(index, bestEnd).trimEnd()
            }
            if (line.isNotEmpty()) lines += line
            index = bestEnd
            while (index < normalized.length && normalized[index] == ' ') index++
            if (isLastAllowedLine && bestEnd < normalized.length) break
        }
        return lines.ifEmpty { listOf("") }
    }

    companion object {
        private const val COLUMNS = 2
        private const val COMMAND_PREVIEW_LINES = 2
    }

    internal fun collectAccessibilityNodes(context: Context): List<OverlayVirtualNode> {
        val nodes = mutableListOf<OverlayVirtualNode>()
        cellLayouts.forEach { layout ->
            val label = layout.command.label.ifBlank { layout.command.command }
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.cd_overlay_shell_command_run, label),
                boundsInParent = RectF(layout.cellRect)
            )
        }
        if (!addButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.shell_panel_add),
                boundsInParent = RectF(addButtonRect)
            )
        }
        return nodes
    }
}
