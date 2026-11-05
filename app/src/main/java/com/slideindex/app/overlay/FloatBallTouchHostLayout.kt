package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.widget.FrameLayout
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/** 取词手势"按住才成立"状态最长允许无输入事件的时间，超过即判定 UP/CANCEL 丢失。 */
private const val CAPTURE_IDLE_TIMEOUT_MS = 10_000L

/**
 * 球体触摸窗：空闲时 WM 层仅为球区；滑出 slop 进入取词后由 [FloatBallOverlay] 扩全屏跟手，手势锁到 UP/CANCEL。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallTouchHostLayout(
    context: Context,
    private val sceneState: FloatBallSceneState,
    private val settingsProvider: () -> AppSettings,
    private val activeSideProvider: () -> FloatBallSide,
    private val screenSizeProvider: () -> Pair<Int, Int>
) : FrameLayout(context) {

    private val ballDetector = FloatBallGestureDetector()

    var ballStripTouchable: Boolean = true

    /** 手势卡死（UP/CANCEL 丢失）时回调，由 [FloatBallOverlay] 收回全屏触摸窗。 */
    var onCaptureStalled: (() -> Unit)? = null

    /** 手指按下并命中球区：闲置隐身据此立即显形。 */
    var onBallPressed: (() -> Unit)? = null

    /** 手指离开/取消/手势收尾：闲置隐身据此重新计时。 */
    var onBallReleased: (() -> Unit)? = null

    private var gestureCaptureActive = false

    private val captureWatchdog = StuckGestureWatchdog(
        timeoutMs = CAPTURE_IDLE_TIMEOUT_MS,
        isHolding = { gestureCaptureActive || ballDetector.isLauncherCaptureMode() },
        onStall = {
            forceEndGestureCapture()
            onCaptureStalled?.invoke()
        }
    )

    private var onBallDragStart: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onBallDrag: ((fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onBallDragEnd: (() -> Unit)? = null
    private var onBallDragCancel: (() -> Unit)? = null
    private var onBallGesture: ((FloatBallGestureType, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Unit)? = null
    private var onBallGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onBallPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onBallPickPreviewMove: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onBallPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onBallPickPreviewCancel: (() -> Unit)? = null
    /** 第二根手指取消黄框/区域选择（回红十字、保留本次拖动）。 */
    private var onBallPickSelectionCancel: (() -> Unit)? = null
    private var onLauncherCaptureMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureUp: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureCancel: (() -> Unit)? = null
    /** FV「自定义手势」：返回 true 表示本次抬手已被自定义手势消费（不再走原生方向手势）。 */
    private var onCustomGestureResolve: ((path: FloatArray, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Boolean)? = null

    private var idleChromeView: View? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** 空闲态球体视觉叠在触摸窗内，避免全屏 display 挡触摸。 */
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

    fun updateSettings(settings: AppSettings) {
        val density = resources.displayMetrics.density
        bindDetector(
            detector = ballDetector,
            settings = settings,
            density = density,
            onDragStart = { downX, downY, fingerX, fingerY ->
                onBallDragStart?.invoke(downX, downY, fingerX, fingerY)
            },
            onDrag = { fingerX, fingerY -> onBallDrag?.invoke(fingerX, fingerY) },
            onDragEnd = { onBallDragEnd?.invoke() },
            onDragCancel = { onBallDragCancel?.invoke() },
            onPickSelectionCancel = { onBallPickSelectionCancel?.invoke() },
            onGesture = { type, x, y, touchDownX, touchDownY ->
                onBallGesture?.invoke(type, x, y, touchDownX, touchDownY)
            },
            onGestureHint = { type -> onBallGestureHint?.invoke(type) },
            onPickPreviewStart = { x, y -> onBallPickPreviewStart?.invoke(x, y) },
            onPickPreviewProgress = { p -> onBallPickPreviewProgress?.invoke(p) },
            onPickPreviewMove = { downX, downY, fingerX, fingerY ->
                onBallPickPreviewMove?.invoke(downX, downY, fingerX, fingerY)
            },
            onPickPreviewCancel = { onBallPickPreviewCancel?.invoke() },
            onLauncherCaptureMove = { x, y -> onLauncherCaptureMove?.invoke(x, y) },
            onLauncherCaptureUp = { x, y -> onLauncherCaptureUp?.invoke(x, y) },
            onLauncherCaptureCancel = { onLauncherCaptureCancel?.invoke() },
            // FV「自定义手势」：返回 true = 本次抬手已被自定义手势消费（见 FloatBallGestureDetector）
            onCustomGestureResolve = { path, rawX, rawY, touchDownX, touchDownY ->
                onCustomGestureResolve?.invoke(path, rawX, rawY, touchDownX, touchDownY) ?: false
            }
        )
    }

    fun bindBallCallbacks(
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
        onBallDragStart = onDragStart
        onBallDrag = onDrag
        onBallDragEnd = onDragEnd
        onBallDragCancel = onDragCancel
        onBallGesture = onGesture
        onBallGestureHint = onGestureHint
        onBallPickPreviewStart = onPickPreviewStart
        onBallPickPreviewProgress = onPickPreviewProgress
        onBallPickPreviewMove = onPickPreviewMove
        onBallPickPreviewCancel = onPickPreviewCancel
        onBallPickSelectionCancel = onPickSelectionCancel
        this.onLauncherCaptureMove = onLauncherCaptureMove
        this.onLauncherCaptureCancel = onLauncherCaptureCancel
        this.onLauncherCaptureUp = onLauncherCaptureUp
        this.onCustomGestureResolve = onCustomGestureResolve
        settingsProvider().let { updateSettings(it) }
    }

    fun endGestureCapture() {
        gestureCaptureActive = false
        captureWatchdog.onStateChanged()
    }

    fun forceEndGestureCapture() {
        if (gestureCaptureActive) {
            ballDetector.cancel()
            onBallReleased?.invoke()
        }
        endGestureCapture()
    }

    /**
     * 圆环工具栏「移动」进入的移动模式：只跟手挪球，不识别/不执行任何悬浮球手势，也不显示手势提示。
     * 抬手时检测器会走 inishPick()（= 拖动正常结束），把落位交回 [FloatBallOverlay]。
     */
    fun setMoveMode(active: Boolean) {
        ballDetector.moveModeActive = active
    }

    fun lockPickFromPause() {
        ballDetector.lockPickFromPause()
    }

    fun unlockPickFromPause() {
        ballDetector.unlockPickFromPause()
    }

    private fun bindDetector(
        detector: FloatBallGestureDetector,
        settings: AppSettings,
        density: Float,
        onDragStart: (Float, Float, Float, Float) -> Unit,
        onDrag: (Float, Float) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onPickSelectionCancel: () -> Unit,
        onGesture: (FloatBallGestureType, Float, Float, Float, Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit,
        onPickPreviewStart: (Float, Float) -> Unit,
        onPickPreviewProgress: (Float) -> Unit,
        onPickPreviewMove: (Float, Float, Float, Float) -> Unit,
        onPickPreviewCancel: () -> Unit,
        onLauncherCaptureMove: (Float, Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (Float, Float) -> Unit = { _, _ -> },
        onLauncherCaptureCancel: () -> Unit = {},
        onCustomGestureResolve: (FloatArray, Float, Float, Float, Float) -> Boolean = { _, _, _, _, _ -> false }
    ) {
        detector.bind(
            settings = settings,
            density = density,
            systemTouchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            onPickStart = onDragStart,
            onPickDrag = onDrag,
            onPickEnd = onDragEnd,
            onPickCancel = onDragCancel,
            onPickSelectionCancel = onPickSelectionCancel,
            onGesture = onGesture,
            onGestureHint = onGestureHint,
            onPickPreviewStart = onPickPreviewStart,
            onPickPreviewProgress = onPickPreviewProgress,
            onPickPreviewMove = onPickPreviewMove,
            onPickPreviewCancel = onPickPreviewCancel,
            onLauncherCaptureMove = onLauncherCaptureMove,
            onLauncherCaptureUp = onLauncherCaptureUp,
            onLauncherCaptureCancel = onLauncherCaptureCancel,
            onCustomGestureResolve = onCustomGestureResolve,
            inwardSignProvider = {
                // 混合手势「向内」= 朝屏幕中心：左侧停靠为 +x，右侧停靠为 -x。
                if (activeSideProvider() == FloatBallSide.LEFT) 1f else -1f
            }
        )
    }

    fun beginLauncherCaptureMode() {
        gestureCaptureActive = true
        ballDetector.enterLauncherCaptureMode()
        captureWatchdog.onStateChanged()
    }

    fun isLauncherCaptureMode(): Boolean = ballDetector.isLauncherCaptureMode()

    fun cancelLauncherCaptureMode() {
        ballDetector.cancelLauncherCaptureMode()
        endGestureCapture()
    }

    private fun hitTestBall(x: Float, y: Float): Boolean {
        if (!ballStripTouchable) return false
        val settings = settingsProvider()
        val metrics = resources.displayMetrics
        val activeSide = activeSideProvider()
        val (screenW, screenH) = screenSizeProvider()
        val ballRect = sceneState.ballHitRect(settings, metrics, activeSide, screenW, screenH)
        return ballRect.contains(x.roundToInt(), y.roundToInt())
    }

    /**
     * WM 窗在 z-order 重挂后可能大于命中区；未命中时返回 false，让触摸落到下层应用。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!hitTestBall(event.rawX, event.rawY)) return false
                }
                else -> return false
            }
        }
        return super.dispatchTouchEvent(event).also { captureWatchdog.onInput() }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureCaptureActive || ballDetector.isLauncherCaptureMode()) return true
        if (!ballStripTouchable) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hitTestBall(event.rawX, event.rawY)) {
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode() && !ballStripTouchable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestBall(event.rawX, event.rawY)) return false
                gestureCaptureActive = true
                onBallPressed?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) return false
                onBallReleased?.invoke()
                val handled = ballDetector.onTouchEvent(event)
                if (!ballDetector.isLauncherCaptureMode()) {
                    gestureCaptureActive = false
                }
                return handled
            }
        }
        if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) return false
        return ballDetector.onTouchEvent(event)
    }

    fun handleForwardedTouch(event: MotionEvent): Boolean {
        return dispatchForwardedTouch(event).also { captureWatchdog.onInput() }
    }

    /**
     * 输入层接管（system_server 模块）转发来的球体触摸。
     *
     * 与 [onTouchEvent] 唯一差别是 DOWN 的命中已在模块 + app 的 `canAcceptTouchAt` 里现场复核过；
     * 这里仍按同一份 `hitTestBall` 再验一次，避免几何在两次判定之间变化时出现"空处起手势"。
     * 未命中即返回 false，调用方丢弃该事件（最坏只丢一次触摸，不产生残留状态）。
     */
    private fun dispatchForwardedTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hitTestBall(event.rawX, event.rawY)) return false
                gestureCaptureActive = true
                onBallPressed?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) return false
                onBallReleased?.invoke()
                val handled = ballDetector.onTouchEvent(event)
                if (!ballDetector.isLauncherCaptureMode()) {
                    gestureCaptureActive = false
                }
                return handled
            }
        }
        if (!gestureCaptureActive && !ballDetector.isLauncherCaptureMode()) return false
        return ballDetector.onTouchEvent(event)
    }
}
