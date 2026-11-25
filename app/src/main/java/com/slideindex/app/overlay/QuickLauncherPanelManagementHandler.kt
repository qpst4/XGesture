package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.launcher.QuickLauncherDragTiming
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType

internal class QuickLauncherPanelManagementHandler(
    private val controller: QuickLauncherPanelController,
    private val host: QuickLauncherPanelController.Host,
    private val toolbar: QuickLauncherPanelToolbar
) {
    private var dragFromIndex = -1
    private var dragTargetIndex = -1
    private var dragFromGlobal = -1
    private var dragToGlobal = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f
    private var managementTouchActive = false

    private var pendingDragCellIndex = -1
    private var pendingDragGlobal = -1
    private var pendingDragStartX = 0f
    private var pendingDragStartY = 0f
    private var dragLongPressRunnable: Runnable? = null

    private var hoverCandidateGlobal = -1
    private var hoverAnchorX = 0f
    private var hoverAnchorY = 0f
    private var mergeTargetGlobal = -1
    private var hoverDwellRunnable: Runnable? = null

    internal fun cancelPendingDrag() {
        dragLongPressRunnable?.let { host.removeCallbacks(it) }
        dragLongPressRunnable = null
        pendingDragCellIndex = -1
        pendingDragGlobal = -1
    }

    private fun cancelHoverDwell() {
        hoverDwellRunnable?.let { host.removeCallbacks(it) }
        hoverDwellRunnable = null
        hoverCandidateGlobal = -1
        if (mergeTargetGlobal >= 0) {
            mergeTargetGlobal = -1
            host.invalidate()
        }
    }

    fun reset() {
        cancelPendingDrag()
        cancelHoverDwell()
        dragFromIndex = -1
        dragTargetIndex = -1
        dragFromGlobal = -1
        dragToGlobal = -1
        managementTouchActive = false
    }

    fun onEditModeDisabled() {
        cancelPendingDrag()
        cancelHoverDwell()
        dragFromIndex = -1
        dragTargetIndex = -1
        dragFromGlobal = -1
        dragToGlobal = -1
    }

    fun handleManagementTouch(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        panelRect: RectF,
        cellBounds: List<Pair<Any, RectF>>,
        tapGesture: Boolean = false,
        toolbarCommitAllowed: Boolean = true
    ): Boolean {
        if (toolbar.panelMenuShowing) {
            if (toolbar.handleMenuTouch(localX, localY, event.actionMasked)) {
                return true
            }
        }
        if (!toolbar.shouldShowToolbar()) return false

        toolbar.layoutToolbar(panelRect)
        val quickCells = cellBounds.mapNotNull { (item, rect) ->
            (item as? QuickLauncherItem)?.let { it to rect }
        }
        toolbar.layoutDeleteBadges(quickCells.map { it.second }, dragFromIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val toolbarAction = toolbar.resolveToolbarAction(localX, localY, panelRect)
                toolbar.setArmedToolbarAction(toolbarAction)
                managementTouchActive = toolbar.toolbarContains(localX, localY) ||
                    toolbarAction != null ||
                    (controller.editMode && toolbar.deleteBadgeIndexAt(localX, localY) >= 0) ||
                    (controller.editMode && indexAt(localX, localY, quickCells) >= 0)

                if (toolbarAction == QuickLauncherPanelToolbar.ToolbarAction.SWITCH) {
                    toolbar.onCapsuleDown(localX, localY)
                    cancelPendingDrag()
                    cancelHoverDwell()
                    return true
                }

                if (toolbarAction != null) {
                    cancelPendingDrag()
                    cancelHoverDwell()
                    return true
                }

                if (controller.editMode) {
                    val badgeIndex = toolbar.deleteBadgeIndexAt(localX, localY)
                    if (badgeIndex >= 0) {
                        cancelPendingDrag()
                        cancelHoverDwell()
                        return true
                    }

                    val cellIndex = indexAt(localX, localY, quickCells)
                    if (cellIndex >= 0) {
                        cancelPendingDrag()
                        cancelHoverDwell()
                        val globalIdx = controller.itemPageOffset + cellIndex
                        pendingDragCellIndex = cellIndex
                        pendingDragGlobal = globalIdx
                        pendingDragStartX = localX
                        pendingDragStartY = localY
                        val runnable = Runnable {
                            if (pendingDragCellIndex == cellIndex) {
                                dragFromGlobal = pendingDragGlobal
                                dragToGlobal = dragFromGlobal
                                dragFromIndex = pendingDragCellIndex
                                dragTargetIndex = pendingDragCellIndex
                                dragStartX = pendingDragStartX
                                dragStartY = pendingDragStartY
                                dragCurrentX = pendingDragStartX
                                dragCurrentY = pendingDragStartY
                                hoverAnchorX = pendingDragStartX
                                hoverAnchorY = pendingDragStartY
                                hoverCandidateGlobal = pendingDragGlobal
                                mergeTargetGlobal = -1
                                host.hapticTick()
                                host.onEditDragBegan()
                                host.invalidate()
                            }
                        }
                        dragLongPressRunnable = runnable
                        host.postDelayed(runnable, 180L)
                        return true
                    }
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragFromGlobal >= 0) {
                    dragCurrentX = localX
                    dragCurrentY = localY
                    host.onEditDragMove(localX, localY, panelRect)
                    updateEditDragTarget(localX, localY, panelRect, quickCells)
                    checkHoverMergeTarget(localX, localY)
                    host.invalidate()
                    return true
                }
                if (pendingDragCellIndex >= 0) {
                    val dx = localX - pendingDragStartX
                    val dy = localY - pendingDragStartY
                    val slop = host.dp(10f)
                    if (dx * dx + dy * dy > slop * slop) {
                        cancelPendingDrag()
                        cancelHoverDwell()
                        managementTouchActive = false
                    }
                }
                if (toolbar.armedToolbarAction == QuickLauncherPanelToolbar.ToolbarAction.SWITCH) {
                    if (toolbar.onCapsuleMove(localX, localY)) {
                        host.invalidate()
                        return true
                    }
                }
                if (toolbar.toolbarContains(localX, localY) || toolbar.armedToolbarAction != null) {
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingDrag()
                if (toolbar.armedToolbarAction == QuickLauncherPanelToolbar.ToolbarAction.SWITCH) {
                    toolbar.onCapsuleUp()
                    toolbar.setArmedToolbarAction(null)
                    managementTouchActive = false
                    host.invalidate()
                    return true
                }
                var handled = false
                if (toolbar.commitToolbarAtRelease(
                        localX = localX,
                        localY = localY,
                        panelRect = panelRect,
                        tapGesture = tapGesture,
                        toolbarCommitAllowed = toolbarCommitAllowed,
                        allowSlideRelease = false
                    )
                ) {
                    handled = true
                    managementTouchActive = false
                    host.invalidate()
                }

                if (controller.editMode) {
                    val badgeIndex = toolbar.deleteBadgeIndexAt(localX, localY)
                    if (badgeIndex >= 0) {
                        controller.removeItemAt(badgeIndex)
                        handled = true
                    } else if (dragFromGlobal >= 0) {
                        if (mergeTargetGlobal >= 0 && mergeTargetGlobal != dragFromGlobal) {
                            controller.mergeItemsGlobal(dragFromGlobal, mergeTargetGlobal)
                            handled = true
                        } else {
                            updateEditDragTarget(localX, localY, panelRect, quickCells)
                            val itemCount = controller.workingItems().size
                            val insertIndex = QuickLauncherGridLogic.dragInsertIndex(
                                dragSlotGlobal = dragToGlobal,
                                itemCount = itemCount
                            )
                            if (insertIndex in 0..itemCount && dragFromGlobal != insertIndex) {
                                controller.moveItemGlobal(dragFromGlobal, insertIndex)
                                handled = true
                            }
                        }
                    } else if (tapGesture) {
                        val cellIndex = indexAt(localX, localY, quickCells)
                        if (cellIndex >= 0) {
                            val globalIdx = controller.itemPageOffset + cellIndex
                            val item = controller.workingItems().getOrNull(globalIdx)
                            if (item?.type == QuickLauncherItemType.FOLDER) {
                                host.onFolderOpen(globalIdx)
                                handled = true
                            }
                        }
                    }
                }
                cancelHoverDwell()
                dragFromIndex = -1
                dragTargetIndex = -1
                dragFromGlobal = -1
                dragToGlobal = -1
                managementTouchActive = false
                if (handled) host.invalidate()
                return handled
            }
        }
        return false
    }

    private fun checkHoverMergeTarget(localX: Float, localY: Float) {
        if (dragFromGlobal < 0) {
            cancelHoverDwell()
            return
        }
        val items = controller.workingItems()
        val draggedItem = items.getOrNull(dragFromGlobal)
        if (draggedItem == null || draggedItem.type == QuickLauncherItemType.FOLDER) {
            cancelHoverDwell()
            return
        }

        val targetSlot = dragToGlobal
        val targetItem = items.getOrNull(targetSlot)
        if (targetSlot < 0 || targetSlot == dragFromGlobal || targetItem == null) {
            cancelHoverDwell()
            return
        }

        val deadzone = host.dp(14f)
        val dx = localX - hoverAnchorX
        val dy = localY - hoverAnchorY
        val movedOutOfDeadzone = (dx * dx + dy * dy) > (deadzone * deadzone)

        if (targetSlot != hoverCandidateGlobal || movedOutOfDeadzone) {
            hoverDwellRunnable?.let { host.removeCallbacks(it) }
            hoverDwellRunnable = null
            hoverCandidateGlobal = targetSlot
            hoverAnchorX = localX
            hoverAnchorY = localY
            if (mergeTargetGlobal >= 0) {
                mergeTargetGlobal = -1
                host.invalidate()
            }
            val candidate = targetSlot
            val runnable = Runnable {
                if (dragFromGlobal >= 0 && hoverCandidateGlobal == candidate) {
                    mergeTargetGlobal = candidate
                    host.hapticTick()
                    host.invalidate()
                }
            }
            hoverDwellRunnable = runnable
            host.postDelayed(runnable, QuickLauncherDragTiming.FOLDER_MERGE_DWELL_MS)
        }
    }

    fun isDragging(): Boolean = dragFromGlobal >= 0
    fun dragSourceIndex(): Int = dragFromIndex
    fun dragDestinationIndex(): Int = dragTargetIndex
    fun dragSourceGlobal(): Int = dragFromGlobal
    fun dragDestinationGlobal(): Int = dragToGlobal
    fun dragMergeTargetGlobal(): Int = mergeTargetGlobal

    fun dragSourceOnPage(pageStart: Int, pageSize: Int): Boolean {
        if (dragFromGlobal < 0) return false
        return dragFromGlobal in pageStart until pageStart + pageSize
    }

    fun dragSourceOnCurrentPage(): Boolean {
        val pageSize = host.quickLauncherPageSize().coerceAtLeast(1)
        return dragSourceOnPage(controller.itemPageOffset, pageSize)
    }

    fun dragPointerX(): Float = dragCurrentX
    fun dragPointerY(): Float = dragCurrentY

    fun dragVisualOffsetForPage(pageStart: Int, pageSize: Int): Pair<Float, Float> {
        if (!dragSourceOnPage(pageStart, pageSize)) return 0f to 0f
        return (dragCurrentX - dragStartX) to (dragCurrentY - dragStartY)
    }

    fun dragVisualOffset(index: Int): Pair<Float, Float> {
        if (index != dragFromIndex || dragFromIndex < 0 || !dragSourceOnCurrentPage()) {
            return 0f to 0f
        }
        return (dragCurrentX - dragStartX) to (dragCurrentY - dragStartY)
    }

    fun syncPageLocalDragTarget() {
        val pageSize = host.quickLauncherPageSize().coerceAtLeast(1)
        dragTargetIndex = if (dragToGlobal in controller.itemPageOffset until controller.itemPageOffset + pageSize) {
            dragToGlobal - controller.itemPageOffset
        } else {
            -1
        }
        dragFromIndex = if (dragFromGlobal in controller.itemPageOffset until controller.itemPageOffset + pageSize) {
            dragFromGlobal - controller.itemPageOffset
        } else {
            -1
        }
    }

    private fun updateEditDragTarget(
        localX: Float,
        localY: Float,
        panelRect: RectF,
        quickCells: List<Pair<QuickLauncherItem, RectF>>
    ) {
        if (dragFromGlobal < 0) return
        val globalTarget = host.resolveEditDragTargetGlobal(localX, localY, panelRect)
        if (globalTarget >= 0) {
            dragToGlobal = globalTarget
            syncPageLocalDragTarget()
            return
        }
        dragTargetIndex = targetIndexForDrag(localX, localY, quickCells)
        if (dragTargetIndex >= 0) {
            dragToGlobal = controller.itemPageOffset + dragTargetIndex
            syncPageLocalDragTarget()
        }
    }

    private fun indexAt(
        localX: Float,
        localY: Float,
        cellBounds: List<Pair<QuickLauncherItem, RectF>>
    ): Int {
        cellBounds.forEachIndexed { index, (_, rect) ->
            if (rect.contains(localX, localY)) return index
        }
        return -1
    }

    private fun targetIndexForDrag(
        localX: Float,
        localY: Float,
        cellBounds: List<Pair<QuickLauncherItem, RectF>>
    ): Int {
        if (cellBounds.isEmpty()) return dragFromIndex
        cellBounds.forEachIndexed { index, (_, rect) ->
            if (rect.contains(localX, localY)) return index
        }
        var best = dragFromIndex
        var bestDist = Float.MAX_VALUE
        cellBounds.forEachIndexed { index, (_, rect) ->
            val dx = localX - rect.centerX()
            val dy = localY - rect.centerY()
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                best = index
            }
        }
        return best
    }
}
