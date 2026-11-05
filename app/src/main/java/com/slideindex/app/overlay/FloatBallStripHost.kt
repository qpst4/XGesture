package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/** 取词手势"按住才成立"状态最长允许无输入事件的时间，超过即判定 UP/CANCEL 丢失。 */
private const val STRIP_CAPTURE_IDLE_TIMEOUT_MS = 10_000L

/**
 * 线条触摸窗：空闲时 WM 层仅为线条触发区；滑出 slop 进入取词后由 [FloatBallOverlay] 扩全屏跟手，手势锁到 UP/CANCEL。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallStripHost(
    context: Context,
    private val sceneState: FloatBallSceneState,
    private val settingsProvider: () -> AppSettings,
    private val activeSideProvider: () -> FloatBallSide,
    private val screenSizeProvider: () -> Pair<Int, Int>
) : FrameLayout(context) {
    private val gestureDetector = FloatBallGestureDetector()
    var stripTouchable: Boolean = true

    /** 手势卡死（UP/CANCEL 丢失）时回调，由 [FloatBallOverlay] 收回全屏触摸窗。 */
    var onCaptureStalled: (() -> Unit)? = null

    private var gestureActive = false

    private val captureWatchdog = StuckGestureWatchdog(
        timeoutMs = STRIP_CAPTURE_IDLE_TIMEOUT_MS,
        isHolding = { gestureActive || gestureDetector.isLauncherCaptureMode() },
        onStall = {
            cancelGesture()
            onCaptureStalled?.invoke()
        }
    )
    private var idleChromeView: View? = null

    /** 空闲态线条视觉叠在触摸窗内，避免全屏 display 挡触摸。 */
    fun setIdleChrome(view: View?, owner: OverlayComposeOwner?) {
        if (idleChromeView === view) return
        idleChromeView?.let { removeView(it) }
        idleChromeView = view
        if (owner != null) {
            OverlayCompose.bindOwners(this, owner)
        } else {
            OverlayCompose.clearViewTreeOwners(this)
        }
        if (view != null) {
            view.isClickable = false
            view.isFocusable = false
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private var onDragStart: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onDrag: ((fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onDragEnd: (() -> Unit)? = null
    private var onDragCancel: (() -> Unit)? = null
    private var onGesture: ((FloatBallGestureType, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Unit)? = null
    private var onGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onPickPreviewMove: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickPreviewCancel: (() -> Unit)? = null
    /** 第二根手指取消黄框/区域选择（回红十字、保留本次拖动）。 */
    private var onPickSelectionCancel: (() -> Unit)? = null
    private var onLauncherCaptureMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureUp: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureCancel: (() -> Unit)? = null
    /** FV「自定义手势」：返回 true 表示本次抬手已被自定义手势消费（不再走原生方向手势）。 */
    private var onCustomGestureResolve: ((path: FloatArray, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Boolean)? = null

    fun updateSettings(settings: AppSettings) {
        val density = resources.displayMetrics.density
        gestureDetector.bind(
            settings = settings,
            density = density,
            systemTouchSlopPx = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            onPickStart = { downX, downY, fingerX, fingerY ->
                onDragStart?.invoke(downX, downY, fingerX, fingerY)
            },
            onPickDrag = { fingerX, fingerY -> onDrag?.invoke(fingerX, fingerY) },
            onPickEnd = { onDragEnd?.invoke() },
            onPickCancel = { onDragCancel?.invoke() },
            onPickSelectionCancel = { onPickSelectionCancel?.invoke() },
            onGesture = { type, rawX, rawY, touchDownX, touchDownY ->
                onGesture?.invoke(type, rawX, rawY, touchDownX, touchDownY)
            },
            onGestureHint = { type -> onGestureHint?.invoke(type) },
            onPickPreviewStart = { x, y -> onPickPreviewStart?.invoke(x, y) },
            onPickPreviewProgress = { progress -> onPickPreviewProgress?.invoke(progress) },
            onPickPreviewMove = { downX, downY, fingerX, fingerY ->
                onPickPreviewMove?.invoke(downX, downY, fingerX, fingerY)
            },
            onPickPreviewCancel = { onPickPreviewCancel?.invoke() },
            onLauncherCaptureMove = { x, y -> onLauncherCaptureMove?.invoke(x, y) },
            onLauncherCaptureUp = { x, y -> onLauncherCaptureUp?.invoke(x, y) },
            onLauncherCaptureCancel = { onLauncherCaptureCancel?.invoke() },
            onCustomGestureResolve = { path, rawX, rawY, touchDownX, touchDownY ->
                onCustomGestureResolve?.invoke(path, rawX, rawY, touchDownX, touchDownY) ?: false
            },
            inwardSignProvider = {
                // 线条恒在球停靠侧的对侧：球在左 → 线在右 → 朝屏幕内侧是 -x。
                // 不传这个 provider 时检测器会用默认值 +1（恒按左侧算），右侧线条上的
                // 混合手势（先上下再向内 / 先向内再上下）就永远判不出来。
                if (FloatBallLayout.resolvedActiveSide(settingsProvider()) == FloatBallSide.LEFT) -1f else 1f
            }
        )
    }

    fun bindDragCallbacks(
        onDragStart: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit,
        onDrag: (fingerX: Float, fingerY: Float) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
        onPickPreviewStart: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (progress: Float) -> Unit = {},
        onPickPreviewMove: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit = { _, _, _, _ -> },
        onPickPreviewCancel: () -> Unit = {},
        onPickSelectionCancel: () -> Unit = {},
        onLauncherCaptureMove: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureCancel: () -> Unit = {},
        onCustomGestureResolve: (path: FloatArray, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Boolean = { _, _, _, _, _ -> false }
    ) {
        this.onDragStart = onDragStart
        this.onDrag = onDrag
        this.onDragEnd = onDragEnd
        this.onDragCancel = onDragCancel
        this.onGesture = onGesture
        this.onGestureHint = onGestureHint
        this.onPickPreviewStart = onPickPreviewStart
        this.onPickPreviewProgress = onPickPreviewProgress
        this.onPickPreviewMove = onPickPreviewMove
        this.onPickPreviewCancel = onPickPreviewCancel
        this.onPickSelectionCancel = onPickSelectionCancel
        this.onLauncherCaptureMove = onLauncherCaptureMove
        this.onLauncherCaptureCancel = onLauncherCaptureCancel
        this.onLauncherCaptureUp = onLauncherCaptureUp
        this.onCustomGestureResolve = onCustomGestureResolve
        settingsProvider().let { updateSettings(it) }
    }

    fun beginLauncherCaptureMode() {
        gestureActive = true
        gestureDetector.enterLauncherCaptureMode()
        captureWatchdog.onStateChanged()
    }

    fun isLauncherCaptureMode(): Boolean = gestureDetector.isLauncherCaptureMode()

    fun cancelLauncherCaptureMode() {
        gestureDetector.cancelLauncherCaptureMode()
        gestureActive = false
    }

    fun cancelGesture() {
        if (gestureActive) {
            gestureActive = false
            gestureDetector.cancel()
        }
        captureWatchdog.onStateChanged()
    }

    fun lockPickFromPause() {
        gestureDetector.lockPickFromPause()
    }

    /** 模块侧多指中断时：先取消黄框/区域选中，再结束手势，避免 cancel 被当成提交。 */
    fun cancelPickSelection(): Boolean = gestureDetector.cancelPickSelectionFromPause()

    fun unlockPickFromPause() {
        gestureDetector.unlockPickFromPause()
    }

    private fun hitTestLine(x: Float, y: Float): Boolean {
        if (!stripTouchable) return false
        val settings = settingsProvider()
        if (!sceneState.lineVisible.value || !FloatBallLayout.shouldShowLine(settings)) return false
        val metrics = resources.displayMetrics
        val inactiveSide = FloatBallSide.opposite(activeSideProvider())
        val (screenW, screenH) = screenSizeProvider()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rect = sceneState.lineHitRect(
            settings = settings,
            metrics = metrics,
            inactiveSide = inactiveSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            isLandscape = isLandscape,
        )
        return rect.contains(x.roundToInt(), y.roundToInt())
    }

    /**
     * WM 窗在 z-order 重挂后可能大于命中区；未命中时返回 false，让触摸落到下层应用。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!gestureActive && !gestureDetector.isLauncherCaptureMode()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!hitTestLine(event.rawX, event.rawY)) return false
                }
                else -> return false
            }
        }
        return super.dispatchTouchEvent(event).also { captureWatchdog.onInput() }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureActive || gestureDetector.isLauncherCaptureMode()) return true
        if (!stripTouchable) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hitTestLine(event.rawX, event.rawY)) {
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureActive && !gestureDetector.isLauncherCaptureMode() && !stripTouchable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestLine(event.rawX, event.rawY)) return false
                gestureActive = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureActive && !gestureDetector.isLauncherCaptureMode()) return false
                val handled = gestureDetector.onTouchEvent(event)
                if (!gestureDetector.isLauncherCaptureMode()) {
                    gestureActive = false
                }
                return handled
            }
        }
        if (!gestureActive && !gestureDetector.isLauncherCaptureMode()) return false
        return gestureDetector.onTouchEvent(event)
    }

    /**
     * 输入层接管（system_server 模块）转发来的线条触摸。
     *
     * 与 [onTouchEvent] 唯一差别是 DOWN 的命中已在模块 + app 的 `canAcceptTouchAt` 里现场复核过；
     * 这里再用同一份 `hitTestLine` 复核一次，几何变化导致未命中就丢弃（不产生残留状态）。
     */
    fun handleForwardedTouch(event: MotionEvent): Boolean {
        return dispatchForwardedTouch(event).also { captureWatchdog.onInput() }
    }

    private fun dispatchForwardedTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestLine(event.rawX, event.rawY)) return false
                gestureActive = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureActive && !gestureDetector.isLauncherCaptureMode()) return false
                val handled = gestureDetector.onTouchEvent(event)
                if (!gestureDetector.isLauncherCaptureMode()) {
                    gestureActive = false
                }
                return handled
            }
        }
        if (!gestureActive && !gestureDetector.isLauncherCaptureMode()) return false
        return gestureDetector.onTouchEvent(event)
    }
}
