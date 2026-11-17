package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine

internal class QuickLauncherFolderHandler(
    private val ctrl: QuickLauncherOverlayController
) {
    internal val host get() = ctrl.host
    private val pageSnapMotion get() = ctrl.quickLauncherPageSnapMotion

    internal var pageIndex = 0
    internal var pageCount = 1
    internal var pageSwipeStartX = 0f
    internal var pageSwipeStartY = 0f
    internal var pageSwipeLocked = false
    internal var pageSwipeTracking = false
    internal var pageChangedThisGesture = false
    internal var pageDragOffset = 0f
    internal var edgePageZone = 0
    internal var edgeAutoPageSeeded = false

    internal var dragFromGlobal = -1
    internal var dragToGlobal = -1
    internal var dragStartX = 0f
    internal var dragStartY = 0f
    internal var dragCurrentX = 0f
    internal var dragCurrentY = 0f
    internal var pendingDragGlobal = -1
    internal var pendingDragStartX = 0f
    internal var pendingDragStartY = 0f
    private var dragLongPressRunnable: Runnable? = null

    internal var layout: FolderLayout? = null

    internal data class FolderLayout(
        val rect: RectF,
        val columns: Int,
        val rows: Int,
        val pageSize: Int,
        val pageCount: Int,
        val headerHeight: Float,
        val padding: Float,
        val cellW: Float,
        val cellH: Float,
        val contentStartY: Float,
        val indicatorHeight: Float
    ) {
        val folderWidth: Float get() = rect.width()
    }

    fun reset() {
        pageIndex = 0
        pageCount = 1
        pageSwipeLocked = false
        pageSwipeTracking = false
        pageChangedThisGesture = false
        pageDragOffset = 0f
        edgePageZone = 0
        edgeAutoPageSeeded = false
        cancelFolderDrag()
        layout = null
    }

    fun syncPagination(childCount: Int, pageSize: Int) {
        pageCount = QuickLauncherGridLogic.pageCount(childCount, pageSize.coerceAtLeast(1))
        pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun computeLayout(panelRect: RectF): FolderLayout {
        val columns = ctrl.quickLauncherColumnsPerPage()
            .coerceIn(2, QuickLauncherPanelLayoutEngine.MAX_COLUMNS)
        val headerHeight = host.dp(44f)
        val padding = host.dp(10f)
        val cellH = ctrl.quickLauncherCellHeight
        val maxH = panelRect.height() - host.dp(24f)
        val indicatorHeight = host.dp(14f)
        val rows = ((maxH - headerHeight - padding - indicatorHeight) / cellH)
            .toInt()
            .coerceAtLeast(1)
        val pageSize = columns * rows
        val childCount = ctrl.folderSubPanelItems.size
        val pageCount = QuickLauncherGridLogic.pageCount(childCount, pageSize)
        syncPagination(childCount, pageSize)

        val folderLeft = panelRect.left + host.dp(6f)
        val folderRight = panelRect.right - host.dp(6f)
        val cellW = (folderRight - folderLeft - padding * 2f) / columns.toFloat()
        val contentRows = rows
        val dotsSpace = if (pageCount > 1) indicatorHeight else 0f
        val folderH = headerHeight + contentRows * cellH + padding + dotsSpace
        val clampedH = folderH.coerceAtMost(maxH)
        val folderTop = (panelRect.centerY() - clampedH / 2f).coerceIn(
            panelRect.top + host.dp(10f),
            panelRect.bottom - clampedH - host.dp(10f)
        )
        val rect = RectF(folderLeft, folderTop, folderRight, folderTop + clampedH)
        return FolderLayout(
            rect = rect,
            columns = columns,
            rows = contentRows,
            pageSize = pageSize,
            pageCount = pageCount,
            headerHeight = headerHeight,
            padding = padding,
            cellW = cellW,
            cellH = cellH,
            contentStartY = folderTop + headerHeight,
            indicatorHeight = dotsSpace
        ).also { layout = it }
    }

    fun pagingActiveForHitTest(): Boolean =
        pageSwipeLocked ||
            pageSnapMotion.isRunning ||
            kotlin.math.abs(pageDragOffset) > host.dp(0.5f)

    fun childGlobalIndexAt(touchX: Float, touchY: Float, folderLayout: FolderLayout): Int {
        if (!folderLayout.rect.contains(touchX, touchY)) return -1
        val pageSize = folderLayout.pageSize.coerceAtLeast(1)
        val folderWidth = folderLayout.folderWidth.coerceAtLeast(1f)
        val pagingActive = pagingActiveForHitTest()

        if (pagingActive && pageCount > 1) {
            val contentBottom = folderLayout.rect.bottom - folderLayout.indicatorHeight
            if (touchY < folderLayout.contentStartY || touchY >= contentBottom) return -1
            val layers = buildList {
                add(pageIndex to pageDragOffset)
                addAll(
                    QuickLauncherScrollHandler.adjacentPagesForDrag(
                        dragOffset = pageDragOffset,
                        currentPageIndex = pageIndex,
                        pageCount = pageCount,
                        pageWidth = folderWidth,
                        side = ctrl.quickLauncherLayoutSide()
                    ).map { it.pageIndex to it.translateX }
                )
            }
            for ((candidatePageIdx, translateX) in layers) {
                val xInPage = touchX - translateX
                if (xInPage < folderLayout.rect.left || xInPage >= folderLayout.rect.right) continue
                val localSlot = localSlotAt(xInPage, touchY, folderLayout)
                val globalIndex = candidatePageIdx * pageSize + localSlot
                if (globalIndex in ctrl.folderSubPanelItems.indices) return globalIndex
            }
            return -1
        }

        val pageIdx = pageIndex.coerceIn(0, pageCount - 1)
        val localSlot = localSlotAt(touchX, touchY, folderLayout)
        val globalIndex = pageIdx * pageSize + localSlot
        return if (globalIndex in ctrl.folderSubPanelItems.indices) globalIndex else -1
    }

    private fun localSlotAt(xInPage: Float, touchY: Float, folderLayout: FolderLayout): Int {
        val columns = folderLayout.columns
        val rows = folderLayout.rows
        val col = ((xInPage - folderLayout.rect.left - folderLayout.padding) / folderLayout.cellW)
            .toInt()
            .coerceIn(0, columns - 1)
        val row = ((touchY - folderLayout.contentStartY) / folderLayout.cellH)
            .toInt()
            .coerceIn(0, rows - 1)
        val colInRow = when (ctrl.quickLauncherLayoutSide()) {
            PanelSide.RIGHT -> columns - 1 - col
            else -> col
        }
        return (row * columns + colInRow).coerceIn(0, folderLayout.pageSize - 1)
    }

    fun handleTouch(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float
    ): Boolean {
        val panelRect = ctrl.quickLauncherPanelRect()
        val folderLayout = layout ?: computeLayout(panelRect)
        ctrl.folderRect.set(folderLayout.rect)
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        val closeHit = ctrl.folderCloseButtonBounds.contains(touchX, localY)
        val addHit = ctrl.quickLauncherPanelController.editMode &&
            ctrl.folderAddButtonBounds.contains(touchX, localY)
        val insideFolder = folderLayout.rect.contains(touchX, localY)
        val isToolbarHit = ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ctrl.folderGestureActive = true
                pageChangedThisGesture = false
                pageDragOffset = 0f
                pageSwipeLocked = false
                edgePageZone = 0
                edgeAutoPageSeeded = false
                pageSwipeStartX = touchX
                pageSwipeStartY = localY
                pageSwipeTracking = pageCount > 1 && insideFolder
                pageSnapMotion.cancel()
                if (isToolbarHit) return false
                if (closeHit) {
                    ctrl.closeFolder()
                    return true
                }
                if (addHit) {
                    ctrl.quickLauncherPanelController.openAddDialog(ctrl.folderGlobalIndex)
                    return true
                }
                if (ctrl.quickLauncherPanelController.editMode) {
                    val deleteGlobal = deleteBadgeGlobalIndex(touchX, localY)
                    if (deleteGlobal >= 0) {
                        ctrl.quickLauncherPanelController.removeFolderChildItem(
                            ctrl.folderGlobalIndex,
                            deleteGlobal
                        )
                        refreshFolderChildren()
                        host.hapticTick()
                        host.invalidate()
                        return true
                    }
                    val cellGlobal = hitCellGlobalIndex(touchX, localY)
                    if (cellGlobal >= 0) {
                        beginPendingDrag(cellGlobal, touchX, localY)
                        return true
                    }
                }
                if (insideFolder) {
                    val hit = hitCellGlobalIndex(touchX, localY)
                    ctrl.folderHighlightLocalIndex = hit
                    if (hit >= 0) {
                        host.hapticTick()
                        if (!ctrl.quickLauncherPanelController.editMode) {
                            ctrl.scheduleFolderLongPress(hit, event.eventTime)
                        }
                    } else {
                        ctrl.cancelFolderLongPress()
                    }
                    host.invalidate()
                    return true
                } else {
                    ctrl.closeFolder()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isToolbarHit) return false
                if (!ctrl.folderOpen) return true
                if (closeHit && continuousPick) {
                    ctrl.closeFolder()
                    host.hapticTick()
                    return true
                }
                if (isDragging()) {
                    dragCurrentX = touchX
                    dragCurrentY = localY
                    applyEditDragAutoPage(touchX, folderLayout)
                    updateDragTarget(touchX, localY, folderLayout)
                    host.invalidate()
                    return true
                }
                if (consumePageSwipeMove(touchX, localY, insideFolder)) {
                    host.invalidate()
                    return true
                }
                if (pendingDragGlobal >= 0 && !pageSwipeLocked && !pageSwipeTracking) {
                    val dx = touchX - pendingDragStartX
                    val dy = localY - pendingDragStartY
                    val slop = host.dp(10f)
                    if (dx * dx + dy * dy > slop * slop) {
                        cancelPendingDrag()
                    }
                }
                if (continuousPick && insideFolder) {
                    applyEdgeAutoPage(touchX, folderLayout)
                }
                if (insideFolder) {
                    val hit = if (pagingActiveForHitTest()) {
                        childGlobalIndexAt(touchX, localY, folderLayout)
                    } else {
                        hitCellGlobalIndex(touchX, localY)
                    }
                    if (hit != ctrl.folderHighlightLocalIndex) {
                        ctrl.folderHighlightLocalIndex = hit
                        if (hit >= 0) {
                            host.hapticTick()
                            ctrl.scheduleFolderLongPress(hit, event.eventTime)
                        } else {
                            ctrl.cancelFolderLongPress()
                        }
                        host.invalidate()
                    }
                } else if (ctrl.folderHighlightLocalIndex >= 0) {
                    ctrl.folderHighlightLocalIndex = -1
                    ctrl.cancelFolderLongPress()
                    host.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ctrl.folderGestureActive = false
                cancelPendingDrag()
                if (isToolbarHit) return false
                if (!ctrl.folderOpen) return true
                if (pageSwipeLocked) {
                    finishPageDrag(folderLayout)
                    pageSwipeLocked = false
                    pageSwipeTracking = false
                    host.invalidate()
                    return true
                }
                if (closeHit) {
                    ctrl.closeFolder()
                    return true
                }
                if (addHit) {
                    ctrl.quickLauncherPanelController.openAddDialog(ctrl.folderGlobalIndex)
                    return true
                }
                if (isDragging()) {
                    commitDrag(folderLayout, touchX, localY)
                    host.invalidate()
                    return true
                }
                val hit = ctrl.folderHighlightLocalIndex
                if (hit in ctrl.folderSubPanelItems.indices && !ctrl.quickLauncherPanelController.editMode) {
                    launchFolderChild(hit, event, localX, localY)
                    return true
                } else if (!insideFolder && !closeHit && !addHit) {
                    ctrl.closeFolder()
                    return true
                }
                ctrl.folderHighlightLocalIndex = -1
                ctrl.cancelFolderLongPress()
                host.invalidate()
                return true
            }
        }
        return false
    }

    fun consumePageRelease(): Boolean {
        if (isDragging() || pageCount <= 1) return false
        if (pageSwipeLocked || kotlin.math.abs(pageDragOffset) > host.dp(6f)) {
            layout?.let { finishPageDrag(it) }
            pageSwipeLocked = false
            pageSwipeTracking = false
            host.invalidate()
            return true
        }
        if (pageSnapMotion.isRunning) {
            host.invalidate()
            return true
        }
        return false
    }

    fun isDragging(): Boolean = dragFromGlobal >= 0

    fun dragPointerX(): Float = dragCurrentX

    fun dragPointerY(): Float = dragCurrentY

    fun dragSourceGlobal(): Int = dragFromGlobal

    fun dragDestinationGlobal(): Int = dragToGlobal

    private fun hitCellGlobalIndex(touchX: Float, localY: Float): Int =
        ctrl.folderCellBounds.firstOrNull { (_, rect) -> rect.contains(touchX, localY) }?.first ?: -1

    private fun deleteBadgeGlobalIndex(touchX: Float, localY: Float): Int {
        ctrl.folderCellBounds.forEach { (globalIndex, rect) ->
            val badgeOnly = touchX in (rect.left - host.dp(6f))..(rect.left + host.dp(24f)) &&
                localY in (rect.top - host.dp(6f))..(rect.top + host.dp(24f))
            if (badgeOnly) return globalIndex
        }
        return -1
    }

    private fun beginPendingDrag(globalIndex: Int, touchX: Float, localY: Float) {
        cancelPendingDrag()
        pendingDragGlobal = globalIndex
        pendingDragStartX = touchX
        pendingDragStartY = localY
        val captured = globalIndex
        val runnable = Runnable {
            if (pendingDragGlobal == captured) {
                dragFromGlobal = captured
                dragToGlobal = captured
                dragStartX = pendingDragStartX
                dragStartY = pendingDragStartY
                dragCurrentX = pendingDragStartX
                dragCurrentY = pendingDragStartY
                edgePageZone = 0
                edgeAutoPageSeeded = false
                pendingDragGlobal = -1
                host.hapticTick()
                host.invalidate()
            }
        }
        dragLongPressRunnable = runnable
        host.postDelayed(runnable, 180L)
    }

    private fun cancelPendingDrag() {
        dragLongPressRunnable?.let { host.removeCallbacks(it) }
        dragLongPressRunnable = null
        pendingDragGlobal = -1
    }

    private fun cancelFolderDrag() {
        cancelPendingDrag()
        dragFromGlobal = -1
        dragToGlobal = -1
    }

    private fun updateDragTarget(touchX: Float, localY: Float, folderLayout: FolderLayout) {
        if (dragFromGlobal < 0) return
        val target = childGlobalIndexAt(touchX, localY, folderLayout)
        if (target >= 0) {
            dragToGlobal = target
            return
        }
        dragToGlobal = nearestChildGlobalIndex(touchX, localY, folderLayout)
    }

    private fun nearestChildGlobalIndex(
        touchX: Float,
        localY: Float,
        folderLayout: FolderLayout
    ): Int {
        if (ctrl.folderCellBounds.isEmpty()) return dragFromGlobal
        var best = dragFromGlobal
        var bestDist = Float.MAX_VALUE
        ctrl.folderCellBounds.forEach { (globalIndex, rect) ->
            val dx = touchX - rect.centerX()
            val dy = localY - rect.centerY()
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                best = globalIndex
            }
        }
        return best
    }

    private fun commitDrag(folderLayout: FolderLayout, touchX: Float, localY: Float) {
        updateDragTarget(touchX, localY, folderLayout)
        val itemCount = ctrl.folderSubPanelItems.size
        val insertIndex = QuickLauncherGridLogic.dragInsertIndex(
            dragSlotGlobal = dragToGlobal,
            itemCount = itemCount
        )
        if (insertIndex in 0..itemCount && dragFromGlobal in 0 until itemCount &&
            dragFromGlobal != insertIndex
        ) {
            ctrl.quickLauncherPanelController.moveFolderChildItem(
                ctrl.folderGlobalIndex,
                dragFromGlobal,
                insertIndex
            )
            refreshFolderChildren()
        }
        cancelFolderDrag()
    }

    private fun refreshFolderChildren() {
        ctrl.folderSubPanelItems = ctrl.quickLauncherRootItems()
            .getOrNull(ctrl.folderGlobalIndex)
            ?.folderItems()
            .orEmpty()
        layout?.let { syncPagination(ctrl.folderSubPanelItems.size, it.pageSize) }
    }

    private fun launchFolderChild(
        hit: Int,
        event: MotionEvent,
        localX: Float,
        localY: Float
    ) {
        val childItem = ctrl.folderSubPanelItems[hit]
        val longPress = ctrl.isFolderLongPressTriggered(event)
        ctrl.cancelFolderLongPress()
        if (childItem.type == QuickLauncherItemType.ACTION) {
            val action = QuickLauncherItemCodec.parseActionPayload(childItem.payload)
            if (action != null) {
                host.gestureSession().performQuickLauncherAction(
                    action,
                    localX,
                    localY,
                    event.rawX,
                    event.rawY,
                    confirmHaptic = longPress
                )
            }
        } else {
            if (longPress) host.hapticConfirmLaunch()
            ctrl.quickLauncherLaunchEndDeferMs = if (
                host.actionExecutor().launchQuickItem(
                    childItem,
                    host.settings(),
                    longPressArmed = longPress,
                    anchorRawX = event.rawX,
                    anchorRawY = event.rawY,
                    panelSide = host.side(),
                )
            ) {
                280L
            } else {
                0L
            }
        }
        ctrl.closeFolder()
        if (ctrl.quickLauncherLaunchEndDeferMs > 0L) {
            host.postDelayed({ host.gestureSession().endSession() }, ctrl.quickLauncherLaunchEndDeferMs)
        } else {
            host.post { host.gestureSession().endSession() }
        }
        ctrl.quickLauncherLaunchEndDeferMs = 0L
    }

    private fun consumePageSwipeMove(touchX: Float, localY: Float, insideFolder: Boolean): Boolean {
        if (!pageSwipeTracking || pageCount <= 1 || !insideFolder) return false
        if (isDragging()) return false
        val deltaX = touchX - pageSwipeStartX
        val deltaY = localY - pageSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val directionLock = host.dp(PAGE_SWIPE_DIRECTION_LOCK_DP)
        if (!pageSwipeLocked) {
            if (absX > directionLock && absX > absY * 1.25f) {
                pageSwipeLocked = true
                cancelPendingDrag()
                ctrl.cancelFolderLongPress()
            } else {
                return false
            }
        }
        updatePageDragOffset(deltaX, layout ?: return false)
        return true
    }

    private fun updatePageDragOffset(deltaX: Float, folderLayout: FolderLayout) {
        pageSnapMotion.cancel()
        val folderWidth = folderLayout.folderWidth.coerceAtLeast(1f)
        val offset = QuickLauncherScrollHandler.applyPageDragResistance(
            offset = deltaX,
            pageIndex = pageIndex,
            pageCount = pageCount,
            side = ctrl.quickLauncherLayoutSide(),
            resistance = PAGE_EDGE_RESISTANCE
        )
        pageDragOffset = offset.coerceIn(-folderWidth, folderWidth)
    }

    private fun finishPageDrag(folderLayout: FolderLayout) {
        val folderWidth = folderLayout.folderWidth.coerceAtLeast(1f)
        val delta = QuickLauncherScrollHandler.computePageCommitDelta(
            offset = pageDragOffset,
            panelWidth = folderWidth,
            pageIndex = pageIndex,
            pageCount = pageCount,
            side = ctrl.quickLauncherLayoutSide()
        )
        if (delta != 0) {
            pageIndex += delta
            pageChangedThisGesture = true
            pageDragOffset += QuickLauncherScrollHandler.pageCommitOffsetCompensation(
                delta = delta,
                pageWidth = folderWidth,
                side = ctrl.quickLauncherLayoutSide()
            )
        }
        animatePageSnapTo(0f)
    }

    private fun animatePageSnapTo(targetOffset: Float) {
        pageSnapMotion.cancel()
        val start = pageDragOffset
        pageSnapMotion.animateTo(
            start = start,
            target = targetOffset,
            epsilon = host.dp(0.5f),
            onValue = { value ->
                pageDragOffset = value
                host.invalidate()
            },
            onComplete = {
                pageDragOffset = targetOffset
                host.invalidate()
            }
        )
    }

    private fun applyEditDragAutoPage(touchX: Float, folderLayout: FolderLayout) {
        if (!isDragging()) return
        if (!ctrl.quickLauncherPanelController.editMode) return
        if (pageCount <= 1) return
        if (pageSnapMotion.isRunning) return
        applyEdgeAutoPageInternal(touchX, folderLayout)
    }

    private fun applyEdgeAutoPage(touchX: Float, folderLayout: FolderLayout) {
        if (pageCount <= 1) return
        if (pageSnapMotion.isRunning) return
        if (pageSwipeLocked) return
        applyEdgeAutoPageInternal(touchX, folderLayout)
    }

    private fun applyEdgeAutoPageInternal(touchX: Float, folderLayout: FolderLayout) {
        val zone = QuickLauncherScrollHandler.computeEdgePageZone(
            touchX = touchX,
            panelRect = folderLayout.rect,
            side = ctrl.quickLauncherLayoutSide(),
            edgePx = host.dp(EDGE_AUTO_PAGE_THRESHOLD_DP)
        )
        if (!edgeAutoPageSeeded) {
            edgeAutoPageSeeded = true
            edgePageZone = zone
            return
        }
        val prevZone = edgePageZone
        edgePageZone = zone
        if (zone == 0 || zone == prevZone) return
        val delta = when (zone) {
            -1 -> if (pageIndex > 0) -1 else 0
            1 -> if (pageIndex < pageCount - 1) 1 else 0
            else -> 0
        }
        if (delta == 0) return
        animatePageTurn(delta, folderLayout)
    }

    private fun animatePageTurn(delta: Int, folderLayout: FolderLayout) {
        if (delta == 0 || pageSnapMotion.isRunning) return
        pageSnapMotion.cancel()
        val folderWidth = folderLayout.folderWidth.coerceAtLeast(1f)
        pageIndex = (pageIndex + delta).coerceIn(0, pageCount - 1)
        pageChangedThisGesture = true
        pageDragOffset += QuickLauncherScrollHandler.pageCommitOffsetCompensation(
            delta = delta,
            pageWidth = folderWidth,
            side = ctrl.quickLauncherLayoutSide()
        )
        ctrl.folderHighlightLocalIndex = -1
        ctrl.cancelFolderLongPress()
        host.hapticTick()
        animatePageSnapTo(0f)
    }

    companion object {
        private const val PAGE_SWIPE_DIRECTION_LOCK_DP = 8f
        private const val PAGE_EDGE_RESISTANCE = 0.35f
        private const val EDGE_AUTO_PAGE_THRESHOLD_DP = 14f
    }
}
