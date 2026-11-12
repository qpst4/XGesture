package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withClip
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherLabels
import com.slideindex.app.launcher.isCloneTarget
import com.slideindex.app.launcher.showsMacroBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.launcher.showsShortcutBadge
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.QuickLauncherIconResolver

internal class QuickLauncherRenderer(
    private val ctrl: QuickLauncherOverlayController
) {
    private val host get() = ctrl.host

    private fun localizedContext() = AppLocaleApplier.wrapOverlayContext(host.context)

    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cellInitialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val folderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val folderSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
    }
    private val folderButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val folderEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(160, 255, 255, 255)
    }
    private val tmpRect = RectF()
    private val iconClipPath = Path()

    private val quickLauncherDisplay get() = host.settings().quickLauncherDisplay
    private val quickLauncherGridIconSize get() =
        host.dp(quickLauncherDisplay.iconSizeDp.toFloat())
    private val quickLauncherIconShape get() = quickLauncherDisplay.iconShape
    private val quickLauncherGridIconTopInset get() = host.dp(4f)
    private val quickLauncherGridIconLabelGap get() = host.dp(4f)
    private val gridCellInset get() = host.dp(4f)
    private val panelCorner get() = host.dp(18f)

    fun syncSettings(@Suppress("UNUSED_PARAMETER") settings: AppSettings) {
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        appLabelPaint.textSize = host.sp(11f)
    }

    /**
     * 面板打开时预取图标。
     *
     * [items] 由调用方按「当前页 → 相邻页 → 其余页」排序传入，先提交的先抢到后台线程。
     * 这里只提交任务，绝不解析：解析耗时（PackageManager + OEM 主题图标）必须留在后台，
     * 否则魅族/小米等机型会直接把主线程卡到 ANR。
     */
    fun prewarm(items: List<QuickLauncherItem>) {
        prewarmIcons(items)
        prewarmShortcuts(items)
        prewarmActionIcons(items)
    }

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) {
        val panelRect = ctrl.quickLauncherPanelRect()
        if (panelRect.isEmpty) return
        ctrl.quickLauncherPagination()
        host.panelGridSession().cellBounds.clear()
        host.panelContentRect().set(panelRect)
        drawQuickLauncherPanelChrome(canvas, panelRect)

        val dragOffset = ctrl.quickLauncherPageDragOffset
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val pagingActive = ctrl.quickLauncherPageSwipeLocked ||
            ctrl.quickLauncherPageSnapMotion.isRunning ||
            kotlin.math.abs(dragOffset) > host.dp(0.5f)
        ctrl.quickLauncherLayoutPanelWidth = panelWidth
        val recordCells = !pagingActive
        canvas.withClip(panelRect) {
            drawQuickLauncherPageCells(
                canvas = this,
                panelRect = panelRect,
                pageIndex = ctrl.quickLauncherPageIndex,
                translateX = if (pagingActive) dragOffset else 0f,
                recordCells = recordCells
            )
            if (pagingActive && kotlin.math.abs(dragOffset) > host.dp(0.5f)) {
                QuickLauncherScrollHandler.adjacentPagesForDrag(
                    dragOffset = dragOffset,
                    currentPageIndex = ctrl.quickLauncherPageIndex,
                    pageCount = ctrl.quickLauncherPageCount,
                    pageWidth = panelWidth,
                    side = ctrl.quickLauncherLayoutSide()
                ).forEach { layer ->
                    drawQuickLauncherPageCells(
                        canvas = this,
                        panelRect = panelRect,
                        pageIndex = layer.pageIndex,
                        translateX = layer.translateX,
                        recordCells = false
                    )
                }
            }
        }

        if (ctrl.quickLauncherPanelController.editMode && ctrl.quickLauncherPanelController.isDragging()) {
            drawQuickLauncherEditDragFloater(canvas, panelRect)
        }

        drawQuickLauncherPageIndicator(canvas, panelRect)
        if (drawToolbar) {
            ctrl.quickLauncherPanelController.drawToolbar(canvas, panelRect)
        }
        ctrl.quickLauncherPanelController.layoutDeleteBadges(host.panelGridSession().cellBounds.map { it.second })
        ctrl.quickLauncherPanelController.drawDeleteBadges(canvas)

        if (ctrl.folderOpen) {
            drawQuickLauncherFolderOverlay(canvas, panelRect)
        }
    }

    private fun prewarmIcons(items: List<QuickLauncherItem>) {
        if (items.isEmpty()) return
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        if (ctrl.quickLauncherAppsByPackage.isEmpty()) {
            ctrl.rebuildQuickLauncherAppsByPackage()
        }
        items.forEach { item -> requestQuickLauncherItemIcon(item, size, urgent = false) }
    }

    private fun prewarmShortcuts(items: List<QuickLauncherItem>) {
        val shortcuts = items.filter { it.type == QuickLauncherItemType.SHORTCUT }
        if (shortcuts.isEmpty()) return
        val appContext = host.context.applicationContext
        OverlayIconLoader.submit {
            AppShortcutLoader.warmQuickLauncherShortcuts(appContext, shortcuts)
        }
    }

    private fun prewarmActionIcons(items: List<QuickLauncherItem>) {
        val sizePx = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        val actions = items.filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload) }
        if (actions.isEmpty()) return
        OverlayIconLoader.submit {
            actions.forEach { action ->
                GestureActionIconBitmap.preload(action, sizePx, outlined = true)
            }
        }
    }

    /**
     * 后台解析单个图标。只在 [OverlayIconLoader] 的工作线程里执行，
     * 参数在提交前就从主线程快照好，避免后台线程回头读 View 状态。
     */
    private fun resolveQuickLauncherIcon(
        item: QuickLauncherItem,
        size: Int,
        context: Context,
        appsByPackage: Map<String, AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>
    ): Bitmap? {
        // 全局图标覆盖优先：这条路径（shouldUseGestureVectorIcon）原本直接画默认矢量图标，
        // 会绕过 QuickLauncherIconResolver，所以必须在这里先查一次覆盖。
        if (item.type == QuickLauncherItemType.ACTION) {
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                com.slideindex.app.ui.ActionIconBitmap.load(
                    context = context,
                    action = action,
                    sizePx = size,
                    tintArgb = android.graphics.Color.WHITE,
                    withPlate = true,
                )?.let { return it }
                // 「运行宏」要显示宏目标 App 的图标；拿不到（宏被删 / 目标 App 卸载）
                // 再落回下面的通用播放键，别把整格变成首字母占位。
                if (action is GestureAction.RunMacro) {
                    QuickLauncherIconResolver.iconBitmap(
                        item = item,
                        appsByPackage = appsByPackage,
                        size = size,
                        context = context,
                        activityShortcuts = activityShortcuts,
                        shellCommands = shellCommands,
                    )?.let { return it }
                }
            }
        }
        if (item.type == QuickLauncherItemType.ACTION &&
            QuickLauncherIconResolver.shouldUseGestureVectorIcon(item, shellCommands)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size,
                tintArgb = Color.WHITE,
                outlined = true,
                withPlate = true
            )
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = appsByPackage,
            size = size,
            context = context,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands
        )
    }

    private fun quickLauncherItemLabel(item: QuickLauncherItem): String {
        val cacheKey = ctrl.quickLauncherItemCacheKey(item)
        ctrl.quickLauncherLabelCache[cacheKey]?.let { return it }
        if (ctrl.quickLauncherAppsByPackage.isEmpty()) {
            ctrl.rebuildQuickLauncherAppsByPackage()
        }
        val label = when (item.type) {
            QuickLauncherItemType.APP,
            QuickLauncherItemType.SHORTCUT,
            QuickLauncherItemType.ACTION,
            QuickLauncherItemType.WIDGET,
            QuickLauncherItemType.FOLDER,
            -> QuickLauncherLabels.resolveLabel(
                localizedContext(),
                item,
                ctrl.quickLauncherAppsByPackage,
                shellCommands = host.settings().shellCommands,
            )
        }
        ctrl.quickLauncherLabelCache[cacheKey] = label
        return label
    }

    /**
     * 绘制路径取图标：命中就画，未命中提交后台解析并返回 null（调用方画占位）。
     * 任何情况下都不会在这里触发 PackageManager / 磁盘 IO。
     */
    private fun quickLauncherItemIcon(item: QuickLauncherItem): Bitmap? {
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        if (ctrl.quickLauncherAppsByPackage.isEmpty()) {
            ctrl.rebuildQuickLauncherAppsByPackage()
        }
        val key = quickLauncherIconKey(item, size)
        OverlayIconLoader.peek(key)?.let { return it }
        requestQuickLauncherItemIcon(item, size, urgent = true)
        return null
    }

    private fun requestQuickLauncherItemIcon(
        item: QuickLauncherItem,
        size: Int,
        urgent: Boolean
    ) {
        val key = quickLauncherIconKey(item, size)
        val appContext = host.context.applicationContext
        val appsByPackage = ctrl.quickLauncherAppsByPackage
        val activityShortcuts = host.settings().activityShortcuts
        val shellCommands = host.settings().shellCommands
        OverlayIconLoader.request(
            key = key,
            urgent = urgent,
            load = {
                resolveQuickLauncherIcon(
                    item = item,
                    size = size,
                    context = appContext,
                    appsByPackage = appsByPackage,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands
                )
            },
            onReady = { ctrl.invalidateQuickLauncherPanel() }
        )
    }

    private fun quickLauncherIconKey(item: QuickLauncherItem, size: Int): String =
        "${ctrl.quickLauncherItemCacheKey(item)}\u0000$size\u0000$quickLauncherIconShape" +
            "\u0000v35\u0000${quickLauncherIconCatalogStamp()}\u0000${ctrl.quickLauncherAppsRevision}" +
            "\u0000${com.slideindex.app.ui.GlobalIconStore.revision}" +
            "\u0000${com.slideindex.app.macro.MacroStore.revision}" +
            "\u0000${com.slideindex.app.ui.IconPlateModeState.mode}" +
            "\u0000${com.slideindex.app.ui.IconPlateModeState.lastResolvedDark}"

    private var cachedShortcutCatalog: List<ActivityShortcut>? = null
    private var cachedShellCatalog: List<ShellCommand>? = null
    private var cachedCatalogStamp: String = ""

    /**
     * 快捷方式目录 / 快捷命令目录的指纹，用于让这些条目在目录变化后重新取图。
     * 目录本身按引用比对，避免每格每帧都拼一次字符串。
     */
    private fun quickLauncherIconCatalogStamp(): String {
        val shortcuts = host.settings().activityShortcuts
        val shells = host.settings().shellCommands
        if (shortcuts !== cachedShortcutCatalog || shells !== cachedShellCatalog) {
            cachedShortcutCatalog = shortcuts
            cachedShellCatalog = shells
            cachedCatalogStamp = buildString {
                shortcuts.forEach { shortcut ->
                    append(shortcut.identityKey()).append(':').append(shortcut.iconPath.orEmpty()).append(';')
                }
                append('\u0000')
                shells.forEach { shell ->
                    append(shell.id).append(':').append(shell.iconType).append(':')
                        .append(shell.iconPath.orEmpty()).append(':').append(shell.textIcon.orEmpty()).append(';')
                }
            }
        }
        return cachedCatalogStamp
    }

    private val frostedGlassDrawable = LocalFrostedGlassDrawable { host.overlayView() }

    private fun drawQuickLauncherPanelChrome(canvas: Canvas, grid: RectF) {
        val alpha = QuickLauncherDisplaySettings.backgroundAlphaArgb(
            host.settings().quickLauncherDisplay.backgroundOpacityPercent
        )
        val blurRadiusDp = if (host.settings().overlayBlurEnabled) {
            host.settings().quickLauncherDisplay.blurRadiusDp
        } else {
            // 全局模糊总开关关闭：半径为 0，下面自然走实色绘制兜底。
            0
        }
        val blurDrawn = if (blurRadiusDp <= 0) {
            false
        } else {
            frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = grid,
                cornerRadiusPx = panelCorner,
                blurRadiusPx = host.dp(blurRadiusDp.toFloat()).toInt(),
                tintColor = Color.argb(alpha, 48, 48, 52),
                enabled = host.settings().overlayBlurEnabled,
            )
        }
        if (!blurDrawn) {
            panelBgPaint.color = Color.argb(alpha, 48, 48, 52)
            canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        }
    }

    private fun drawQuickLauncherPageCells(
        canvas: Canvas,
        panelRect: RectF,
        pageIndex: Int,
        translateX: Float,
        recordCells: Boolean
    ) {
        val entries = ctrl.quickLauncherItemsForPage(pageIndex)
        val rootItems = ctrl.quickLauncherRootItems()
        val layer = if (translateX != 0f) canvas.save() else -1
        if (layer >= 0) {
            canvas.translate(translateX, 0f)
        }
        val m = ctrl.quickLauncherColumnsPerPage()
        // 排版侧（受「右侧面板镜像」开关影响），与面板物理侧分开：只影响格子铺法与翻页方向。
        val layoutSide = ctrl.quickLauncherLayoutSide()
        val pageSize = ctrl.quickLauncherPageSize().coerceAtLeast(1)
        val pageStart = pageIndex * pageSize
        val fromGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragSourceGlobal() else -1
        val toGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragDestinationGlobal() else -1
        val mergeTargetGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragMergeTargetGlobal() else -1
        val itemCount = rootItems.size
        val mappingSize = pageStart + pageSize
        val editDragActive = recordCells &&
            ctrl.quickLauncherPanelController.editMode &&
            fromGlobal >= 0 &&
            toGlobal >= 0
        val dragMapping = if (editDragActive) {
            QuickLauncherGridLogic.displayMapping(
                itemCount = itemCount,
                dragFrom = fromGlobal,
                dragSlotGlobal = toGlobal,
                mappingSize = mappingSize,
                mergeTargetGlobal = mergeTargetGlobal
            )
        } else {
            null
        }
        if (entries.isEmpty() && dragMapping == null) {
            if (layer >= 0) canvas.restoreToCount(layer)
            return
        }
        val dragSourceIndex = if (recordCells) {
            quickLauncherDragLocalIndexOnPage(
                globalIndex = fromGlobal,
                pageStart = pageStart,
                pageItemCount = pageSize
            )
        } else {
            -1
        }
        val slotCount = pageSize
        fun drawCellAt(index: Int) {
            if (index !in 0 until slotCount) return
            val globalHere = pageStart + index
            val item: QuickLauncherItem
            if (dragMapping != null) {
                val showOrig = dragMapping.getOrNull(globalHere) ?: return
                if (showOrig == fromGlobal) return
                item = rootItems.getOrNull(showOrig) ?: return
            } else {
                if (index !in entries.indices) return
                item = entries[index]
            }
            val col = index % m
            val row = index / m
            val visualCol = visualColumn(col, m, m, layoutSide)
            val left = panelRect.left + ctrl.quickLauncherGridPadding + visualCol * ctrl.quickLauncherCellWidth
            val top = panelRect.top + ctrl.quickLauncherHeaderHeight + ctrl.quickLauncherGridPadding +
                row * ctrl.quickLauncherCellHeight
            val cell = RectF(left, top, left + ctrl.quickLauncherCellWidth, top + ctrl.quickLauncherCellHeight)
            val (offsetX, offsetY) = 0f to 0f
            if (offsetX != 0f || offsetY != 0f) {
                canvas.save()
                canvas.translate(offsetX, offsetY)
            }
            if (recordCells) {
                host.panelGridSession().cellBounds.add(item to cell)
            }
            val isMergeTarget = recordCells && mergeTargetGlobal >= 0 && globalHere == mergeTargetGlobal
            if (isMergeTarget) {
                val corner = host.dp(12f)
                cellHighlightPaint.style = Paint.Style.FILL
                cellHighlightPaint.color = Color.argb(80, 100, 160, 255)
                canvas.drawRoundRect(cell, corner, corner, cellHighlightPaint)
                cellHighlightPaint.style = Paint.Style.STROKE
                cellHighlightPaint.strokeWidth = host.dp(2f)
                cellHighlightPaint.color = Color.argb(230, 100, 180, 255)
                canvas.drawRoundRect(cell, corner, corner, cellHighlightPaint)
                cellHighlightPaint.style = Paint.Style.FILL
            }
            drawGridCell(
                canvas,
                cell,
                index,
                quickLauncherItemLabel(item),
                iconProvider = { quickLauncherItemIcon(item) },
                showShortcutBadge = item.showsShortcutBadge(),
                showShellCommandBadge = item.showsShellCommandBadge(host.settings().shellCommands),
            showMacroBadge = item.showsMacroBadge(),
            isClone = item.isCloneTarget(host.context),
                longPressArmed = recordCells &&
                    index == host.panelGridSession().highlightedIndex &&
                    ctrl.quickLauncherLongPressArmed,
                iconSize = quickLauncherGridIconSize,
                iconTopInset = quickLauncherGridIconTopInset,
                iconLabelGap = quickLauncherGridIconLabelGap,
                labelMaxWidth = ctrl.quickLauncherCellWidth - gridCellInset * 2
            )
            if (offsetX != 0f || offsetY != 0f) {
                canvas.restore()
            }
        }
        for (index in 0 until slotCount) {
            if (index != dragSourceIndex) {
                drawCellAt(index)
            }
        }
        if (dragSourceIndex in 0 until slotCount) {
            drawCellAt(dragSourceIndex)
        }
        if (layer >= 0) {
            canvas.restoreToCount(layer)
        }
    }

    private fun quickLauncherDragLocalIndexOnPage(
        globalIndex: Int,
        pageStart: Int,
        pageItemCount: Int
    ): Int {
        if (globalIndex < 0 || pageItemCount <= 0) return -1
        if (globalIndex !in pageStart until pageStart + pageItemCount) return -1
        return globalIndex - pageStart
    }

    private fun drawQuickLauncherEditDragFloater(canvas: Canvas, panelRect: RectF) {
        val globalFrom = ctrl.quickLauncherPanelController.dragSourceGlobal()
        val item = ctrl.quickLauncherRootItems().getOrNull(globalFrom) ?: return
        val cx = ctrl.quickLauncherPanelController.dragPointerX()
        val cy = ctrl.quickLauncherPanelController.dragPointerY()
        val isMerging = ctrl.quickLauncherPanelController.dragMergeTargetGlobal() >= 0
        val scale = if (isMerging) 0.90f else 1.10f
        val halfW = (ctrl.quickLauncherCellWidth * scale) / 2f
        val halfH = (ctrl.quickLauncherCellHeight * scale) / 2f
        val cell = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val shadowBlur = host.dp(6f)
        val shadowLayers = 3
        val shadowAlpha = 50
        val shadowRect = RectF()
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            cellHighlightPaint.color = Color.argb(alpha, 0, 0, 0)
            shadowRect.set(
                cell.left - spread,
                cell.top - spread + host.dp(3f),
                cell.right + spread,
                cell.bottom + spread + host.dp(3f)
            )
            canvas.drawRoundRect(shadowRect, host.dp(14f), host.dp(14f), cellHighlightPaint)
        }

        drawGridCell(
            canvas = canvas,
            cell = cell,
            index = -1,
            label = quickLauncherItemLabel(item),
            iconProvider = { quickLauncherItemIcon(item) },
            showShortcutBadge = item.showsShortcutBadge(),
            showShellCommandBadge = item.showsShellCommandBadge(host.settings().shellCommands),
            showMacroBadge = item.showsMacroBadge(),
            isClone = item.isCloneTarget(host.context),
            iconSize = quickLauncherGridIconSize * scale,
            iconTopInset = quickLauncherGridIconTopInset * scale,
            iconLabelGap = quickLauncherGridIconLabelGap * scale,
            labelMaxWidth = (ctrl.quickLauncherCellWidth - gridCellInset * 2) * scale
        )
    }

    private fun drawQuickLauncherPageIndicator(canvas: Canvas, grid: RectF) {
        drawPageIndicator(
            canvas = canvas,
            rect = grid,
            pageIndex = ctrl.quickLauncherPageIndex,
            pageCount = ctrl.quickLauncherPageCount,
            bottomInset = host.dp(10f)
        )
    }

    private fun drawPageIndicator(
        canvas: Canvas,
        rect: RectF,
        pageIndex: Int,
        pageCount: Int,
        bottomInset: Float
    ) {
        if (pageCount <= 1 || rect.isEmpty) return
        val dotRadius = host.dp(2.5f)
        val dotGap = host.dp(6f)
        val totalWidth = pageCount * dotRadius * 2f + (pageCount - 1) * dotGap
        val cy = rect.bottom - bottomInset
        // 页码圆点也按排版侧排：镜像时从右往左，不镜像时一律从左往右。
        val layoutSide = ctrl.quickLauncherLayoutSide()
        var cx = when (layoutSide) {
            PanelSide.RIGHT -> rect.centerX() + totalWidth / 2f - dotRadius
            else -> rect.centerX() - totalWidth / 2f + dotRadius
        }
        val step = if (layoutSide == PanelSide.RIGHT) {
            -(dotRadius * 2f + dotGap)
        } else {
            dotRadius * 2f + dotGap
        }
        for (page in 0 until pageCount) {
            pageIndicatorPaint.color = if (page == pageIndex) {
                Color.argb(230, 255, 255, 255)
            } else {
                Color.argb(90, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, dotRadius, pageIndicatorPaint)
            cx += step
        }
    }

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
        showShortcutBadge: Boolean = false,
        showShellCommandBadge: Boolean = false,
        showMacroBadge: Boolean = false,
        isClone: Boolean = false,
        longPressArmed: Boolean = false,
        iconSize: Float = quickLauncherGridIconSize,
        iconTopInset: Float = quickLauncherGridIconTopInset,
        iconLabelGap: Float = quickLauncherGridIconLabelGap,
        labelMaxWidth: Float = ctrl.quickLauncherCellWidth - gridCellInset * 2
    ) {
        if (index == host.panelGridSession().highlightedIndex) {
            tmpRect.set(
                cell.left + host.dp(3f),
                cell.top + host.dp(2f),
                cell.right - host.dp(3f),
                cell.bottom - host.dp(2f)
            )
            val paint = if (longPressArmed) cellLongPressHighlightPaint else cellHighlightPaint
            canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), paint)
        }
        val icon = iconProvider()
        val showLabels = quickLauncherDisplay.showLabels
        // 不显示名称时把图标在格子里垂直居中，避免每格底部空出一条。
        val iconTop = if (showLabels) {
            cell.top + iconTopInset
        } else {
            cell.top + (cell.height() - iconSize) / 2f
        }
        val displayLabel = ellipsize(label, labelMaxWidth)
        val labelBaseline = iconTop + iconSize + iconLabelGap - appLabelPaint.fontMetrics.ascent
        val iconCenterX = cell.centerX()
        val iconCenterY = iconTop + iconSize / 2f
        tmpRect.set(
            iconCenterX - iconSize / 2f,
            iconTop,
            iconCenterX + iconSize / 2f,
            iconTop + iconSize
        )
        QuickLauncherIconMask.pathFor(
            shape = quickLauncherIconShape,
            bounds = tmpRect,
            out = iconClipPath
        )
        if (icon != null) {
            if (quickLauncherIconShape == QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT) {
                canvas.drawBitmap(icon, null, tmpRect, iconBitmapPaint)
            } else {
                canvas.withClip(iconClipPath) {
                    drawBitmap(icon, null, tmpRect, iconBitmapPaint)
                }
            }
        } else {
            cellHighlightPaint.color = Color.argb(130, 48, 48, 52)
            canvas.drawPath(iconClipPath, cellHighlightPaint)
            cellHighlightPaint.color = Color.argb(90, 255, 255, 255)
            cellHighlightPaint.style = Paint.Style.STROKE
            cellHighlightPaint.strokeWidth = host.dp(1f)
            canvas.drawPath(iconClipPath, cellHighlightPaint)
            cellHighlightPaint.style = Paint.Style.FILL

            val initial = displayLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            cellInitialPaint.textSize = host.sp(15f)
            cellInitialPaint.color = Color.argb(220, 255, 255, 255)
            cellInitialPaint.isFakeBoldText = true
            canvas.drawText(
                initial,
                iconCenterX,
                iconCenterY - (cellInitialPaint.descent() + cellInitialPaint.ascent()) / 2f,
                cellInitialPaint
            )
        }
        if (showShortcutBadge) {
            ShortcutBadgeRenderer.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                host.dp(1f)
            )
        } else if (showShellCommandBadge) {
            ShellCommandBadgeRenderer.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                host.dp(1f)
            )
        } else if (showMacroBadge) {
            // 「运行宏」显示的是目标 App 图标，角标用来和「打开应用」区分
            MacroBadgeRenderer.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                host.dp(1f)
            )
        }
        // 分身黄点：画在图标形状裁剪之外，圆形图标用 45° 圆内安全位
        if (isClone) {
            com.slideindex.app.util.CloneBadge.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                circular = quickLauncherIconShape == QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE,
            )
        }
        if (showLabels) {
            canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
        }
    }

    private fun drawQuickLauncherFolderOverlay(canvas: Canvas, panelRect: RectF) {
        val folder = ctrl.folderItem ?: return
        val children = ctrl.folderSubPanelItems
        val childCount = children.size
        val folderHandler = ctrl.folderHandler
        val folderLayout = folderHandler.computeLayout(panelRect)
        ctrl.folderRect.set(folderLayout.rect)

        cellHighlightPaint.color = Color.argb(130, 0, 0, 0)
        canvas.drawRoundRect(panelRect, panelCorner, panelCorner, cellHighlightPaint)

        val folderLeft = folderLayout.rect.left
        val folderRight = folderLayout.rect.right
        val folderTop = folderLayout.rect.top
        val folderCorner = host.dp(20f)
        val blurRadiusDp = if (host.settings().overlayBlurEnabled) {
            host.settings().quickLauncherDisplay.blurRadiusDp
        } else {
            // 全局模糊总开关关闭：半径为 0，下面自然走实色绘制兜底。
            0
        }
        val blurDrawn = if (blurRadiusDp <= 0) {
            false
        } else {
            frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = ctrl.folderRect,
                cornerRadiusPx = folderCorner,
                blurRadiusPx = host.dp(blurRadiusDp.toFloat()).toInt(),
                tintColor = Color.argb(235, 34, 34, 38),
                enabled = host.settings().overlayBlurEnabled,
            )
        }
        if (!blurDrawn) {
            panelBgPaint.color = Color.argb(235, 34, 34, 38)
            canvas.drawRoundRect(ctrl.folderRect, folderCorner, folderCorner, panelBgPaint)
        }
        cellHighlightPaint.color = Color.argb(45, 255, 255, 255)
        cellHighlightPaint.style = Paint.Style.STROKE
        cellHighlightPaint.strokeWidth = host.dp(1f)
        canvas.drawRoundRect(ctrl.folderRect, folderCorner, folderCorner, cellHighlightPaint)
        cellHighlightPaint.style = Paint.Style.FILL

        val title = folder.label.ifBlank { localizedContext().getString(R.string.quick_launcher_item_folder) }
        folderTitlePaint.textSize = host.sp(14f)
        folderTitlePaint.color = Color.WHITE
        val titleX = folderLeft + host.dp(14f)
        val titleY = folderTop + host.dp(28f)
        canvas.drawText(title, titleX, titleY, folderTitlePaint)

        val titleW = folderTitlePaint.measureText(title)
        val subtitle = localizedContext().resources.getQuantityString(
            R.plurals.quick_launcher_folder_items_count,
            childCount,
            childCount,
        )
        folderSubtitlePaint.textSize = host.sp(11f)
        canvas.drawText(subtitle, titleX + titleW + host.dp(8f), titleY, folderSubtitlePaint)

        val closeBtnR = host.dp(12f)
        val closeCenterX = folderRight - host.dp(20f)
        val closeCenterY = folderTop + host.dp(22f)
        ctrl.folderCloseButtonBounds.set(
            closeCenterX - closeBtnR - host.dp(4f),
            closeCenterY - closeBtnR - host.dp(4f),
            closeCenterX + closeBtnR + host.dp(4f),
            closeCenterY + closeBtnR + host.dp(4f)
        )
        cellHighlightPaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(closeCenterX, closeCenterY, closeBtnR, cellHighlightPaint)
        folderButtonPaint.textSize = host.sp(12f)
        folderButtonPaint.isFakeBoldText = true
        canvas.drawText("✕", closeCenterX, closeCenterY - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f, folderButtonPaint)

        if (ctrl.quickLauncherPanelController.editMode) {
            val addBtnW = host.dp(46f)
            val addBtnH = host.dp(24f)
            val addBtnRight = closeCenterX - closeBtnR - host.dp(10f)
            val addBtnLeft = addBtnRight - addBtnW
            val addBtnTop = folderTop + host.dp(10f)
            val addBtnBottom = addBtnTop + addBtnH
            ctrl.folderAddButtonBounds.set(addBtnLeft, addBtnTop, addBtnRight, addBtnBottom)
            cellHighlightPaint.color = Color.argb(80, 66, 133, 244)
            canvas.drawRoundRect(ctrl.folderAddButtonBounds, host.dp(12f), host.dp(12f), cellHighlightPaint)
            folderButtonPaint.textSize = host.sp(11f)
            folderButtonPaint.isFakeBoldText = false
            canvas.drawText(
                localizedContext().getString(R.string.quick_launcher_folder_add),
                ctrl.folderAddButtonBounds.centerX(),
                ctrl.folderAddButtonBounds.centerY() - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f,
                folderButtonPaint
            )
        } else {
            ctrl.folderAddButtonBounds.setEmpty()
        }

        val dragOffset = folderHandler.pageDragOffset
        val folderWidth = folderLayout.folderWidth.coerceAtLeast(1f)
        val pagingActive = folderHandler.pagingActiveForHitTest()
        val recordCells = !pagingActive
        val contentClip = RectF(
            folderLayout.rect.left,
            folderLayout.contentStartY,
            folderLayout.rect.right,
            folderLayout.rect.bottom - folderLayout.indicatorHeight
        )
        canvas.withClip(contentClip) {
            drawFolderPageCells(
                canvas = this,
                folderLayout = folderLayout,
                pageIndex = folderHandler.pageIndex,
                translateX = if (pagingActive) dragOffset else 0f,
                recordCells = recordCells
            )
            if (pagingActive && kotlin.math.abs(dragOffset) > host.dp(0.5f)) {
                QuickLauncherScrollHandler.adjacentPagesForDrag(
                    dragOffset = dragOffset,
                    currentPageIndex = folderHandler.pageIndex,
                    pageCount = folderHandler.pageCount,
                    pageWidth = folderWidth,
                    side = ctrl.quickLauncherLayoutSide()
                ).forEach { layer ->
                    drawFolderPageCells(
                        canvas = this,
                        folderLayout = folderLayout,
                        pageIndex = layer.pageIndex,
                        translateX = layer.translateX,
                        recordCells = false
                    )
                }
            }
        }

        if (folderHandler.pageCount > 1) {
            drawFolderPageIndicator(canvas, folderLayout, folderHandler.pageIndex, folderHandler.pageCount)
        }

        if (folderHandler.isDragging()) {
            drawFolderEditDragFloater(canvas)
        }
    }

    private fun drawFolderPageCells(
        canvas: Canvas,
        folderLayout: QuickLauncherFolderHandler.FolderLayout,
        pageIndex: Int,
        translateX: Float,
        recordCells: Boolean
    ) {
        val children = ctrl.folderSubPanelItems
        if (children.isEmpty()) {
            if (recordCells) ctrl.folderCellBounds.clear()
            val emptyText = localizedContext().getString(R.string.quick_launcher_folder_empty)
            folderEmptyPaint.textSize = host.sp(12f)
            canvas.drawText(
                emptyText,
                folderLayout.rect.centerX() + translateX,
                folderLayout.rect.centerY() + host.dp(10f),
                folderEmptyPaint
            )
            return
        }
        if (recordCells) ctrl.folderCellBounds.clear()
        val pageSize = folderLayout.pageSize.coerceAtLeast(1)
        val pageStart = pageIndex.coerceIn(0, ctrl.folderHandler.pageCount - 1) * pageSize
        val columns = folderLayout.columns
        // 文件夹内的排版侧与主面板保持一致，否则开关一翻，文件夹里的点选也会跟着错位。
        val layoutSide = ctrl.quickLauncherLayoutSide()
        val fromGlobal = if (recordCells) ctrl.folderHandler.dragSourceGlobal() else -1
        val toGlobal = if (recordCells) ctrl.folderHandler.dragDestinationGlobal() else -1
        val editDragActive = recordCells &&
            ctrl.quickLauncherPanelController.editMode &&
            fromGlobal >= 0 &&
            toGlobal >= 0 &&
            ctrl.folderHandler.isDragging()
        val dragMapping = if (editDragActive) {
            QuickLauncherGridLogic.displayMappingForPage(
                itemCount = children.size,
                dragFrom = fromGlobal,
                dragSlotGlobal = toGlobal,
                pageStart = pageStart,
                pageSize = pageSize
            )
        } else {
            null
        }
        val pageItemCount = (children.size - pageStart).coerceAtLeast(0).coerceAtMost(pageSize)
        val slotCount = when {
            dragMapping != null -> pageSize
            recordCells && ctrl.quickLauncherPanelController.editMode -> pageSize
            else -> pageItemCount
        }
        val layer = if (translateX != 0f) canvas.save() else -1
        if (layer >= 0) canvas.translate(translateX, 0f)

        for (localIndex in 0 until slotCount) {
            val globalHere = pageStart + localIndex
            val child: QuickLauncherItem
            val childGlobalIndex: Int
            if (dragMapping != null) {
                val showOrig = dragMapping.getOrNull(localIndex) ?: continue
                if (showOrig == fromGlobal) continue
                child = children.getOrNull(showOrig) ?: continue
                childGlobalIndex = showOrig
            } else {
                if (globalHere !in children.indices) continue
                child = children[globalHere]
                childGlobalIndex = globalHere
            }
            val col = localIndex % columns
            val row = localIndex / columns
            val visualCol = visualColumn(col, columns, columns, layoutSide)
            val cLeft = folderLayout.rect.left + folderLayout.padding + visualCol * folderLayout.cellW
            val cTop = folderLayout.contentStartY + row * folderLayout.cellH
            val cRect = RectF(cLeft, cTop, cLeft + folderLayout.cellW, cTop + folderLayout.cellH)
            if (recordCells) ctrl.folderCellBounds.add(childGlobalIndex to cRect)

            drawGridCell(
                canvas = canvas,
                cell = cRect,
                index = if (childGlobalIndex == ctrl.folderHighlightLocalIndex) {
                    host.panelGridSession().highlightedIndex
                } else {
                    -2
                },
                label = quickLauncherItemLabel(child),
                iconProvider = { quickLauncherItemIcon(child) },
                showShortcutBadge = child.showsShortcutBadge(),
                showShellCommandBadge = child.showsShellCommandBadge(host.settings().shellCommands),
                showMacroBadge = child.showsMacroBadge(),
                isClone = child.isCloneTarget(host.context),
                longPressArmed = childGlobalIndex == ctrl.folderHighlightLocalIndex && ctrl.folderLongPressArmed,
                labelMaxWidth = folderLayout.cellW - gridCellInset * 2
            )

            if (ctrl.quickLauncherPanelController.editMode) {
                val badgeSize = host.dp(16f)
                val badgeLeft = cRect.left + host.dp(6f)
                val badgeTop = cRect.top + host.dp(2f)
                val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)
                cellHighlightPaint.color = Color.argb(220, 229, 57, 53)
                canvas.drawCircle(badgeRect.centerX(), badgeRect.centerY(), badgeSize / 2f, cellHighlightPaint)
                folderButtonPaint.textSize = host.sp(10f)
                folderButtonPaint.isFakeBoldText = true
                canvas.drawText("−", badgeRect.centerX(), badgeRect.centerY() - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f, folderButtonPaint)
            }
        }
        if (layer >= 0) canvas.restoreToCount(layer)
    }

    private val folderHandler get() = ctrl.folderHandler

    private fun drawFolderPageIndicator(
        canvas: Canvas,
        folderLayout: QuickLauncherFolderHandler.FolderLayout,
        pageIndex: Int,
        pageCount: Int
    ) {
        drawPageIndicator(
            canvas = canvas,
            rect = folderLayout.rect,
            pageIndex = pageIndex,
            pageCount = pageCount,
            bottomInset = host.dp(8f)
        )
    }

    private fun drawFolderEditDragFloater(canvas: Canvas) {
        val globalFrom = ctrl.folderHandler.dragSourceGlobal()
        val item = ctrl.folderSubPanelItems.getOrNull(globalFrom) ?: return
        val cx = ctrl.folderHandler.dragPointerX()
        val cy = ctrl.folderHandler.dragPointerY()
        val scale = 1.10f
        val halfW = (ctrl.quickLauncherCellWidth * scale) / 2f
        val halfH = (ctrl.quickLauncherCellHeight * scale) / 2f
        val cell = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val shadowBlur = host.dp(6f)
        val shadowLayers = 3
        val shadowAlpha = 50
        val shadowRect = RectF()
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            cellHighlightPaint.color = Color.argb(alpha, 0, 0, 0)
            shadowRect.set(
                cell.left - spread,
                cell.top - spread + host.dp(3f),
                cell.right + spread,
                cell.bottom + spread + host.dp(3f)
            )
            canvas.drawRoundRect(shadowRect, host.dp(14f), host.dp(14f), cellHighlightPaint)
        }

        drawGridCell(
            canvas = canvas,
            cell = cell,
            index = -1,
            label = quickLauncherItemLabel(item),
            iconProvider = { quickLauncherItemIcon(item) },
            showShortcutBadge = item.showsShortcutBadge(),
            showShellCommandBadge = item.showsShellCommandBadge(host.settings().shellCommands),
            showMacroBadge = item.showsMacroBadge(),
            isClone = item.isCloneTarget(host.context),
            iconSize = quickLauncherGridIconSize * scale,
            iconTopInset = quickLauncherGridIconTopInset * scale,
            iconLabelGap = quickLauncherGridIconLabelGap * scale,
            labelMaxWidth = (ctrl.quickLauncherCellWidth - gridCellInset * 2) * scale
        )
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
    }
}
