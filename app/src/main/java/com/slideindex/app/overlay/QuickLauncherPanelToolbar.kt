package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.core.graphics.toColorInt
import com.slideindex.app.R

internal class QuickLauncherPanelToolbar(
    private val controller: QuickLauncherPanelController,
    private val host: QuickLauncherPanelController.Host
) {
    enum class ToolbarAction { ADD, SWITCH, EDIT, CLOSE, PIN }

    data class ToolbarLayoutMetrics(
        val toolbarHeight: Float,
        val toolbarPanelGap: Float,
        val edgeInset: Float,
        val buttonHeight: Float,
        val buttonGap: Float
    )

    private val addButtonRect = RectF()
    private val switchButtonRect = RectF()
    private val editButtonRect = RectF()
    private val closeButtonRect = RectF()
    private val pinButtonRect = RectF()
    private val toolbarRect = RectF()
    private val deleteBadgeRects = mutableListOf<RectF>()

    private var cachedPanelLeft = 0f
    private var cachedPanelTop = 0f
    private var cachedPanelWidth = -1f
    private var cachedPanelHeight = -1f
    private var cachedPanelName = ""
    private var cachedEditMode = false
    private var cachedPinned = false
    private var cachedHasMultiplePanels = false
    private var cachedDisplayLabel = ""

    fun invalidateLayoutCache() {
        cachedPanelWidth = -1f
    }

    private var capsuleDownX = 0f
    private var capsuleDownY = 0f
    var isDraggingPanel = false
        private set
    private var capsuleLongPressConsumed = false
    private var capsuleLongPressRunnable: Runnable? = null

    var panelMenuShowing: Boolean = false
        private set
    private val panelMenuRect = RectF()
    private val menuItemRects = mutableListOf<Pair<String, RectF>>()
    private var pressedMenuPanelId: String? = null

    var armedToolbarAction: ToolbarAction? = null
        private set

    private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val toolbarButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val menuBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val menuBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val menuTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val deleteBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#EA4335".toColorInt()
    }
    private val deleteBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val deleteBadgeMinusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun reset() {
        armedToolbarAction = null
        deleteBadgeRects.clear()
        dismissPanelMenu()
        cancelCapsuleLongPress()
        isDraggingPanel = false
        capsuleLongPressConsumed = false
        invalidateLayoutCache()
    }

    fun onEditModeDisabled() {
        deleteBadgeRects.clear()
        dismissPanelMenu()
        cancelCapsuleLongPress()
        isDraggingPanel = false
        capsuleLongPressConsumed = false
        invalidateLayoutCache()
    }

    fun isCapsuleTouch(localX: Float, localY: Float): Boolean {
        val slop = host.dp(4f)
        val r = RectF(switchButtonRect.left - slop, switchButtonRect.top - slop, switchButtonRect.right + slop, switchButtonRect.bottom + slop)
        return r.contains(localX, localY)
    }

    fun onCapsuleDown(localX: Float, localY: Float) {
        capsuleDownX = localX
        capsuleDownY = localY
        isDraggingPanel = false
        capsuleLongPressConsumed = false
        cancelCapsuleLongPress()
        val runnable = Runnable {
            if (!isDraggingPanel && !capsuleLongPressConsumed) {
                capsuleLongPressConsumed = true
                controller.switchToNextPanel()
                host.hapticTick()
                host.invalidate()
            }
        }
        capsuleLongPressRunnable = runnable
        host.postDelayed(runnable, 320L)
    }

    fun onCapsuleMove(localX: Float, localY: Float): Boolean {
        val dy = localY - capsuleDownY
        val dx = localX - capsuleDownX
        val slop = host.dp(6f)
        if (!isDraggingPanel && (dx * dx + dy * dy > slop * slop)) {
            cancelCapsuleLongPress()
            isDraggingPanel = true
        }
        if (isDraggingPanel) {
            controller.movePanelAnchor(dx, dy)
            capsuleDownY = localY
            capsuleDownX = localX
            return true
        }
        return false
    }

    fun onCapsuleUp(): Boolean {
        cancelCapsuleLongPress()
        val wasDragging = isDraggingPanel
        val wasConsumed = capsuleLongPressConsumed
        isDraggingPanel = false
        capsuleLongPressConsumed = false
        if (wasDragging || wasConsumed) {
            return true
        }
        if (host.hasMultiplePanels()) {
            togglePanelMenu()
            host.hapticTick()
        }
        return true
    }

    private fun cancelCapsuleLongPress() {
        capsuleLongPressRunnable?.let { host.removeCallbacks(it) }
        capsuleLongPressRunnable = null
    }

    fun togglePanelMenu() {
        panelMenuShowing = !panelMenuShowing
        if (!panelMenuShowing) {
            menuItemRects.clear()
            pressedMenuPanelId = null
        }
        host.invalidate()
    }

    fun dismissPanelMenu() {
        if (panelMenuShowing) {
            panelMenuShowing = false
            menuItemRects.clear()
            pressedMenuPanelId = null
            host.invalidate()
        }
    }

    fun handleMenuTouch(localX: Float, localY: Float, actionMasked: Int): Boolean {
        if (!panelMenuShowing) return false
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!panelMenuRect.contains(localX, localY)) {
                    dismissPanelMenu()
                    return true
                }
                pressedMenuPanelId = menuItemRects.firstOrNull { it.second.contains(localX, localY) }?.first
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val hovered = menuItemRects.firstOrNull { it.second.contains(localX, localY) }?.first
                if (hovered != pressedMenuPanelId) {
                    pressedMenuPanelId = hovered
                    host.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val target = menuItemRects.firstOrNull { it.second.contains(localX, localY) }
                if (target != null) {
                    controller.switchToPanel(target.first)
                    host.hapticTick()
                }
                dismissPanelMenu()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismissPanelMenu()
                return true
            }
        }
        return false
    }

    /**
     * 底部操作栏是否显示。
     *
     * 编辑态**强制显示**：那条栏里的「✓」是从编辑态退出来的唯一入口，设置里关掉操作栏之后
     * 如果编辑态也不画，用户会卡在编辑态里出不来（Back 键虽然也能退，但面板内就没有可见出口了）。
     */
    fun shouldShowToolbar(): Boolean =
        host.isPanelReady() &&
            controller.displayItems().isNotEmpty() &&
            !host.isAddDialogShowing() &&
            (controller.editMode || (host.settings().quickLauncherDisplay.showToolbar && host.hasMultiplePanels()))

    fun toolbarLayoutMetrics(): ToolbarLayoutMetrics = ToolbarLayoutMetrics(
        toolbarHeight = host.dp(44f),
        toolbarPanelGap = 0f,
        edgeInset = host.dp(8f),
        buttonHeight = host.dp(28f),
        buttonGap = host.dp(6f)
    )

    fun contentReserveWidth(): Float = 0f

    fun toolbarBounds(): RectF = RectF(toolbarRect)

    fun toolbarContains(localX: Float, localY: Float): Boolean {
        if (panelMenuShowing && panelMenuRect.contains(localX, localY)) return true
        if (controller.editMode) {
            return (!addButtonRect.isEmpty && addButtonRect.contains(localX, localY)) ||
                (!switchButtonRect.isEmpty && switchButtonRect.contains(localX, localY)) ||
                (!editButtonRect.isEmpty && editButtonRect.contains(localX, localY)) ||
                (!closeButtonRect.isEmpty && closeButtonRect.contains(localX, localY))
        } else {
            return (!switchButtonRect.isEmpty && switchButtonRect.contains(localX, localY)) ||
                (!pinButtonRect.isEmpty && pinButtonRect.contains(localX, localY)) ||
                (!closeButtonRect.isEmpty && closeButtonRect.contains(localX, localY))
        }
    }

    fun combinedContentRect(panelRect: RectF): RectF {
        layoutToolbar(panelRect)
        return RectF(panelRect)
    }

    fun layoutToolbar(panelRect: RectF) {
        if (!shouldShowToolbar()) {
            toolbarRect.setEmpty()
            addButtonRect.setEmpty()
            switchButtonRect.setEmpty()
            editButtonRect.setEmpty()
            closeButtonRect.setEmpty()
            pinButtonRect.setEmpty()
            cachedPanelWidth = -1f
            return
        }
        val panelWidth = panelRect.width()
        val panelHeight = panelRect.height()
        val currentName = host.currentPanelName()
        val currentEditMode = controller.editMode
        val currentPinned = controller.pinned
        val currentMultiple = host.hasMultiplePanels()

        val isLayoutValid = cachedPanelWidth == panelWidth &&
            cachedPanelHeight == panelHeight &&
            cachedPanelName == currentName &&
            cachedEditMode == currentEditMode &&
            cachedPinned == currentPinned &&
            cachedHasMultiplePanels == currentMultiple

        if (isLayoutValid) {
            val dx = panelRect.left - cachedPanelLeft
            val dy = panelRect.top - cachedPanelTop
            if (dx != 0f || dy != 0f) {
                toolbarRect.offset(dx, dy)
                addButtonRect.offset(dx, dy)
                switchButtonRect.offset(dx, dy)
                editButtonRect.offset(dx, dy)
                closeButtonRect.offset(dx, dy)
                pinButtonRect.offset(dx, dy)
                cachedPanelLeft = panelRect.left
                cachedPanelTop = panelRect.top
            }
            return
        }

        cachedPanelLeft = panelRect.left
        cachedPanelTop = panelRect.top
        cachedPanelWidth = panelWidth
        cachedPanelHeight = panelHeight
        cachedPanelName = currentName
        cachedEditMode = currentEditMode
        cachedPinned = currentPinned
        cachedHasMultiplePanels = currentMultiple

        val headerHeight = host.dp(44f)
        val buttonHeight = host.dp(28f)
        val buttonTop = panelRect.top + (headerHeight - buttonHeight) / 2f
        val buttonBottom = buttonTop + buttonHeight
        val sidePadding = host.dp(12f)
        toolbarRect.set(panelRect.left, panelRect.top, panelRect.right, panelRect.top + headerHeight)

        val pinned = currentPinned
        val btnSize = host.dp(28f)
        val gap = host.dp(8f)

        if (currentEditMode) {
            pinButtonRect.setEmpty()
            addButtonRect.set(panelRect.left + sidePadding, buttonTop, panelRect.left + sidePadding + btnSize, buttonBottom)

            var rightCursor = panelRect.right - sidePadding
            if (pinned) {
                closeButtonRect.set(rightCursor - btnSize, buttonTop, rightCursor, buttonBottom)
                rightCursor -= (btnSize + gap)
            } else {
                closeButtonRect.setEmpty()
            }
            editButtonRect.set(rightCursor - btnSize, buttonTop, rightCursor, buttonBottom)
            rightCursor -= (btnSize + gap)

            val leftLimit = addButtonRect.right + gap
            val rightLimit = rightCursor
            val maxAllowedW = (rightLimit - leftLimit).coerceAtLeast(0f)

            if (maxAllowedW >= host.dp(40f)) {
                toolbarIconPaint.textSize = host.sp(12.5f)
                val name = currentName
                val label = name
                val textW = toolbarIconPaint.measureText(label)
                val desiredCapsuleW = (textW + host.dp(22f)).coerceIn(host.dp(60f), host.dp(150f))
                val capsuleW = minOf(desiredCapsuleW, maxAllowedW)
                val cx = panelRect.centerX()
                val left = (cx - capsuleW / 2f).coerceIn(leftLimit, rightLimit - capsuleW)
                switchButtonRect.set(left, buttonTop, left + capsuleW, buttonBottom)

                val maxTextWidth = capsuleW - host.dp(12f)
                cachedDisplayLabel = if (textW <= maxTextWidth) {
                    label
                } else {
                    var end = name.length
                    while (end > 1 && toolbarIconPaint.measureText(name.substring(0, end) + "…") > maxTextWidth) end--
                    name.substring(0, end.coerceAtLeast(1)) + "…"
                }
            } else {
                switchButtonRect.setEmpty()
                cachedDisplayLabel = ""
            }
        } else {
            addButtonRect.setEmpty()
            editButtonRect.setEmpty()

            pinButtonRect.set(panelRect.left + sidePadding, buttonTop, panelRect.left + sidePadding + btnSize, buttonBottom)

            var rightCursor = panelRect.right - sidePadding
            if (pinned) {
                closeButtonRect.set(rightCursor - btnSize, buttonTop, rightCursor, buttonBottom)
                rightCursor -= (btnSize + gap)
            } else {
                closeButtonRect.setEmpty()
            }

            if (currentMultiple) {
                toolbarIconPaint.textSize = host.sp(12.5f)
                val name = currentName
                val label = "$name ▾"
                val textW = toolbarIconPaint.measureText(label)
                val desiredCapsuleW = (textW + host.dp(22f)).coerceIn(host.dp(64f), host.dp(160f))
                val maxAllowedW = panelRect.width() - (sidePadding + btnSize + gap) * 2f
                val capsuleW = minOf(desiredCapsuleW, maxAllowedW)
                val cx = panelRect.centerX()
                switchButtonRect.set(cx - capsuleW / 2f, buttonTop, cx + capsuleW / 2f, buttonBottom)

                val maxTextWidth = capsuleW - host.dp(12f)
                cachedDisplayLabel = if (textW <= maxTextWidth) {
                    label
                } else {
                    var end = name.length
                    val suffix = "… ▾"
                    while (end > 1 && toolbarIconPaint.measureText(name.substring(0, end) + suffix) > maxTextWidth) end--
                    name.substring(0, end.coerceAtLeast(1)) + suffix
                }
            } else {
                switchButtonRect.setEmpty()
                cachedDisplayLabel = ""
            }
        }
    }

    fun drawToolbar(canvas: Canvas, panelRect: RectF) {
        layoutToolbar(panelRect)
        if (toolbarRect.isEmpty) return

        val theme = OverlayPanelTheme.colors(host.context)
        val isDark = OverlayPanelTheme.isDarkTheme(host.context)

        if (controller.editMode) {
            drawToolbarButton(canvas, addButtonRect, ToolbarAction.ADD, theme.accent, active = false, isDark = isDark)
            if (!switchButtonRect.isEmpty) {
                drawToolbarButton(canvas, switchButtonRect, ToolbarAction.SWITCH, theme.accent, active = false, isDark = isDark)
            }
            drawToolbarButton(canvas, editButtonRect, ToolbarAction.EDIT, theme.accent, active = true, isDark = isDark)
            if (controller.pinned && !closeButtonRect.isEmpty) {
                drawToolbarButton(canvas, closeButtonRect, ToolbarAction.CLOSE, Color.argb(200, 160, 160, 160), active = false, isDark = isDark)
            }
        } else {
            if (!pinButtonRect.isEmpty) {
                drawToolbarButton(canvas, pinButtonRect, ToolbarAction.PIN, theme.accent, active = controller.pinned, isDark = isDark)
            }
            if (!switchButtonRect.isEmpty) {
                drawToolbarButton(canvas, switchButtonRect, ToolbarAction.SWITCH, theme.accent, active = false, isDark = isDark)
            }
            if (controller.pinned && !closeButtonRect.isEmpty) {
                drawToolbarButton(canvas, closeButtonRect, ToolbarAction.CLOSE, Color.argb(200, 160, 160, 160), active = false, isDark = isDark)
            }
        }

        if (panelMenuShowing) {
            drawPanelMenu(canvas, panelRect, isDark, theme.accent)
        }
    }

    fun layoutDeleteBadges(cells: List<RectF>, dragFromIndex: Int = -1) {
        deleteBadgeRects.clear()
        if (!controller.editMode) return
        val radius = host.dp(8f)
        cells.forEachIndexed { index, cell ->
            if (index == dragFromIndex && dragFromIndex >= 0) return@forEachIndexed
            deleteBadgeRects += RectF(
                cell.left + host.dp(2f),
                cell.top + host.dp(2f),
                cell.left + host.dp(2f) + radius * 2f,
                cell.top + host.dp(2f) + radius * 2f
            )
        }
    }

    fun drawDeleteBadges(canvas: Canvas) {
        if (!controller.editMode) return
        deleteBadgeBorderPaint.strokeWidth = host.dp(1f)
        deleteBadgeMinusPaint.strokeWidth = host.dp(1.8f)
        val minusHalfLen = host.dp(3.5f)
        deleteBadgeRects.forEach { badge ->
            val cx = badge.centerX()
            val cy = badge.centerY()
            val r = badge.width() / 2f
            canvas.drawCircle(cx, cy, r, deleteBadgePaint)
            canvas.drawCircle(cx, cy, r - host.dp(0.5f), deleteBadgeBorderPaint)
            canvas.drawLine(cx - minusHalfLen, cy, cx + minusHalfLen, cy, deleteBadgeMinusPaint)
        }
    }

    fun resolveToolbarAction(localX: Float, localY: Float, panelRect: RectF): ToolbarAction? {
        if (!shouldShowToolbar()) return null
        layoutToolbar(panelRect)
        return toolbarActionAt(localX, localY)
    }

    fun commitToolbarAtRelease(
        localX: Float,
        localY: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
        allowSlideRelease: Boolean = false
    ): Boolean {
        if (!toolbarCommitAllowed || !shouldShowToolbar()) return false
        layoutToolbar(panelRect)
        val action = when {
            allowSlideRelease -> resolveToolbarAction(localX, localY, panelRect)
            tapGesture -> armedToolbarAction
            else -> null
        } ?: return false
        when (action) {
            ToolbarAction.ADD -> {
                controller.openAddDialog()
                host.hapticTick()
            }
            ToolbarAction.SWITCH -> {
                if (host.hasMultiplePanels()) {
                    togglePanelMenu()
                    host.hapticTick()
                } else {
                    controller.switchToNextPanel()
                }
            }
            ToolbarAction.EDIT -> {
                controller.setEditMode(!controller.editMode)
                host.hapticTick()
            }
            ToolbarAction.CLOSE -> {
                controller.onCloseRequested?.invoke()
                host.hapticTick()
            }
            ToolbarAction.PIN -> {
                controller.togglePinMode()
            }
        }
        armedToolbarAction = null
        return true
    }

    fun setArmedToolbarAction(action: ToolbarAction?) {
        armedToolbarAction = action
    }

    fun deleteBadgeIndexAt(localX: Float, localY: Float): Int {
        deleteBadgeRects.forEachIndexed { index, rect ->
            if (rect.contains(localX, localY)) return index
        }
        return -1
    }

    private fun drawToolbarButton(
        canvas: Canvas,
        rect: RectF,
        action: ToolbarAction,
        accentColor: Int,
        active: Boolean,
        isDark: Boolean,
    ) {
        if (rect.isEmpty) return
        val buttonCorner = rect.height() / 2f
        toolbarButtonPaint.color = when {
            active -> Color.argb(140, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            isDark -> Color.argb(40, 255, 255, 255)
            else -> Color.argb(22, 0, 0, 0)
        }
        canvas.drawRoundRect(rect, buttonCorner, buttonCorner, toolbarButtonPaint)

        toolbarBorderPaint.color = when {
            active -> Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            isDark -> Color.argb(35, 255, 255, 255)
            else -> Color.argb(16, 0, 0, 0)
        }
        toolbarBorderPaint.strokeWidth = host.dp(0.75f)
        canvas.drawRoundRect(rect, buttonCorner, buttonCorner, toolbarBorderPaint)

        val textColor = when {
            active -> Color.WHITE
            action == ToolbarAction.ADD -> if (isDark) Color.WHITE else Color.argb(230, 28, 28, 30)
            isDark -> Color.WHITE
            else -> Color.argb(230, 28, 28, 30)
        }
        toolbarIconPaint.color = textColor

        when (action) {
            ToolbarAction.ADD -> {
                toolbarIconPaint.textSize = host.sp(15f)
                toolbarIconPaint.isFakeBoldText = true
                canvas.drawText(
                    "+",
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint
                )
            }
            ToolbarAction.SWITCH -> {
                toolbarIconPaint.textSize = host.sp(12.5f)
                toolbarIconPaint.isFakeBoldText = false
                canvas.drawText(
                    cachedDisplayLabel,
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint
                )
            }
            ToolbarAction.EDIT -> {
                toolbarIconPaint.textSize = host.sp(13.5f)
                toolbarIconPaint.isFakeBoldText = true
                canvas.drawText(
                    "✓",
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint
                )
            }
            ToolbarAction.CLOSE -> {
                toolbarIconPaint.textSize = host.sp(15f)
                toolbarIconPaint.isFakeBoldText = false
                canvas.drawText(
                    "×",
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint
                )
            }
            ToolbarAction.PIN -> {
                drawPinIcon(canvas, rect.centerX(), rect.centerY(), host.dp(13f), controller.pinned, textColor)
            }
        }
    }

    private fun toolbarActionAt(localX: Float, localY: Float): ToolbarAction? {
        val slop = host.dp(4f)
        if (!editButtonRect.isEmpty) {
            val r = RectF(editButtonRect.left - slop, editButtonRect.top - slop, editButtonRect.right + slop, editButtonRect.bottom + slop)
            if (r.contains(localX, localY)) return ToolbarAction.EDIT
        }
        if (!addButtonRect.isEmpty) {
            val r = RectF(addButtonRect.left - slop, addButtonRect.top - slop, addButtonRect.right + slop, addButtonRect.bottom + slop)
            if (r.contains(localX, localY)) return ToolbarAction.ADD
        }
        if (!closeButtonRect.isEmpty) {
            val r = RectF(closeButtonRect.left - slop, closeButtonRect.top - slop, closeButtonRect.right + slop, closeButtonRect.bottom + slop)
            if (r.contains(localX, localY)) return ToolbarAction.CLOSE
        }
        if (!pinButtonRect.isEmpty) {
            val r = RectF(pinButtonRect.left - slop, pinButtonRect.top - slop, pinButtonRect.right + slop, pinButtonRect.bottom + slop)
            if (r.contains(localX, localY)) return ToolbarAction.PIN
        }
        if (!switchButtonRect.isEmpty) {
            val r = RectF(switchButtonRect.left - slop, switchButtonRect.top - slop, switchButtonRect.right + slop, switchButtonRect.bottom + slop)
            if (r.contains(localX, localY)) return ToolbarAction.SWITCH
        }
        return null
    }

    private fun drawPinIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, isPinned: Boolean, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = host.dp(1.3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val r = size / 2f
        canvas.save()
        val opticalOffsetY = host.dp(1.8f)
        canvas.translate(cx, cy - opticalOffsetY)
        canvas.rotate(if (isPinned) 0f else -35f)
        val path = android.graphics.Path()
        // 针尖
        path.moveTo(0f, r)
        path.lineTo(0f, r * 0.35f)
        // 钉身横梁
        path.moveTo(-r * 0.45f, r * 0.35f)
        path.lineTo(r * 0.45f, r * 0.35f)
        // 钉身侧边
        path.lineTo(r * 0.25f, -r * 0.35f)
        path.lineTo(-r * 0.25f, -r * 0.35f)
        path.close()
        // 钉帽横梁
        path.moveTo(-r * 0.45f, -r * 0.35f)
        path.lineTo(r * 0.45f, -r * 0.35f)

        if (isPinned) {
            paint.style = Paint.Style.FILL_AND_STROKE
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawPanelMenu(canvas: Canvas, panelRect: RectF, isDark: Boolean, accentColor: Int) {
        val panels = controller.allPanels()
        if (panels.size <= 1) {
            dismissPanelMenu()
            return
        }
        val itemHeight = host.dp(38f)
        val corner = host.dp(14f)
        menuTextPaint.textSize = host.sp(13f)
        val panelNames = panels.mapIndexed { index, p ->
            p.name.ifBlank {
                host.context.getString(R.string.quick_launcher_panel_default_name, index + 1)
            }
        }
        val maxTextW = panelNames.maxOfOrNull { menuTextPaint.measureText(it) } ?: 0f
        val menuWidth = (maxTextW + host.dp(56f)).coerceIn(host.dp(120f), host.dp(200f))
        val menuHeight = panels.size * itemHeight + host.dp(12f)

        val top = switchButtonRect.bottom + host.dp(6f)
        val left = (switchButtonRect.centerX() - menuWidth / 2f)
            .coerceIn(panelRect.left + host.dp(10f), panelRect.right - host.dp(10f) - menuWidth)
        panelMenuRect.set(left, top, left + menuWidth, top + menuHeight)

        // 绘制阴影/毛玻璃底板
        menuBgPaint.color = if (isDark) Color.argb(242, 38, 38, 40) else Color.argb(246, 252, 252, 254)
        canvas.drawRoundRect(panelMenuRect, corner, corner, menuBgPaint)

        menuBorderPaint.color = if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(22, 0, 0, 0)
        menuBorderPaint.strokeWidth = host.dp(0.75f)
        canvas.drawRoundRect(panelMenuRect, corner, corner, menuBorderPaint)

        menuItemRects.clear()
        val currentPanelId = host.activeQuickLauncherPanel().id

        panels.forEachIndexed { i, panel ->
            val rowTop = top + host.dp(6f) + i * itemHeight
            val rowBottom = rowTop + itemHeight
            val rowRect = RectF(left + host.dp(6f), rowTop, left + menuWidth - host.dp(6f), rowBottom)
            menuItemRects.add(panel.id to RectF(left, rowTop, left + menuWidth, rowBottom))

            val isActive = panel.id == currentPanelId
            val isPressed = panel.id == pressedMenuPanelId
            val displayName = panelNames[i]

            if (isPressed || isActive) {
                val rowCorner = host.dp(8f)
                menuBgPaint.color = when {
                    isPressed -> if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(30, 0, 0, 0)
                    isActive -> Color.argb(35, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    else -> Color.TRANSPARENT
                }
                canvas.drawRoundRect(rowRect, rowCorner, rowCorner, menuBgPaint)
            }

            // 对勾图标
            if (isActive) {
                menuTextPaint.color = accentColor
                menuTextPaint.typeface = Typeface.DEFAULT_BOLD
                menuTextPaint.textSize = host.sp(13f)
                canvas.drawText("✓", rowRect.left + host.dp(10f), rowRect.centerY() - (menuTextPaint.descent() + menuTextPaint.ascent()) / 2f, menuTextPaint)
            }

            // 文字
            menuTextPaint.color = when {
                isActive -> accentColor
                isDark -> Color.WHITE
                else -> Color.argb(230, 28, 28, 30)
            }
            menuTextPaint.typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val textX = rowRect.left + host.dp(28f)
            val textY = rowRect.centerY() - (menuTextPaint.descent() + menuTextPaint.ascent()) / 2f
            canvas.drawText(displayName, textX, textY, menuTextPaint)
        }
    }

    fun collectAccessibilityNodes(context: android.content.Context, panelRect: RectF): List<OverlayVirtualNode> {
        layoutToolbar(panelRect)
        val nodes = mutableListOf<OverlayVirtualNode>()
        if (!addButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.quick_launcher_add),
                boundsInParent = RectF(addButtonRect)
            )
        }
        if (!switchButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.quick_launcher_panel_switch),
                boundsInParent = RectF(switchButtonRect)
            )
        }
        if (!editButtonRect.isEmpty) {
            val label = if (controller.editMode) {
                context.getString(R.string.cd_action_confirm)
            } else {
                context.getString(R.string.widget_panel_edit_mode)
            }
            nodes += OverlayVirtualNode(
                description = label,
                boundsInParent = RectF(editButtonRect)
            )
        }
        if (!closeButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.quick_launcher_pinned_close),
                boundsInParent = RectF(closeButtonRect)
            )
        }
        return nodes
    }
}
