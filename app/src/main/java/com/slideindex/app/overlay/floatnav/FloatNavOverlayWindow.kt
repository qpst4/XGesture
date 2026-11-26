package com.slideindex.app.overlay.floatnav

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.overlayHostContext
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandleEdgeWidthDp
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 浮动导航按钮条的窗口控制器。
 *
 * 关键约束：持续触发时，手指的 DOWN 已经被边缘捕获窗口锁定，浮条自己收不到后续 MOVE/UP。
 * 因此持续选择完全由 [GestureSession] 把 raw 坐标喂进来（[updatePointer]），松手由
 * [confirmTrackingRelease] 按坐标命中；浮条窗口始终可触摸，只负责正常点按和拖动。
 */
@SuppressLint("StaticFieldLeak", "RtlHardcoded") // 浮层单例：dismiss/宿主重建时清空 View 与 Context 引用。
object FloatNavOverlayWindow {

    private const val TAG = "FloatNav"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FloatNavView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hostContext: Context? = null
    private var settings: AppSettings? = null
    private var side: PanelSide = PanelSide.RIGHT
    private var form: FloatNavForm = FloatNavForm.HORIZONTAL
    private var expandForward = true

    private var tracking = false
    private var armed = false
    private var restoreCollapsedAfterSession = false
    private var toggleAnimator: ValueAnimator? = null
    private var expandedBarWidth = 0
    private var expandedBarHeight = 0
    private var updatingDirection = false
    private var showRawX = 0f
    private var showRawY = 0f
    private var armSlopPx = 24f

    private var screenWidth = 1
    private var screenHeight = 1
    private var edgeInsetPx = 0
    private var topInsetPx = 0
    private var bottomInsetPx = 0

    val isShowing: Boolean get() = root?.isAttachedToWindow == true
    val isTracking: Boolean get() = tracking && isShowing

    /**
     * @param continuousTracking true = 持续触发：进入跟手选择态；false = 松手/直接触发：常驻显示。
     * @param toggleIfVisible 离散触发时，已显示则收起（oneHandX 的 toggle 语义）；持续触发忽略。
     */
    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        side: PanelSide?,
        continuousTracking: Boolean,
        toggleIfVisible: Boolean,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                show(context, settings, anchorRawX, anchorRawY, side, continuousTracking, toggleIfVisible)
            }
            return true
        }
        if (isShowing) {
            this.settings = settings
            this.side = side ?: this.side
            root?.let { view ->
                view.updateSettings(settings)
                if (!view.isCollapsed) {
                    expandedBarWidth = view.width
                    expandedBarHeight = view.height
                }
            }
            if (continuousTracking) {
                val desiredExpandForward = computeExpandForward(this.side, anchorRawX, anchorRawY, form)
                if (desiredExpandForward != expandForward) {
                    expandForward = desiredExpandForward
                    root?.setExpandForward(desiredExpandForward)
                }
                moveToAnchor(anchorRawX, anchorRawY)
                startTracking(anchorRawX, anchorRawY)
                return true
            }
            if (toggleIfVisible) {
                dismiss()
                return true
            }
            bringToFront()
            return true
        }

        restoreCollapsedAfterSession = false
        val host = overlayHostContext() ?: return false
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(host)) return false
        val wm = host.getSystemService(WindowManager::class.java) ?: return false
        val metrics = OverlayScreenMetrics.sizePx(wm, host.resources.displayMetrics)
        screenWidth = metrics.first.coerceAtLeast(1)
        screenHeight = metrics.second.coerceAtLeast(1)
        val density = host.resources.displayMetrics.density
        val resolvedSide = side ?: inferSide(anchorRawX, anchorRawY)
        val resolvedForm = FloatNavPrefs.resolveInitialForm(host)
        val resolvedExpandForward = computeExpandForward(
            resolvedSide,
            anchorRawX,
            anchorRawY,
            resolvedForm,
        )
        val edgeInset = computeEdgeInset(settings, resolvedSide, density)
        val systemInsets = computeSystemInsets(wm, density, resolvedSide, edgeInset)
        edgeInsetPx = edgeInset
        topInsetPx = systemInsets.first
        bottomInsetPx = systemInsets.second
        this.side = resolvedSide
        this.form = resolvedForm
        this.expandForward = resolvedExpandForward
        this.settings = settings
        hostContext = host

        val view = FloatNavView(
            context = host,
            appSettings = settings,
            form = resolvedForm,
            expandForward = resolvedExpandForward,
            listener = viewListener,
        )
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        val barWidth = view.measuredWidth.coerceAtLeast(1)
        val barHeight = view.measuredHeight.coerceAtLeast(1)
        expandedBarWidth = barWidth
        expandedBarHeight = barHeight
        val position = positionFor(
            side = resolvedSide,
            anchorX = anchorRawX ?: (screenWidth / 2f),
            anchorY = anchorRawY ?: (screenHeight / 2f),
            barWidth = barWidth,
            barHeight = barHeight,
            density = density,
            collapsed = false,
        )
        val lp = buildLayoutParams(host, position, barWidth, barHeight)
        return runCatching {
            wm.addView(view, lp)
            root = view
            windowManager = wm
            layoutParams = lp
            if (continuousTracking) {
                startTracking(anchorRawX, anchorRawY)
            } else {
                tracking = false
                armed = false
            }
            Log.i(TAG, "shown side=$resolvedSide at (${position.x},${position.y}) ${barWidth}x$barHeight")
            true
        }.getOrElse { error ->
            Log.w(TAG, "show failed", error)
            root = null
            windowManager = null
            layoutParams = null
            hostContext = null
            false
        }
    }

    /** 非边缘入口（快捷启动器槽位、悬浮球、动作中心测试）的开关语义。 */
    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        side: PanelSide?,
    ): Boolean {
        if (isShowing) {
            dismiss()
            return true
        }
        return show(
            context = context,
            settings = settings,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY,
            side = side,
            continuousTracking = false,
            toggleIfVisible = false,
        )
    }

    /** 设置/主题变化：更新主题色与材质，不重建窗口。 */
    fun onSettingsChanged(settings: AppSettings) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onSettingsChanged(settings) }
            return
        }
        this.settings = settings
        root?.let { view ->
            view.updateSettings(settings)
            if (!view.isCollapsed) {
                expandedBarWidth = view.width
                expandedBarHeight = view.height
            }
        }
    }

    /** 持续触发：边滑会话喂入的 raw 坐标，负责 arming 与高亮。 */
    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        if (!isTracking) return
        if (!armed) {
            if (hypot(rawX - showRawX, rawY - showRawY) < armSlopPx) {
                root?.clearHighlight()
                return
            }
            armed = true
        }
        val key = root?.keyAt(rawX, rawY) ?: FloatNavKey.NONE
        root?.setHoveredKey(if (key == FloatNavKey.GRIP) FloatNavKey.NONE else key)
    }

    /** 持续触发：松手确认。返回 true 表示执行了导航键或关闭了浮条。 */
    fun confirmTrackingRelease(rawX: Float, rawY: Float): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var handled = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                handled = confirmTrackingRelease(rawX, rawY)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return handled
        }
        if (!isTracking) return false
        val key = if (armed) root?.keyAt(rawX, rawY) ?: FloatNavKey.NONE else FloatNavKey.NONE
        root?.clearHighlight()
        tracking = false
        armed = false
        return when (key) {
            FloatNavKey.BACK -> execute(GestureAction.Back)
            FloatNavKey.HOME -> execute(GestureAction.Home)
            FloatNavKey.RECENTS -> execute(GestureAction.Recents)
            FloatNavKey.CLOSE -> {
                dismiss()
                true
            }
            FloatNavKey.GRIP, FloatNavKey.NONE -> false
        }
    }

    /** 会话结束：只清跟手态，浮条常驻；若本次是为了选择而临时展开，恢复收起态。 */
    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        tracking = false
        armed = false
        root?.clearHighlight()
        if (restoreCollapsedAfterSession) {
            restoreCollapsedAfterSession = false
            root?.setCollapsed(true)
        }
    }

    /** 取消/丢失 UP：只清跟手态，不执行、不收起。 */
    fun cancelTracking() {
        onGestureSessionEnd()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        val view = root
        val wm = windowManager
        toggleAnimator?.cancel()
        toggleAnimator = null
        view?.setAnimating(false)
        root = null
        windowManager = null
        layoutParams = null
        hostContext = null
        settings = null
        tracking = false
        armed = false
        restoreCollapsedAfterSession = false
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        Log.i(TAG, "dismissed")
    }

    /** 旋转/分屏/折叠：按当前中心重新贴边，并重新套材质。 */
    fun onConfigurationChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onConfigurationChanged() }
            return
        }
        val host = hostContext ?: return
        val wm = windowManager ?: return
        val view = root ?: return
        val lp = layoutParams ?: return
        val oldScreenWidth = screenWidth
        val oldScreenHeight = screenHeight
        val currentLeft = if (form == FloatNavForm.HORIZONTAL) {
            if (expandForward) lp.x else oldScreenWidth - lp.x - view.width.coerceAtLeast(1)
        } else {
            lp.x
        }
        val currentTop = if (form == FloatNavForm.HORIZONTAL) {
            lp.y
        } else {
            if (expandForward) lp.y else oldScreenHeight - lp.y - view.height.coerceAtLeast(1)
        }
        val metrics = OverlayScreenMetrics.sizePx(wm, host.resources.displayMetrics)
        screenWidth = metrics.first.coerceAtLeast(1)
        screenHeight = metrics.second.coerceAtLeast(1)
        val density = host.resources.displayMetrics.density
        val currentSettings = settings ?: AppSettings()
        val resolvedSide = side
        val edgeInset = computeEdgeInset(currentSettings, resolvedSide, density)
        val systemInsets = computeSystemInsets(wm, density, resolvedSide, edgeInset)
        edgeInsetPx = edgeInset
        topInsetPx = systemInsets.first
        bottomInsetPx = systemInsets.second
        val gripOffset = gripOffsetPx(density, view.isCollapsed)
        val gripCenterX = if (form == FloatNavForm.HORIZONTAL) {
            if (expandForward) {
                currentLeft + gripOffset
            } else {
                currentLeft + view.width.coerceAtLeast(1) - gripOffset
            }
        } else {
            currentLeft + view.width.coerceAtLeast(1) / 2f
        }
        val gripCenterY = if (form == FloatNavForm.VERTICAL) {
            if (expandForward) {
                currentTop + gripOffset
            } else {
                currentTop + view.height.coerceAtLeast(1) - gripOffset
            }
        } else {
            currentTop + view.height.coerceAtLeast(1) / 2f
        }
        val position = positionFor(
            side = resolvedSide,
            anchorX = gripCenterX,
            anchorY = gripCenterY,
            barWidth = view.width.coerceAtLeast(1),
            barHeight = view.height.coerceAtLeast(1),
            density = density,
            collapsed = view.isCollapsed,
        )
        applyWindowPosition(lp, view, position)
        runCatching { wm.updateViewLayout(view, lp) }
        if (!view.isCollapsed) {
            expandedBarWidth = view.width
            expandedBarHeight = view.height
        }
        view.applyTheme()
    }

    /**
     * 底/顶边：把把柄中心放到触发点 X，按钮条朝屏幕中心展开。
     * 左/右边缘仍由 [FloatNavGeometry.computePosition] 贴边，X 不参与。
     */
    private fun positionFor(
        side: PanelSide,
        anchorX: Float,
        anchorY: Float,
        barWidth: Int,
        barHeight: Int,
        density: Float,
        collapsed: Boolean,
    ): FloatNavPosition {
        if (form == FloatNavForm.VERTICAL) {
            val minX = edgeInsetPx
            val maxX = (screenWidth - barWidth - edgeInsetPx).coerceAtLeast(minX)
            val minY = topInsetPx
            val maxY = (screenHeight - barHeight - bottomInsetPx).coerceAtLeast(minY)
            val offset = gripOffsetPx(density, collapsed)
            return when (side) {
                PanelSide.LEFT -> FloatNavPosition(
                    x = minX,
                    y = (if (expandForward) anchorY - offset else anchorY - barHeight + offset)
                        .roundToInt()
                        .coerceIn(minY, maxY),
                )
                PanelSide.RIGHT -> FloatNavPosition(
                    x = maxX,
                    y = (if (expandForward) anchorY - offset else anchorY - barHeight + offset)
                        .roundToInt()
                        .coerceIn(minY, maxY),
                )
                PanelSide.TOP -> FloatNavPosition(
                    x = (anchorX - barWidth / 2f).roundToInt().coerceIn(minX, maxX),
                    y = minY,
                )
                PanelSide.BOTTOM -> FloatNavPosition(
                    x = (anchorX - barWidth / 2f).roundToInt().coerceIn(minX, maxX),
                    y = maxY,
                )
            }
        }
        val base = FloatNavGeometry.computePosition(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            barWidthPx = barWidth,
            barHeightPx = barHeight,
            side = side,
            anchorX = anchorX,
            anchorY = anchorY,
            edgeInsetPx = edgeInsetPx,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        )
        if (side != PanelSide.BOTTOM && side != PanelSide.TOP) return base
        val offset = gripOffsetPx(density, collapsed)
        val rawX = if (expandForward) anchorX - offset else anchorX - barWidth + offset
        val minX = edgeInsetPx
        val maxX = (screenWidth - barWidth - edgeInsetPx).coerceAtLeast(minX)
        return base.copy(x = rawX.roundToInt().coerceIn(minX, maxX))
    }

    /** 把柄中心到窗口展开侧边缘的距离：展开时 22dp，收起时 28dp。 */
    private fun gripOffsetPx(density: Float, collapsed: Boolean): Float =
        (if (collapsed) 28f else 22f) * density

    /**
     * 窗口 x 语义：
     * - 向右展开：START，lp.x = 左边缘；
     * - 向左展开：END，lp.x = 距离屏幕右边缘的偏移。
     * 这样宽度变化时由 WindowManager 保持锚定边，不会先长出去再跳回来。
     */
    private fun windowLeft(lp: WindowManager.LayoutParams, width: Int): Int =
        if (form == FloatNavForm.HORIZONTAL) {
            if (expandForward) lp.x else screenWidth - lp.x - width.coerceAtLeast(1)
        } else {
            lp.x
        }

    private fun windowTop(lp: WindowManager.LayoutParams, height: Int): Int =
        if (form == FloatNavForm.HORIZONTAL) {
            lp.y
        } else {
            if (expandForward) lp.y else screenHeight - lp.y - height.coerceAtLeast(1)
        }

    private fun applyWindowPosition(
        lp: WindowManager.LayoutParams,
        view: View,
        position: FloatNavPosition,
    ) {
        if (form == FloatNavForm.HORIZONTAL) {
            lp.gravity = Gravity.TOP or if (expandForward) Gravity.LEFT else Gravity.RIGHT
            lp.x = if (expandForward) {
                position.x
            } else {
                screenWidth - position.x - view.width.coerceAtLeast(1)
            }
            lp.y = position.y
        } else {
            lp.gravity = (if (expandForward) Gravity.TOP else Gravity.BOTTOM) or Gravity.LEFT
            lp.x = position.x
            lp.y = if (expandForward) {
                position.y
            } else {
                screenHeight - position.y - view.height.coerceAtLeast(1)
            }
        }
    }

    private fun startTracking(anchorRawX: Float?, anchorRawY: Float?) {
        val host = hostContext ?: return
        // 收起态没有导航键可落；持续触发先自动展开，会话结束后再恢复收起。
        root?.let { view ->
            if (view.isCollapsed) {
                updateDirectionFromGrip(view)
                restoreCollapsedAfterSession = true
                view.setCollapsed(false)
            }
        }
        tracking = true
        armed = false
        showRawX = anchorRawX ?: (screenWidth / 2f)
        showRawY = anchorRawY ?: (screenHeight / 2f)
        armSlopPx = ViewConfiguration.get(host).scaledTouchSlop * 2f
        root?.clearHighlight()
    }

    private fun execute(action: GestureAction): Boolean {
        val ok = SlideIndexAccessibilityService.perform(action)
        if (ok) {
            root?.let { view -> settings?.let { HapticHelper.confirmLaunch(view, it) } }
        } else {
            Log.w(TAG, "perform($action) failed")
        }
        return ok
    }

    private fun moveToAnchor(anchorRawX: Float?, anchorRawY: Float?) {
        val view = root ?: return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val host = hostContext ?: return
        val density = host.resources.displayMetrics.density
        val currentSettings = settings ?: AppSettings()
        val edgeInset = computeEdgeInset(currentSettings, side, density)
        val systemInsets = computeSystemInsets(wm, density, side, edgeInset)
        edgeInsetPx = edgeInset
        topInsetPx = systemInsets.first
        bottomInsetPx = systemInsets.second
        val position = positionFor(
            side = side,
            anchorX = anchorRawX ?: (windowLeft(lp, view.width) + view.width / 2f),
            anchorY = anchorRawY ?: lp.y + view.height / 2f,
            barWidth = view.width.coerceAtLeast(1),
            barHeight = view.height.coerceAtLeast(1),
            density = density,
            collapsed = view.isCollapsed,
        )
        applyWindowPosition(lp, view, position)
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun bringToFront() {
        val view = root ?: return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val view = root ?: return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        if (dx == 0f && dy == 0f) return
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val minX = edgeInsetPx
        val maxX = (screenWidth - width - edgeInsetPx).coerceAtLeast(minX)
        val minY = topInsetPx
        val maxY = (screenHeight - height - bottomInsetPx).coerceAtLeast(minY)
        val left = (windowLeft(lp, width) + dx.roundToInt()).coerceIn(minX, maxX)
        val top = (windowTop(lp, height) + dy.roundToInt()).coerceIn(minY, maxY)
        if (form == FloatNavForm.HORIZONTAL) {
            lp.x = if (expandForward) left else screenWidth - left - width
            lp.y = top
        } else {
            lp.x = left
            lp.y = if (expandForward) top else screenHeight - top - height
        }
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private val viewListener = object : FloatNavView.Listener {
        override fun onKey(key: FloatNavKey) {
            if (tracking) return
            when (key) {
                FloatNavKey.BACK -> execute(GestureAction.Back)
                FloatNavKey.HOME -> execute(GestureAction.Home)
                FloatNavKey.RECENTS -> execute(GestureAction.Recents)
                else -> Unit
            }
        }

        override fun onClose() {
            if (tracking) return
            dismiss()
        }

        override fun onGripClick() {
            if (tracking || toggleAnimator?.isRunning == true) return
            val view = root ?: return
            updateDirectionFromGrip(view)
            animateToggleCollapsed(view)
            settings?.let { HapticHelper.confirmLaunch(view, it) }
        }

        override fun onGripLongPress() {
            toggleFormFromGrip()
        }

        override fun onDragStart() = Unit

        override fun onDragBy(dx: Float, dy: Float) {
            if (tracking || toggleAnimator?.isRunning == true) return
            moveBy(dx, dy)
        }

        override fun onDragEnd() {
            if (tracking) return
            val view = root ?: return
            if (view.isCollapsed) updateDirectionFromGrip(view)
        }
    }

    /**
     * 收起态长按把柄切换横/竖；窗口仍是 56×56，只换内部布局和下一帧的展开方向。
     */
    private fun toggleFormFromGrip() {
        if (tracking || toggleAnimator?.isRunning == true) return
        val view = root ?: return
        if (!view.isCollapsed) return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val host = hostContext ?: return
        val density = host.resources.displayMetrics.density
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val centerX = windowLeft(lp, width) + width / 2f
        val centerY = windowTop(lp, height) + height / 2f
        val nextForm = if (form == FloatNavForm.HORIZONTAL) {
            FloatNavForm.VERTICAL
        } else {
            FloatNavForm.HORIZONTAL
        }
        val nextForward = if (nextForm == FloatNavForm.HORIZONTAL) {
            when (side) {
                PanelSide.LEFT -> true
                PanelSide.RIGHT -> false
                PanelSide.TOP, PanelSide.BOTTOM -> centerX < screenWidth / 2f
            }
        } else {
            when (side) {
                PanelSide.TOP -> true
                PanelSide.BOTTOM -> false
                PanelSide.LEFT, PanelSide.RIGHT -> centerY < screenHeight / 2f
            }
        }
        form = nextForm
        expandForward = nextForward
        view.setForm(nextForm, nextForward)
        val (expandedWidth, expandedHeight) = view.measureExpandedSizePx()
        expandedBarWidth = expandedWidth
        expandedBarHeight = expandedHeight
        val position = positionFor(
            side = side,
            anchorX = centerX,
            anchorY = centerY,
            barWidth = view.width.coerceAtLeast(1),
            barHeight = view.height.coerceAtLeast(1),
            density = density,
            collapsed = true,
        )
        applyWindowPosition(lp, view, position)
        runCatching { wm.updateViewLayout(view, lp) }
        FloatNavPrefs.writeLastForm(host, nextForm)
        settings?.let { HapticHelper.appTick(view, it) }
    }

    /**
     * 收起态拖到对侧/另一半屏后，按把柄当前位置重算展开方向，并保持当前左上角不变。
     */
    private fun updateDirectionFromGrip(view: FloatNavView) {
        if (updatingDirection || toggleAnimator?.isRunning == true) return
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val host = hostContext ?: return
        updatingDirection = true
        try {
            val density = host.resources.displayMetrics.density
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            val left = windowLeft(lp, width)
            val top = windowTop(lp, height)
            val offset = gripOffsetPx(density, view.isCollapsed)
            val gripCenterX = if (form == FloatNavForm.HORIZONTAL) {
                if (expandForward) left + offset else left + width - offset
            } else {
                left + width / 2f
            }
            val gripCenterY = if (form == FloatNavForm.VERTICAL) {
                if (expandForward) top + offset else top + height - offset
            } else {
                top + height / 2f
            }
            val desiredSide = when {
                gripCenterY > screenHeight * 0.8f -> PanelSide.BOTTOM
                gripCenterY < screenHeight * 0.2f -> PanelSide.TOP
                gripCenterX < screenWidth / 2f -> PanelSide.LEFT
                else -> PanelSide.RIGHT
            }
            val desiredExpandForward = if (form == FloatNavForm.HORIZONTAL) {
                when (desiredSide) {
                    PanelSide.LEFT -> true
                    PanelSide.RIGHT -> false
                    PanelSide.TOP, PanelSide.BOTTOM -> gripCenterX < screenWidth / 2f
                }
            } else {
                when (desiredSide) {
                    PanelSide.TOP -> true
                    PanelSide.BOTTOM -> false
                    PanelSide.LEFT, PanelSide.RIGHT -> gripCenterY < screenHeight / 2f
                }
            }
            if (desiredSide == side && desiredExpandForward == expandForward) return

            // 先更新字段和锚点，再重排；重排是 post 的，不会再在 touch dispatch 中重入。
            side = desiredSide
            expandForward = desiredExpandForward
            applyWindowPosition(lp, view, FloatNavPosition(left, top))
            runCatching { wm.updateViewLayout(view, lp) }
            view.setExpandForward(desiredExpandForward)
        } finally {
            updatingDirection = false
        }
    }

    /**
     * 展开/收起动画：固定锚定边，宽度从当前值平滑到目标值；子 View 在窗口变宽/变窄时
     * 自动被裁剪，看起来像从把柄处展开或收回。
     */
    private fun animateToggleCollapsed(view: FloatNavView) {
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val host = hostContext ?: return
        if (toggleAnimator?.isRunning == true) return
        val collapsing = !view.isCollapsed
        val vertical = form == FloatNavForm.VERTICAL
        val startWidth = view.width.coerceAtLeast(1)
        val startHeight = view.height.coerceAtLeast(1)
        val density = host.resources.displayMetrics.density
        val fallback = (248f * density).roundToInt()
        val targetWidth = if (vertical) {
            startWidth
        } else if (collapsing) {
            view.collapsedWidthPx()
        } else {
            expandedBarWidth.coerceAtLeast(fallback)
        }
        val targetHeight = if (!vertical) {
            startHeight
        } else if (collapsing) {
            view.collapsedHeightPx()
        } else {
            expandedBarHeight.coerceAtLeast(fallback)
        }
        val start = if (vertical) startHeight else startWidth
        val target = if (vertical) targetHeight else targetWidth
        if (target <= 0 || target == start) {
            view.setCollapsed(collapsing)
            return
        }
        if (collapsing && start <= target) return
        if (!collapsing && start >= target) return

        // 先把窗口尺寸锁到当前值，避免 setCollapsed 触发 WRAP_CONTENT 立即跳变。
        lp.width = startWidth
        lp.height = startHeight
        runCatching { wm.updateViewLayout(view, lp) }
        view.setAnimating(true)
        // 收起：先只留把柄，再让窗口向把柄收；展开：先让窗口从把柄长出去，结束时再显示按键。
        if (collapsing) view.setCollapsed(true)

        val animator = ValueAnimator.ofInt(start, target).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                if (vertical) {
                    lp.height = value
                } else {
                    lp.width = value
                }
                runCatching { wm.updateViewLayout(view, lp) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (toggleAnimator !== animation) return
                    toggleAnimator = null
                    if (!collapsing) view.setCollapsed(false)
                    lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                    lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                    view.setAnimating(false)
                    view.applyMaterial()
                    runCatching { wm.updateViewLayout(view, lp) }
                    if (!collapsing) {
                        expandedBarWidth = targetWidth
                        expandedBarHeight = targetHeight
                    }
                }
            })
        }
        toggleAnimator = animator
        animator.start()
    }

    private fun buildLayoutParams(
        host: Context,
        position: FloatNavPosition,
        barWidth: Int,
        barHeight: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        OverlayWindowTypes.overlayWindowType(host),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (form == FloatNavForm.HORIZONTAL) {
            gravity = Gravity.TOP or if (expandForward) Gravity.LEFT else Gravity.RIGHT
            x = if (expandForward) {
                position.x
            } else {
                screenWidth - position.x - barWidth.coerceAtLeast(1)
            }
            y = position.y
        } else {
            gravity = (if (expandForward) Gravity.TOP else Gravity.BOTTOM) or Gravity.LEFT
            x = position.x
            y = if (expandForward) {
                position.y
            } else {
                screenHeight - position.y - barHeight.coerceAtLeast(1)
            }
        }
        alpha = 1f
        title = "XG:float-nav"
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        OverlayWindowTypes.ensureNoBrightnessOverride(this)
    }

    private fun inferSide(anchorRawX: Float?, anchorRawY: Float?): PanelSide {
        if (anchorRawX == null || anchorRawY == null) return PanelSide.RIGHT
        return when {
            anchorRawY > screenHeight * 0.78f -> PanelSide.BOTTOM
            anchorRawY < screenHeight * 0.22f -> PanelSide.TOP
            anchorRawX < screenWidth / 2f -> PanelSide.LEFT
            else -> PanelSide.RIGHT
        }
    }

    /** 展开方向朝屏幕中心：横向看左/右半边，竖向看上/下半边。 */
    private fun computeExpandForward(
        side: PanelSide,
        anchorRawX: Float?,
        anchorRawY: Float?,
        form: FloatNavForm,
    ): Boolean = if (form == FloatNavForm.HORIZONTAL) {
        when (side) {
            PanelSide.LEFT -> true
            PanelSide.RIGHT -> false
            PanelSide.TOP, PanelSide.BOTTOM -> {
                val x = anchorRawX ?: (screenWidth / 2f)
                x < screenWidth / 2f
            }
        }
    } else {
        when (side) {
            PanelSide.TOP -> true
            PanelSide.BOTTOM -> false
            PanelSide.LEFT, PanelSide.RIGHT -> {
                val y = anchorRawY ?: (screenHeight / 2f)
                y < screenHeight / 2f
            }
        }
    }

    private fun computeEdgeInset(
        settings: AppSettings,
        side: PanelSide,
        density: Float,
    ): Int {
        val handleWidthDp = settings.triggerHandles(side)
            .maxOfOrNull { settings.triggerHandleEdgeWidthDp(side, it.id) }
            ?: 16f
        return (max(handleWidthDp, 12f) * density).roundToInt() + (8f * density).roundToInt()
    }

    /** @return (topInset, bottomInset)，已经包含状态栏/导航栏与 8dp 呼吸位。 */
    private fun computeSystemInsets(
        wm: WindowManager,
        density: Float,
        side: PanelSide,
        edgeInset: Int,
    ): Pair<Int, Int> {
        val insets = runCatching { wm.currentWindowMetrics.windowInsets }.getOrNull()
        val status = insets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            ?.top ?: 0
        val nav = insets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            ?.bottom ?: 0
        val cutout = insets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
        val gap = (8f * density).roundToInt()
        val top = max(status, cutout?.top ?: 0) + gap
        val bottom = max(nav, cutout?.bottom ?: 0) + gap
        val horizontalGap = max(edgeInset, max(cutout?.left ?: 0, cutout?.right ?: 0) + gap)
        return when (side) {
            PanelSide.TOP -> max(top, horizontalGap) to bottom
            PanelSide.BOTTOM -> top to max(bottom, horizontalGap)
            else -> top to bottom
        }
    }
}
