package com.slideindex.app.widget

import android.view.MotionEvent
import androidx.core.view.ViewCompat
import kotlin.math.abs

internal class WidgetCanvasTouchHandler(
    private val layout: WidgetCanvasLayout,
) {
    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val chromeTarget = layout.chromeTouchTarget
        if (chromeTarget != null) {
            val handled = layout.deliverTouchToChild(chromeTarget, event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                layout.chromeTouchTarget = null
            }
            return handled
        }

        if (layout.editMode && layout.draggingChild == null && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val child = layout.findTouchTarget(event.x, event.y)
            if (child != null) {
                val localX = event.x - child.left - child.translationX
                val localY = event.y - child.top - child.translationY
                if (child.isTouchOnChrome(localX, localY)) {
                    layout.chromeTouchTarget = child
                    return layout.deliverTouchToChild(child, event)
                }
            }
        }
        if (layout.editMode && layout.draggingChild == null) {
            if (layout.panelScrollActive) {
                return onTouchEvent(event)
            }
            if (shouldDelegateWidgetBodyTouch(event)) {
                return layout.superDispatchTouchEvent(event)
            }
            return handleEditModePanelTouch(event)
        }
        if (layout.draggingChild != null) {
            return onTouchEvent(event)
        }
        if (!layout.editMode) {
            if (handleBrowseModeTouch(event)) return true
        }
        val handled = layout.superDispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            WidgetPanelProbe.gestureDown(
                editMode = layout.editMode,
                foundCard = layout.findTouchTarget(event.x, event.y) != null,
                handled = handled,
            )
        }
        if (!handled) {
            // 画布没接住这次手势 → 它不在触摸目标链里，后续 MOVE/UP/CANCEL 都不会送达，
            // 长按计时器再没有机会被取消；必须在这里立刻撤销，否则 500ms 后会误进编辑态。
            cancelBrowseLongPress()
        }
        return handled
    }

    fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (layout.draggingChild != null) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layout.panelScrollDownX = event.x
                layout.panelScrollDownY = event.y
                layout.panelScrollLastY = event.y
                layout.panelScrollActive = false
                layout.panelScrollTouchTarget = layout.findTouchTarget(event.x, event.y)
                if (!layout.editMode) return false
                val child = layout.panelScrollTouchTarget
                if (child != null) {
                    val localX = event.x - child.left - child.translationX
                    val localY = event.y - child.top - child.translationY
                    if (!child.isTouchOnChrome(localX, localY)) {
                        scheduleWidgetDragLongPress(child, event.x, event.y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (layout.editMode) {
                    if (layout.pendingDragChild != null && layout.draggingChild == null) {
                        val dx = event.x - layout.pendingDragDownX
                        val dy = event.y - layout.pendingDragDownY
                        if (dx * dx + dy * dy > layout.touchSlop * layout.touchSlop) {
                            cancelPendingDrag()
                        }
                    }
                    if (maybeStartPanelScroll(event)) {
                        return true
                    }
                    return layout.draggingChild != null
                }
                if (maybeStartPanelScroll(event)) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingDrag()
                layout.panelScrollTouchTarget = null
                if (layout.panelScrollActive) {
                    stopPanelScroll()
                    return true
                }
            }
        }
        return layout.draggingChild != null || layout.panelScrollActive
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (layout.panelScrollActive && layout.draggingChild == null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> return dispatchPanelNestedScroll(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopPanelScroll()
                    return true
                }
            }
        }

        if (layout.draggingChild != null) {
            val child = layout.draggingChild!!
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val left = event.x - layout.dragTouchOffsetX
                    val top = event.y - layout.dragTouchOffsetY
                    child.translationX = left - child.left
                    child.translationY = top - child.top
                    val oldHoverX = layout.hoverCellX
                    val oldHoverY = layout.hoverCellY
                    WidgetCanvasLayoutGeometry.updateHoverCell(layout, event.x, event.y)
                    if (oldHoverX != layout.hoverCellX || oldHoverY != layout.hoverCellY) {
                        layout.applyDragLiveReorderPreview()
                    }
                    layout.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag(child)
            }
            return true
        }

        if (!layout.editMode) {
            if (handleBlankAreaTouch(event)) return true
        }
        return false
    }

    fun cancelPendingDrag() {
        layout.pendingDragLongPress?.let { layout.removeCallbacks(it) }
        layout.pendingDragLongPress = null
        layout.pendingDragChild = null
    }

    fun cancelBrowseLongPress() {
        layout.pendingBrowseLongPress?.let { layout.removeCallbacks(it) }
        layout.pendingBrowseLongPress = null
        layout.browseTouchChild = null
        layout.browseTouchTracking = false
        layout.browseLongPressConsumed = false
    }

    fun cancelPendingLongPress() {
        layout.pendingLongPress?.let { layout.removeCallbacks(it) }
        layout.pendingLongPress = null
    }

    /** 撤销尚未触发的全部计时器（页面被摘除、或手势已确认丢失时调用）。 */
    fun cancelPendingTimers() {
        cancelPendingLongPress()
        cancelPendingDrag()
        cancelBrowseLongPress()
        layout.panelScrollActive = false
        layout.blankTouchTracking = false
        layout.chromeTouchTarget = null
    }

    fun resetTouchInteractionState() {
        cancelPendingLongPress()
        cancelPendingDrag()
        cancelBrowseLongPress()
        layout.blankTouchTracking = false
        layout.chromeTouchTarget = null
        layout.panelScrollActive = false
        layout.panelScrollTouchTarget = null
        stopPanelScroll()
        layout.requestDisallowInterceptAllParents(false)
        layout.resetAllChildrenLiveTranslations()
        layout.dragStartPage = null
        if (layout.draggingChild != null) {
            val child = layout.draggingChild!!
            child.animate().cancel()
            child.scaleX = 1f
            child.scaleY = 1f
            child.alpha = 1f
            child.translationZ = 0f
            child.translationX = 0f
            child.translationY = 0f
            layout.draggingChild = null
            layout.draggingItem = null
            layout.hoverCellX = -1
            layout.hoverCellY = -1
        }
        if (layout.canvasInteractionActive) {
            layout.updateInteractionActive(false)
            layout.post { layout.refreshAllWidgetLayouts() }
        }
    }

    private fun scheduleBrowseLongPress(child: WidgetCardContainer, x: Float, y: Float) {
        cancelBrowseLongPress()
        // 点在小组件自己的可交互内容上时，不抢长按：那会把这次点击取消掉
        // （真机表现为「按住稍久一点就点了没反应」）。空白/边框仍保留长按进编辑。
        if (child.shouldReceiveTouchAtPoint(x - child.left - child.translationX, y - child.top - child.translationY)) {
            return
        }
        layout.browseTouchChild = child
        layout.browseTouchDownX = x
        layout.browseTouchDownY = y
        layout.browseTouchTracking = true
        layout.pendingBrowseLongPress = Runnable {
            if (!layout.browseTouchTracking || layout.editMode) return@Runnable
            val target = layout.browseTouchChild ?: return@Runnable
            if (layout.findTouchTarget(layout.browseTouchDownX, layout.browseTouchDownY) !== target) return@Runnable
            val localX = layout.browseTouchDownX - target.left - target.translationX
            val localY = layout.browseTouchDownY - target.top - target.translationY
            if (target.shouldReceiveTouchAtPoint(localX, localY)) {
                cancelBrowseLongPress()
                return@Runnable
            }
            layout.browseLongPressConsumed = true
            layout.browseTouchTracking = false
            val cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            layout.deliverTouchToChild(target, cancel)
            cancel.recycle()
            layout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            layout.onLongPressBlank?.invoke()
        }
        layout.postDelayed(layout.pendingBrowseLongPress!!, layout.longPressTimeout)
    }

    private fun handleBrowseModeTouch(event: MotionEvent): Boolean {
        if (layout.editMode) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 上一次手势若在别的容器里结束，这个标记可能残留；新手势开始先清账。
                layout.browseLongPressConsumed = false
                val child = layout.findTouchTarget(event.x, event.y)
                if (child != null) {
                    scheduleBrowseLongPress(child, event.x, event.y)
                } else {
                    cancelBrowseLongPress()
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!layout.browseTouchTracking) return false
                val dx = event.x - layout.browseTouchDownX
                val dy = event.y - layout.browseTouchDownY
                if (dx * dx + dy * dy > layout.touchSlop * layout.touchSlop) {
                    cancelBrowseLongPress()
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (layout.browseLongPressConsumed) {
                    layout.browseLongPressConsumed = false
                    cancelBrowseLongPress()
                    return true
                }
                cancelBrowseLongPress()
                return false
            }
        }
        return false
    }

    private fun stopPanelScroll() {
        if (layout.panelScrollActive || layout.nestedScrollChildHelper.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)) {
            layout.nestedScrollChildHelper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        }
        layout.panelScrollActive = false
    }

    private fun dispatchPanelNestedScroll(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = (layout.panelScrollLastY - event.y).toInt()
                layout.panelScrollLastY = event.y
                if (dy == 0) return layout.panelScrollActive
                val consumed = IntArray(2)
                layout.nestedScrollChildHelper.dispatchNestedPreScroll(
                    0,
                    dy,
                    consumed,
                    null,
                    ViewCompat.TYPE_TOUCH,
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopPanelScroll()
                return layout.panelScrollActive
            }
        }
        return false
    }

    private fun maybeStartPanelScroll(event: MotionEvent): Boolean {
        val dx = event.x - layout.panelScrollDownX
        val dy = event.y - layout.panelScrollDownY
        val isOverWidget = layout.panelScrollTouchTarget != null
        val slop = if (isOverWidget) layout.touchSlop * 2.2f else layout.touchSlop.toFloat()
        if (dx * dx + dy * dy <= slop * slop) return false
        if (abs(dy) <= abs(dx)) return false
        if (shouldDeferPanelScrollToWidget(dy)) return false
        cancelPendingDrag()
        cancelPendingLongPress()
        cancelBrowseLongPress()
        layout.blankTouchTracking = false
        if (!layout.panelScrollActive) {
            layout.panelScrollActive = layout.nestedScrollChildHelper.startNestedScroll(
                ViewCompat.SCROLL_AXIS_VERTICAL,
                ViewCompat.TYPE_TOUCH,
            )
        }
        return layout.panelScrollActive
    }

    private fun handleEditModePanelTouch(event: MotionEvent): Boolean {
        if (layout.draggingChild != null) {
            return onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layout.panelScrollDownX = event.x
                layout.panelScrollDownY = event.y
                layout.panelScrollLastY = event.y
                layout.panelScrollActive = false
                layout.panelScrollTouchTarget = layout.findTouchTarget(event.x, event.y)
                val child = layout.panelScrollTouchTarget
                if (child != null) {
                    val localX = event.x - child.left - child.translationX
                    val localY = event.y - child.top - child.translationY
                    if (!child.isTouchOnChrome(localX, localY)) {
                        scheduleWidgetDragLongPress(child, event.x, event.y)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (maybeStartPanelScroll(event)) {
                    return dispatchPanelNestedScroll(event)
                }
                if (layout.pendingDragChild != null) {
                    val dx = event.x - layout.pendingDragDownX
                    val dy = event.y - layout.pendingDragDownY
                    if (dx * dx + dy * dy > layout.touchSlop * layout.touchSlop) {
                        cancelPendingDrag()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingDrag()
                layout.panelScrollTouchTarget = null
                stopPanelScroll()
                return true
            }
        }
        return true
    }

    private fun scheduleWidgetDragLongPress(child: WidgetCardContainer, x: Float, y: Float) {
        cancelPendingDrag()
        layout.pendingDragChild = child
        layout.pendingDragDownX = x
        layout.pendingDragDownY = y
        layout.pendingDragLongPress = Runnable {
            val target = layout.pendingDragChild
            if (target !== child || layout.draggingChild != null || !layout.editMode) {
                cancelPendingDrag()
                return@Runnable
            }
            layout.pendingDragChild = null
            layout.pendingDragLongPress = null
            startDrag(child, x, y)
        }
        layout.postDelayed(layout.pendingDragLongPress!!, layout.longPressTimeout)
    }

    private fun scheduleBlankLongPress() {
        cancelPendingLongPress()
        layout.pendingLongPress = Runnable {
            if (!layout.blankTouchTracking || layout.editMode) return@Runnable
            if (layout.findTouchTarget(layout.blankTouchDownX, layout.blankTouchDownY) == null) {
                layout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                layout.onLongPressBlank?.invoke()
            }
            layout.blankTouchTracking = false
        }
        layout.postDelayed(layout.pendingLongPress!!, layout.longPressTimeout)
    }

    private fun handleBlankAreaTouch(event: MotionEvent): Boolean {
        if (layout.editMode) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (layout.findTouchTarget(event.x, event.y) != null) return false
                layout.blankTouchTracking = true
                layout.blankTouchDownX = event.x
                layout.blankTouchDownY = event.y
                scheduleBlankLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!layout.blankTouchTracking) return false
                val dx = event.x - layout.blankTouchDownX
                val dy = event.y - layout.blankTouchDownY
                if (dx * dx + dy * dy > layout.touchSlop * layout.touchSlop) {
                    cancelPendingLongPress()
                    layout.blankTouchTracking = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!layout.blankTouchTracking) return false
                cancelPendingLongPress()
                val tappedBlank = event.actionMasked == MotionEvent.ACTION_UP &&
                    layout.findTouchTarget(event.x, event.y) == null
                layout.blankTouchTracking = false
                if (tappedBlank) {
                    layout.onTapBlank?.invoke()
                }
                return true
            }
        }
        return false
    }

    private fun startDrag(child: WidgetCardContainer, x: Float, y: Float) {
        layout.dragStartPage = layout.canvasPage
        child.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        layout.draggingChild = child
        layout.draggingItem = child.item
        layout.dragTouchOffsetX = x - (child.left + child.translationX)
        layout.dragTouchOffsetY = y - (child.top + child.translationY)
        child.bringToFront()
        child.translationZ = 8f * layout.resources.displayMetrics.density
        child.animate().cancel()
        child.scaleX = 1.05f
        child.scaleY = 1.05f
        child.alpha = 0.88f
        layout.updateInteractionActive(true)
        layout.requestDisallowInterceptAllParents(true)
        WidgetCanvasLayoutGeometry.updateHoverCell(layout, x, y)
        layout.applyDragLiveReorderPreview()
        layout.invalidate()
    }

    private fun endDrag(child: WidgetCardContainer) {
        val initialPage = layout.dragStartPage ?: layout.canvasPage
        val item = layout.draggingItem ?: return
        child.animate().cancel()
        child.scaleX = 1f
        child.scaleY = 1f
        child.alpha = 1f
        child.translationZ = 0f
        layout.updateInteractionActive(false)
        layout.requestDisallowInterceptAllParents(false)
        layout.resetAllChildrenLiveTranslations()

        if (initialPage != null && (layout.hoverCellX != item.x || layout.hoverCellY != item.y) && layout.hoverCellX >= 0 && layout.hoverCellY >= 0) {
            child.translationX = 0f
            child.translationY = 0f
            val updatedPage = WidgetPanelGridLogic.moveItemWithAutoSwapOrShift(initialPage, item, layout.hoverCellX, layout.hoverCellY)
            if (updatedPage != initialPage) {
                layout.commitPageChange(updatedPage)
            } else {
                layout.requestLayout()
            }
        } else {
            child.translationX = 0f
            child.translationY = 0f
            layout.requestLayout()
        }
        layout.dragStartPage = null
        layout.draggingChild = null
        layout.draggingItem = null
        layout.hoverCellX = -1
        layout.hoverCellY = -1
        layout.invalidate()
    }

    private fun shouldDelegateWidgetBodyTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val child = layout.findTouchTarget(event.x, event.y) ?: return false
                val localX = event.x - child.left - child.translationX
                val localY = event.y - child.top - child.translationY
                if (child.isTouchOnChrome(localX, localY)) return false
                if (!child.shouldReceiveTouchAtPoint(localX, localY)) return false
                layout.panelScrollTouchTarget = child
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val delegated = layout.panelScrollTouchTarget != null
                layout.panelScrollTouchTarget = null
                return delegated
            }
            else -> return layout.panelScrollTouchTarget != null
        }
    }

    private fun shouldDeferPanelScrollToWidget(dy: Float): Boolean {
        val target = layout.panelScrollTouchTarget ?: return false
        val localX = layout.panelScrollDownX - target.left - target.translationX
        val localY = layout.panelScrollDownY - target.top - target.translationY
        if (target.isTouchOnChrome(localX, localY)) return false
        // 只要小组件里这一方向还能滚，就一直让位；不再用「拖过 N 倍 slop 就收回」的
        // 距离窗口（那会导致滑两下就变成面板滚动）。
        return target.canContentScrollAtPoint(localX, localY, 0, dy)
    }
}
