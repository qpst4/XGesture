package com.slideindex.app.overlay.floatnav

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
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
@SuppressLint("StaticFieldLeak") // 浮层单例：dismiss/宿主重建时清空 View 与 Context 引用。
object FloatNavOverlayWindow {

    private const val TAG = "FloatNav"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FloatNavView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hostContext: Context? = null
    private var settings: AppSettings? = null
    private var side: PanelSide = PanelSide.RIGHT

    private var tracking = false
    private var armed = false
    private var restoreCollapsedAfterSession = false
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
            if (continuousTracking) {
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
        val edgeInset = computeEdgeInset(settings, resolvedSide, density)
        val systemInsets = computeSystemInsets(wm, density, resolvedSide, edgeInset)
        edgeInsetPx = edgeInset
        topInsetPx = systemInsets.first
        bottomInsetPx = systemInsets.second
        this.side = resolvedSide
        this.settings = settings
        hostContext = host

        val view = FloatNavView(
            context = host,
            appSettings = settings,
            side = resolvedSide,
            listener = viewListener,
        )
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        val barWidth = view.measuredWidth.coerceAtLeast(1)
        val barHeight = view.measuredHeight.coerceAtLeast(1)
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            barWidthPx = barWidth,
            barHeightPx = barHeight,
            side = resolvedSide,
            anchorX = anchorRawX ?: (screenWidth / 2f),
            anchorY = anchorRawY ?: (screenHeight / 2f),
            edgeInsetPx = edgeInsetPx,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        )
        val lp = buildLayoutParams(host, position)
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
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            barWidthPx = view.width.coerceAtLeast(1),
            barHeightPx = view.height.coerceAtLeast(1),
            side = resolvedSide,
            anchorX = lp.x + view.width / 2f,
            anchorY = lp.y + view.height / 2f,
            edgeInsetPx = edgeInsetPx,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        )
        lp.x = position.x
        lp.y = position.y
        runCatching { wm.updateViewLayout(view, lp) }
        view.applyMaterial()
    }

    private fun startTracking(anchorRawX: Float?, anchorRawY: Float?) {
        val host = hostContext ?: return
        // 收起态没有导航键可落；持续触发先自动展开，会话结束后再恢复收起。
        root?.let { view ->
            if (view.isCollapsed) {
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
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            barWidthPx = view.width.coerceAtLeast(1),
            barHeightPx = view.height.coerceAtLeast(1),
            side = side,
            anchorX = anchorRawX ?: lp.x + view.width / 2f,
            anchorY = anchorRawY ?: lp.y + view.height / 2f,
            edgeInsetPx = edgeInsetPx,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        )
        lp.x = position.x
        lp.y = position.y
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
        val minX = edgeInsetPx
        val maxX = (screenWidth - view.width - edgeInsetPx).coerceAtLeast(minX)
        val minY = topInsetPx
        val maxY = (screenHeight - view.height - bottomInsetPx).coerceAtLeast(minY)
        lp.x = (lp.x + dx.roundToInt()).coerceIn(minX, maxX)
        lp.y = (lp.y + dy.roundToInt()).coerceIn(minY, maxY)
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
            if (tracking) return
            val view = root ?: return
            view.toggleCollapsed()
            settings?.let { HapticHelper.confirmLaunch(view, it) }
        }

        override fun onBarSizeChanged(oldW: Int, oldH: Int, newW: Int, newH: Int) {
            if (oldW <= 0 || oldH <= 0) return
            val view = root ?: return
            val wm = windowManager ?: return
            val lp = layoutParams ?: return
            view.post {
                if (root !== view || layoutParams !== lp) return@post
                // 右侧边缘的把柄在最右边：收起/展开时固定窗右边，别让把柄横跳。
                if (side == PanelSide.RIGHT) {
                    lp.x += oldW - newW
                }
                // 把柄在条内垂直居中；高度变化时保持把柄中心不动。
                if (oldH != newH) {
                    lp.y += (oldH - newH) / 2
                }
                val minX = edgeInsetPx
                val maxX = (screenWidth - newW - edgeInsetPx).coerceAtLeast(minX)
                val minY = topInsetPx
                val maxY = (screenHeight - newH - bottomInsetPx).coerceAtLeast(minY)
                lp.x = lp.x.coerceIn(minX, maxX)
                lp.y = lp.y.coerceIn(minY, maxY)
                runCatching { wm.updateViewLayout(view, lp) }
            }
        }

        override fun onDragStart() = Unit

        override fun onDragBy(dx: Float, dy: Float) {
            if (tracking) return
            moveBy(dx, dy)
        }

        override fun onDragEnd() = Unit
    }

    private fun buildLayoutParams(
        host: Context,
        position: FloatNavPosition,
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
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        x = position.x
        y = position.y
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
