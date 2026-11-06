package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.layout.TaskSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.TaskSwitcherLayoutHost
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.overlay.layout.TaskSwitcherRowEntry
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class TaskSwitcherOverlayController(
    internal val host: Host
) {
    private val motionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val taskSwitcherOverscrollMotion = OverlayFloatSpringMotion(motionScope)
    interface Host : TaskSwitcherLayoutHost {
        val context: Context
        fun settings(): AppSettings
        override fun side(): PanelSide
        fun appRepository(): AppRepository
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        fun panelContentRect(): RectF
        override fun activeTriggerZoneRect(): RectF
        override fun viewWidth(): Int
        override fun viewHeight(): Int
        override fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun density(): Float
        fun viewLocationOnScreen(): IntArray
        fun overlayView(): android.view.View? = null
        fun invalidate()
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticConfirmLaunch()
        fun iconFor(app: AppInfo): Bitmap
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun clearEdgeCaptureTouchActive()
        fun onInitiatingEdgeGestureReleased()
        fun setPanelPresentationFocus(needsFocus: Boolean)
    }

    private val renderer = TaskSwitcherRenderer(host)
    private val touchHandler = TaskSwitcherTouchHandler(this)

    internal var recentApps = mutableListOf<RecentAppEntry>()
    internal var taskSwitcherLayout: TaskSwitcherPanelLayout? = null
    internal var taskSwitcherRowHighlight = -1
    internal var taskSwitcherCloseHighlight = -1
    internal var taskSwitcherFreeWindowHighlight = -1
    internal var taskSwitcherCloseAllHighlight = false
    internal var taskSwitcherClosePressIndex = -1
    internal var taskSwitcherClosePressDownTime = 0L
    internal var taskSwitcherCloseLongPressTriggered = false
    internal var taskSwitcherCloseHapticIndex = -1
    internal var taskSwitcherContinuousHapticKey = -1
    internal var taskSwitcherCloseLongPressRunnable: Runnable? = null
    internal var taskSwitcherRowPressIndex = -1
    internal var taskSwitcherRowPressDownTime = 0L
    internal var taskSwitcherRowLongPressTriggered = false
    internal var taskSwitcherRowLongPressRunnable: Runnable? = null
    internal var taskSwitcherContextMenu: TaskSwitcherContextMenuLayout? = null
    internal var taskSwitcherMenuHighlight = -1
    internal var taskSwitcherMenuAwaitingRelease = false
    internal var taskSwitcherMenuEnterProgress = 1f
    internal var taskSwitcherMenuEnterAnimator: ValueAnimator? = null
    internal var taskSwitcherMenuDismissing = false
    internal var lastTaskSwitcherTouchX = 0f
    internal var lastTaskSwitcherTouchY = 0f
    internal var taskSwitcherLoadGeneration = 0
    internal var taskSwitcherAnchorRawY: Float? = null
    internal var taskSwitcherAnchorRawX: Float? = null
    internal var taskSwitcherExternalAnchor = false
    internal var taskSwitcherFrozenAnchorLocalY: Float? = null
    internal var taskSwitcherFrozenAnchorLocalX: Float? = null
    internal var taskSwitcherScrollOffset = 0f
    internal var taskSwitcherScrollDragging = false
    internal var taskSwitcherScrollDragStartY = 0f
    internal var taskSwitcherScrollDragStartOffset = 0f
    internal var taskSwitcherOverscrollOffset = 0f
    internal var taskSwitcherGestureScrolled = false
    internal var taskSwitcherExiting = false
    internal var taskSwitcherLoading = false
    internal var taskSwitcherPrivilegedAccess = false

    // 性能打点：打开 → 数据就绪 → 首帧带行。只在 logcat 里看，不参与业务逻辑。
    private var loadStartedAtMs = 0L
    private var dataReadyAtMs = 0L
    private var firstFrameLogged = false

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        touchHandler.handleTouch(event, localX, localY)

    fun draw(canvas: Canvas) {
        val layout = computeTaskSwitcherLayout()
        taskSwitcherLayout = layout
        if (!firstFrameLogged && dataReadyAtMs > 0L && layout.rows.isNotEmpty()) {
            firstFrameLogged = true
            Log.i(
                PERF_TAG,
                "panel first frame with rows: ${SystemClock.uptimeMillis() - loadStartedAtMs}ms " +
                    "after open (data ${dataReadyAtMs - loadStartedAtMs}ms), rows=${layout.rows.size}"
            )
        }
        host.panelContentRect().set(layout.panelRect)
        host.drawWithPanelEnterAnimation(canvas, layout.panelRect) {
            renderer.drawPanelContent(canvas, layout, taskSwitcherRenderState())
        }
    }

    internal fun loadTaskSwitcherApps(deferInvalidate: Boolean = false) {
        loadStartedAtMs = SystemClock.uptimeMillis()
        dataReadyAtMs = 0L
        firstFrameLogged = false
        taskSwitcherLayout = null
        touchHandler.clearTaskSwitcherPickHighlights()

        recentApps = mutableListOf()
        taskSwitcherPrivilegedAccess = TaskManagerUtil.peekPrivilegedAccess()
        taskSwitcherLoading = taskSwitcherPrivilegedAccess
        if (!deferInvalidate) {
            invalidateTaskSwitcherPanel()
        }

        if (!taskSwitcherPrivilegedAccess) {
            if (!deferInvalidate) {
                invalidateTaskSwitcherPanel()
            }
            return
        }

        val generation = ++taskSwitcherLoadGeneration
        RecentTasksLoader.refreshAsync(host.appRepository()) { fresh ->
            if (generation != taskSwitcherLoadGeneration) return@refreshAsync
            if (host.gestureSession().panelMode() != OverlayPanelMode.TASK_SWITCHER) return@refreshAsync
            dataReadyAtMs = SystemClock.uptimeMillis()
            Log.i(
                PERF_TAG,
                "data ready: ${dataReadyAtMs - loadStartedAtMs}ms entries=${fresh.size}"
            )
            if (taskSwitcherContextMenuActive()) {
                dismissTaskSwitcherContextMenu(immediate = true)
            }
            taskSwitcherLoading = false
            recentApps = fresh.toMutableList()
            taskSwitcherLayout = null
            val iconSize = host.dp(30f).toInt().coerceAtLeast(48)
            host.appRepository().warmLaunchIconBitmapsAsync(
                fresh.map { it.app.packageName },
                iconSize
            )
            invalidateTaskSwitcherPanel()
        }
    }

    internal fun computeTaskSwitcherLayout(): TaskSwitcherPanelLayout {
        val rowEntries = recentApps.map { TaskSwitcherRowEntry(it.app.packageName, it.taskId) }
        val (layout, offset) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rowEntries,
            scrollOffset = taskSwitcherScrollOffset,
            anchorLocalY = taskSwitcherAnchorLocalY(),
            anchorLocalX = taskSwitcherAnchorLocalX(),
            // 外部触发（全局触控 / 角轮盘 / 悬浮球）：面板跟触发高度走，不拉回触钮条。
            clampAnchorToTrigger = !taskSwitcherExternalAnchor,
        )
        taskSwitcherScrollOffset = offset
        return layout
    }

    internal fun invalidateTaskSwitcherPanel() {
        if (host.gestureSession().panelMode() == OverlayPanelMode.TASK_SWITCHER) {
            host.invalidate()
        }
    }

    internal fun endTaskSwitcherSession(
        runBeforeExit: Boolean = false,
        runAfter: (() -> Unit)? = null
    ) {
        if (taskSwitcherExiting) return
        dismissTaskSwitcherContextMenu(immediate = true)
        taskSwitcherExiting = true
        host.setPanelPresentationFocus(false)
        runAfter?.invoke()
        if (runBeforeExit) {
            taskSwitcherExiting = false
            host.gestureSession().endSession()
            return
        }
        host.startPanelExitAnimation {
            taskSwitcherExiting = false
            host.gestureSession().endSession()
        }
    }

    internal fun taskSwitcherContextMenuActive(): Boolean =
        taskSwitcherContextMenu != null && !taskSwitcherMenuDismissing

    internal fun dismissTaskSwitcherContextMenu(immediate: Boolean = false) {
        if (taskSwitcherContextMenu == null) return
        if (taskSwitcherMenuDismissing && !immediate) return
        clearTaskSwitcherMenuRowHighlight()
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuHighlight = -1
        taskSwitcherMenuAwaitingRelease = false
        taskSwitcherScrollDragging = false
        touchHandler.cancelTaskSwitcherScrollMotion()
        taskSwitcherOverscrollOffset = 0f
        if (immediate || taskSwitcherMenuEnterProgress <= 0f) {
            finishTaskSwitcherMenuDismiss()
            host.invalidate()
            return
        }
        startTaskSwitcherMenuExitAnimation()
    }

    internal fun taskSwitcherOverscrollEnabled(): Boolean =
        !host.gestureSession().taskSwitcherContinuousPickActive()

    internal fun shouldFreezeTaskSwitcherAnchor(): Boolean =
        host.gestureSession().panelMode() == OverlayPanelMode.TASK_SWITCHER

    fun setExternalAnchor(rawX: Float, rawY: Float) {
        taskSwitcherExternalAnchor = true
        taskSwitcherAnchorRawX = rawX
        taskSwitcherAnchorRawY = rawY
    }

    fun onSessionStart() {
        taskSwitcherFrozenAnchorLocalY = null
        taskSwitcherFrozenAnchorLocalX = null
        if (taskSwitcherAnchorRawY == null || taskSwitcherAnchorRawY == 0f) {
            taskSwitcherAnchorRawY = host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
        }
        if (taskSwitcherAnchorRawX == null) {
            // 横坐标 0 是合法值（屏幕最左），不能照搬 Y 那边的 `> 0` 过滤，
            // 否则贴着左边缘的底部触钮会丢掉锚点、退回"锁死屏幕中间"。
            taskSwitcherAnchorRawX = host.pathRecognizer().gestureStartRawX()
        }
        taskSwitcherLayout = null
        loadTaskSwitcherApps(deferInvalidate = false)
    }

    fun onLayoutReady() {
        taskSwitcherLayout = null
        taskSwitcherFrozenAnchorLocalY = resolveTaskSwitcherAnchorLocalY()
        taskSwitcherFrozenAnchorLocalX = resolveTaskSwitcherAnchorLocalX()
        if (!host.gestureSession().taskSwitcherContinuousPickActive() &&
            !host.gestureSession().isMoveTimeActionLocked()
        ) {
            host.setPanelPresentationFocus(true)
        }
    }

    fun handleBackPress(): Boolean {
        if (host.gestureSession().panelMode() != OverlayPanelMode.TASK_SWITCHER) return false
        if (taskSwitcherContextMenuActive()) {
            dismissTaskSwitcherContextMenu()
            return true
        }
        endTaskSwitcherSession()
        return true
    }

    fun onSessionEnd() {
        taskSwitcherLoadGeneration++
        taskSwitcherLayout = null
        touchHandler.clearTaskSwitcherPickHighlights()
        taskSwitcherAnchorRawY = null
        taskSwitcherAnchorRawX = null
        taskSwitcherExternalAnchor = false
        taskSwitcherFrozenAnchorLocalY = null
        taskSwitcherFrozenAnchorLocalX = null
        taskSwitcherScrollOffset = 0f
        taskSwitcherScrollDragging = false
        taskSwitcherOverscrollOffset = 0f
        touchHandler.cancelTaskSwitcherScrollMotion()
        taskSwitcherGestureScrolled = false
        taskSwitcherExiting = false
        dismissTaskSwitcherContextMenu(immediate = true)
        touchHandler.cancelTaskSwitcherCloseLongPress()
        touchHandler.cancelTaskSwitcherRowLongPress()
        host.setPanelPresentationFocus(false)
    }

    private fun taskSwitcherRenderState() = TaskSwitcherRenderState(
        loading = taskSwitcherLoading,
        privilegedAccess = taskSwitcherPrivilegedAccess,
        recentEntries = recentApps,
        rowHighlight = taskSwitcherRowHighlight,
        closeHighlight = taskSwitcherCloseHighlight,
        freeWindowHighlight = taskSwitcherFreeWindowHighlight,
        closeAllHighlight = taskSwitcherCloseAllHighlight,
        overscrollOffset = taskSwitcherOverscrollOffset,
        overscrollEnabled = taskSwitcherOverscrollEnabled(),
        contextMenuActive = taskSwitcherContextMenuActive(),
        contextMenu = taskSwitcherContextMenu,
        menuHighlight = taskSwitcherMenuHighlight,
        menuEnterProgress = taskSwitcherMenuEnterProgress
    )

    private fun resolveTaskSwitcherAnchorLocalY(): Float {
        val rawY = taskSwitcherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        if (taskSwitcherExternalAnchor) {
            val minY = host.dp(16f)
            val maxY = (host.viewHeight() - host.dp(16f)).coerceAtLeast(minY)
            return anchorY.coerceIn(minY, maxY)
        }
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun taskSwitcherAnchorLocalY(): Float =
        taskSwitcherFrozenAnchorLocalY ?: resolveTaskSwitcherAnchorLocalY()

    /**
     * 触发点横坐标（本地）。上下侧触钮用它当面板水平中心，左右侧不读这个值。
     *
     * 与 Y 一样在布局就绪时冻结：否则面板打开后手指继续滑动，面板会横向漂移。
     * 没有手指坐标（理论上 onSessionStart 已补上）时回退屏幕中心。
     */
    private fun resolveTaskSwitcherAnchorLocalX(): Float {
        val loc = host.viewLocationOnScreen()
        val rawX = taskSwitcherAnchorRawX ?: (loc[0] + host.viewWidth() / 2f)
        val anchorX = rawX - loc[0]
        val minX = 0f
        val maxX = host.viewWidth().toFloat().coerceAtLeast(minX)
        return anchorX.coerceIn(minX, maxX)
    }

    private fun taskSwitcherAnchorLocalX(): Float =
        taskSwitcherFrozenAnchorLocalX ?: resolveTaskSwitcherAnchorLocalX()

    private fun clearTaskSwitcherMenuRowHighlight() {
        if (host.gestureSession().taskSwitcherContinuousPickActive()) return
        val menuRowIndex = taskSwitcherContextMenu?.rowIndex ?: -1
        if (menuRowIndex >= 0 && taskSwitcherRowHighlight == menuRowIndex) {
            taskSwitcherRowHighlight = -1
        }
        taskSwitcherRowLongPressTriggered = false
    }

    private fun finishTaskSwitcherMenuDismiss() {
        taskSwitcherContextMenu = null
        taskSwitcherMenuDismissing = false
        taskSwitcherMenuEnterProgress = 1f
    }

    private fun cancelTaskSwitcherMenuAnimation() {
        taskSwitcherMenuEnterAnimator?.removeAllListeners()
        taskSwitcherMenuEnterAnimator?.cancel()
        taskSwitcherMenuEnterAnimator = null
    }

    private fun startTaskSwitcherMenuExitAnimation() {
        cancelTaskSwitcherMenuAnimation()
        taskSwitcherMenuDismissing = true
        val startProgress = taskSwitcherMenuEnterProgress.coerceIn(0f, 1f)
        taskSwitcherMenuEnterAnimator = ValueAnimator.ofFloat(startProgress, 0f).apply {
            duration = (TASK_SWITCHER_MENU_EXIT_MS * startProgress).toLong().coerceAtLeast(1L)
            interpolator = AccelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                taskSwitcherMenuEnterProgress = animator.animatedValue as Float
                host.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    taskSwitcherMenuEnterAnimator = null
                    finishTaskSwitcherMenuDismiss()
                    host.invalidate()
                }
            })
            start()
        }
        host.invalidate()
    }

    internal fun collectAccessibilityNodes(context: android.content.Context): List<OverlayVirtualNode> {
        val layout = taskSwitcherLayout ?: return emptyList()
        val localized = AppLocaleApplier.wrapOverlayContext(context)
        val nodes = mutableListOf<OverlayVirtualNode>()
        layout.rows.forEachIndexed { index, row ->
            val entry = recentApps.getOrNull(index) ?: return@forEachIndexed
            val label = entry.app.label
            nodes += OverlayVirtualNode(
                description = localized.getString(R.string.cd_overlay_task_switch_row, label),
                boundsInParent = RectF(row.rowRect)
            )
            nodes += OverlayVirtualNode(
                description = localized.getString(R.string.cd_overlay_task_switch_close, label),
                boundsInParent = RectF(row.closeRect)
            )
            nodes += OverlayVirtualNode(
                description = localized.getString(R.string.cd_overlay_task_switch_free_window, label),
                boundsInParent = RectF(row.freeWindowRect)
            )
        }
        if (!layout.closeAllRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = localized.getString(R.string.task_switcher_close_all),
                boundsInParent = RectF(layout.closeAllRect)
            )
        }
        return nodes
    }

    companion object {
        private const val PERF_TAG = "TaskSwitcherPerf"
        internal const val TASK_SWITCHER_LONG_PRESS_MS = 650L
        internal const val TASK_SWITCHER_MENU_ENTER_MS = 200L
        internal const val TASK_SWITCHER_MENU_EXIT_MS = 160L
        internal const val TASK_SWITCHER_CLOSE_DWELL_MS = 750L
        internal const val TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS = 1_100L
        internal const val TASK_SWITCHER_SCROLL_SLOP_DP = 8f
        internal const val TASK_SWITCHER_OVERSCROLL_MAX_DP = 52f
        internal const val TASK_SWITCHER_OVERSCROLL_RESISTANCE = 0.36f
        internal const val TASK_SWITCHER_OVERSCROLL_STRETCH = 0.22f
        internal const val TASK_SWITCHER_OVERSCROLL_RELEASE_MS = 280L
    }
}
