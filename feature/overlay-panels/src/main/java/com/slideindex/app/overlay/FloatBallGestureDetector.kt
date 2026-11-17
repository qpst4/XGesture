package com.slideindex.app.overlay

import android.os.Handler
import android.util.Log
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.isEffective
import kotlin.math.abs
import kotlin.math.hypot

/**
 * FooView 风格：拖出后球体立即跟手；约 [PICK_GESTURE_LOCK_MS] 内松手走手势，
 * 超时后锁定取词/区域拾取，抬手不再触发滑动手势。
 * 首次滑出方向后锁定轴向，斜拖/改向不再切换手势提示与抬手判定。
 * 沿锁定轴往回滑则取消手势；再次往原方向滑出可重新触发提示与抬手判定。
 * 若配置了折返手势且滑出达标后快速搓回，则抬手触发对应折返手势。
 * 抬手判定记录锁定轴正向峰值位移；末尾微回弹在 [GESTURE_CANCEL_RETAIN_FRACTION] 容差内仍触发手势。
 * 单击/双击/长按不进入取词；slop 内球体微跟手，拖出 slop 后进入取词，[PICK_GESTURE_LOCK_MS] 从拖出时刻起算。
 * 黄框暂停（[lockPickFromPause]）与超时锁定等价：抬手仅取词，不触发滑动手势。
 * 黄框/区域选择时第二根手指按下可取消选中并回到红十字：本次拖动保留，但抬手不提交。
 */
class FloatBallGestureDetector(
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    enum class LockedSwipeAxis {
        UP,
        DOWN,
        SIDE,
    }

    private companion object {
        /** 与 FV s4() 的 800ms 手势窗口一致：超时后仅取词。 */
        const val PICK_GESTURE_LOCK_MS = 800L
        const val LONG_PRESS_MS = 500L
        const val DOUBLE_TAP_MS = 300L
        const val DIRECTION_RATIO = 1.4f
        /** FV f11261s：40dp 基准。 */
        const val SWIPE_BASE_DP = 40f
        /** 折返手势反向位移门槛（约 28dp）。 */
        const val REBOUND_THRESHOLD_DP = 28f
        /** 混合手势第二段「向内」位移门槛（与边缘触钮 TURN_SLOP_DP 一致）。 */
        const val COMPOUND_TURN_DP = 32f
        /**
         * 混合手势首段统一门槛：不再要求滑够该轴短滑阈值（那样要 8~13dp 起步、向内甚至 128dp），
         * 只要滑出一小段即可进入第二段判定——区分两段式与普通滑动的关键是第二段的转向。
         */
        const val COMPOUND_FIRST_DP = 40f
        /** 混合手势第二段沿边分量相对向内分量的上限（与边缘触钮同容差，防斜滑误判）。 */
        const val COMPOUND_MAX_ALONG_RATIO = 0.8f
        /** 拉回原点停顿超时判定为取消（ms）。 */
        const val REBOUND_CANCEL_HOLD_MS = 320L
        /**
         * 抬手时当前正向位移低于峰值的比例则视为取消；高于则仍触发（容忍末尾微回弹）。
         */
        const val GESTURE_CANCEL_RETAIN_FRACTION = 0.55f

        /** 长按允许的手指抖动：12dp ≈ 2mm。超过它才取消长按（转成取词）。 */
        const val LONG_PRESS_TOLERANCE_DP = 12f
    }

    private var density = 1f
    private var downSwipeShortPx = 60f
    private var sideSwipeShortPx = 60f
    private var upSwipeShortPx = 60f
    /**
     * 点击/双击判定的容差（px）：取系统 `ViewConfiguration.scaledTouchSlop`，与 FV 一致。
     * 它**不参与**拖动起点判定 —— 对齐 FV：手指一动球就跟、取词立即开始，没有死区。
     */
    private var tapSlopPx = 8f

    /** 取词启动阈值（px）：来自设置「拖动死区」，0 = 手指一动就开始取词。 */
    private var pickStartPx = 0f
    private var longPressMs = LONG_PRESS_MS
    private var configuredActions: Map<FloatBallGestureType, GestureAction> = emptyMap()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var pickActive = false
    /** 手指滑出 slop 后为 true，此后才跟手取词并启动手势窗口。 */
    private var pickDragStarted = false

    /**
     * 移动模式（圆环工具栏「移动」触发）：只跟手挪球。
     * 置位后：不显示手势提示、不执行任何手势，抬手直接走 inishPick()（= 拖动结束，交回覆盖层落位）。
     */
    var moveModeActive: Boolean = false
    private var pickDragStartTime = 0L
    /** 超过手势窗口后为 true，抬手走取词而非手势。 */
    private var pickGestureLocked = false
    /** 黄框暂停后为 true，与 [pickGestureLocked] 一样锁定取词提交。 */
    private var pausePickLocked = false
    /**
     * 主手指 pointerId。
     *
     * 多指时只认这一根：黄框后第二根手指按下用于“取消选中”，第一根手指若先抬起，
     * 绝不能把第二根当成主手指继续拖动/提交。
     */
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    /** 第二根手指取消黄框/区域选择后为 true：回红叉，本次抬手不提交、不触发手势。 */
    private var selectionCancelActive = false
    /** 主手指已抬起但仍有其它手指按着：吞掉剩余事件，直到全部抬起。 */
    private var ignoreMultiTouchTail = false
    /** 首次滑出方向锁定后，仅沿该轴累计位移用于提示/抬手判定。 */
    private var lockedSwipeAxis: LockedSwipeAxis? = null
    /** 锁定轴上的正向符号：上=-1、下=+1、侧=sign(dx)。 */
    private var lockedAxisForwardSign = 0f
    /** 沿锁定轴正向滑出 slop 后为 true；往回滑取消，再次正向滑出可重新武装。 */
    private var gestureArmed = false
    /** 本次拖拽在锁定轴上达到的最大正向位移（px）。 */
    private var peakForwardProgressPx = 0f
    private var reboundStartTime = 0L
    private var longPressFired = false
    /** 首段（上/下）达标时的拐点锚点，用于第二段「向内」判定；null 表示尚未达标。 */
    private var firstSegmentAnchorX: Float? = null
    private var firstSegmentAnchorY: Float? = null
    /** 朝屏幕内侧的 X 方向符号（左侧停靠 = +1，右侧停靠 = -1）。 */
    private var inwardSignProvider: () -> Float = { 1f }
    /** 本次按下后是否曾滑出 touch slop；用于拖出再拖回时不误判为单击。 */
    private var movedBeyondSlop = false
    private var pendingSingleTap = false
    private var pendingSingleTapX = 0f
    private var pendingSingleTapY = 0f

    /** 待判定单击那一次的按下位置，供 [onGesture] 的 touchDown 参数使用。 */
    private var pendingSingleTapDownX = 0f
    private var pendingSingleTapDownY = 0f

    private var onPickPreviewStart: ((screenX: Float, screenY: Float) -> Unit)? = null
    private var onPickPreviewProgress: ((progress: Float) -> Unit)? = null
    private var onPickPreviewMove: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickPreviewCancel: (() -> Unit)? = null
    private var onPickStart: ((touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickDrag: ((fingerX: Float, fingerY: Float) -> Unit)? = null
    private var onPickEnd: (() -> Unit)? = null
    private var onPickCancel: (() -> Unit)? = null
    /** 第二根手指取消黄框/区域选择：覆盖层应清选中态、回红十字，但保留本次拖动。 */
    private var onPickSelectionCancel: (() -> Unit)? = null
    private var onGesture: ((
        FloatBallGestureType,
        rawX: Float,
        rawY: Float,
        touchDownX: Float,
        touchDownY: Float,
    ) -> Unit)? = null
    private var onGestureHint: ((FloatBallGestureType?) -> Unit)? = null
    private var onLauncherCaptureMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureUp: ((rawX: Float, rawY: Float) -> Unit)? = null
    private var onLauncherCaptureCancel: (() -> Unit)? = null
    private var launcherCaptureMode = false

    /**
     * FV「自定义手势」的接线点：把本次抬手的原始轨迹（x,y 交替的**屏幕 raw 坐标**）交给外部判定。
     *
     * 返回 true = 本次已被自定义手势消费（命中并执行了动作，或画的是曲线但没匹配到 —— 后者按 FV
     * 语义「什么都不做」）；返回 false = 交给原生方向手势，行为与未接入时完全一致。
     *
     * 解耦说明：这里是纯 lambda，检测器不认识 FV 的类型；判定与动作执行都在
     * `FloatBallOverlay` 侧（见 `FvFloatBallBridge.kt`）。
     */
    private var onCustomGestureResolve: ((path: FloatArray, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Boolean)? = null

    /**
     * 本次手势的原始轨迹（x,y 交替）。仅用于 FV 自定义手势的形状识别；
     * 与 `downX/downY/lastX/lastY` 那套「方向 + 位移」判定互不干扰。
     */
    private val gesturePath = ArrayList<Float>(64)

    private val pickGestureLockRunnable = Runnable {
        if (!pickDragStarted) return@Runnable
        pickGestureLocked = true
        onGestureHint?.invoke(null)
    }

    private val longPressRunnable = Runnable {
        if (pickDragStarted || longPressFired) return@Runnable
        val dist = hypot(lastX - downX, lastY - downY)
        if (dist <= maxOf(tapSlopPx * 2f, pickStartPx)) {
            longPressFired = true
            pendingSingleTap = false
            // 长按抢在取词前面：先把已经开始的取词/预览收掉，再执行长按动作。
            if (pickDragStarted) {
                pickDragStarted = false
                pickActive = false
                onPickCancel?.invoke()
            } else if (pickActive) {
                pickActive = false
                onPickPreviewCancel?.invoke()
            }
            onGesture?.invoke(FloatBallGestureType.LONG_PRESS, lastX, lastY, downX, downY)
        }
    }

    private val singleTapRunnable = Runnable {
        if (pendingSingleTap) {
            pendingSingleTap = false
            onGesture?.invoke(
                FloatBallGestureType.SINGLE_TAP,
                pendingSingleTapX,
                pendingSingleTapY,
                pendingSingleTapDownX,
                pendingSingleTapDownY,
            )
        }
    }

    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f

    fun bind(
        settings: AppSettings,
        density: Float,
        systemTouchSlopPx: Float,
        onPickStart: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit,
        onPickDrag: (fingerX: Float, fingerY: Float) -> Unit,
        onPickEnd: () -> Unit,
        onPickCancel: () -> Unit,
        onPickSelectionCancel: () -> Unit = {},
        onGesture: (FloatBallGestureType, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Unit,
        onGestureHint: (FloatBallGestureType?) -> Unit = {},
        onPickPreviewStart: (screenX: Float, screenY: Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (progress: Float) -> Unit = {},
        onPickPreviewMove: (touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) -> Unit = { _, _, _, _ -> },
        onPickPreviewCancel: () -> Unit = {},
        onLauncherCaptureMove: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureUp: (rawX: Float, rawY: Float) -> Unit = { _, _ -> },
        onLauncherCaptureCancel: () -> Unit = {},
        onCustomGestureResolve: (path: FloatArray, rawX: Float, rawY: Float, touchDownX: Float, touchDownY: Float) -> Boolean = { _, _, _, _, _ -> false },
        inwardSignProvider: () -> Float = { 1f }
    ) {
        this.density = density
        downSwipeShortPx = swipeThresholdPx(settings.floatBallDownSwipeShortPercent, density)
        sideSwipeShortPx = swipeThresholdPx(settings.floatBallSideSwipeShortPercent, density)
        upSwipeShortPx = swipeThresholdPx(settings.floatBallUpSwipeShortPercent, density)
        tapSlopPx = systemTouchSlopPx
        pickStartPx = settings.floatBallPointerSlopDp.coerceIn(0f, 32f) * density
        longPressMs = settings.floatBallLongPressMs.coerceIn(200, 2000).toLong()
        configuredActions = settings.floatBallGestureActions
        this.onPickStart = onPickStart
        this.onPickDrag = onPickDrag
        this.onPickEnd = onPickEnd
        this.onPickCancel = onPickCancel
        this.onPickSelectionCancel = onPickSelectionCancel
        this.onGesture = onGesture
        this.onGestureHint = onGestureHint
        this.onPickPreviewStart = onPickPreviewStart
        this.onPickPreviewProgress = onPickPreviewProgress
        this.onPickPreviewMove = onPickPreviewMove
        this.onPickPreviewCancel = onPickPreviewCancel
        this.onLauncherCaptureMove = onLauncherCaptureMove
        this.onLauncherCaptureUp = onLauncherCaptureUp
        this.onLauncherCaptureCancel = onLauncherCaptureCancel
        this.onCustomGestureResolve = onCustomGestureResolve
        this.inwardSignProvider = inwardSignProvider
    }

    fun enterLauncherCaptureMode() {
        launcherCaptureMode = true
        pickActive = true
        longPressFired = false
        handler.removeCallbacks(longPressRunnable)
        pickDragStarted = false
        pickGestureLocked = false
        pausePickLocked = false
        onGestureHint?.invoke(null)
    }

    fun isLauncherCaptureMode(): Boolean = launcherCaptureMode

    fun cancelLauncherCaptureMode() {
        launcherCaptureMode = false
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingSingleTap()
                resetTouchSession()
                activePointerId = event.getPointerId(0)
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                downTime = SystemClock.uptimeMillis()
                gesturePath.clear()
                gesturePath.add(downX)
                gesturePath.add(downY)
                pickActive = true
                onGestureHint?.invoke(null)
                onPickPreviewStart?.invoke(downX, downY)
                onPickPreviewMove?.invoke(downX, downY, downX, downY)
                handler.postDelayed(longPressRunnable, longPressMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pickActive || ignoreMultiTouchTail) return true
                if (launcherCaptureMode) {
                    lastX = event.rawX
                    lastY = event.rawY
                    onLauncherCaptureMove?.invoke(event.rawX, event.rawY)
                    return true
                }
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                gesturePath.add(event.rawX)
                gesturePath.add(event.rawY)
                val distFromStart = hypot(event.rawX - downX, event.rawY - downY)
                if (!pickDragStarted) {
                    onPickPreviewProgress?.invoke(1f)
                    if (distFromStart > 0f) {
                        onPickPreviewMove?.invoke(downX, downY, event.rawX, event.rawY)
                    }
                }
                var justStartedPickDrag = false
                // 取词：手指一离开按下点就开始（对齐 FV，没有死区）。
                // 长按定时器**不因移动取消**（FV 的 MOVE 分支里也不 remove）：
                // 到点时再看位移是否超过抖动容差，超过才算这次是取词。
                if (distFromStart > pickStartPx) {
                    movedBeyondSlop = true
                    handler.removeCallbacks(longPressRunnable)
                    pendingSingleTap = false
                    if (!pickDragStarted) {
                        startPickDrag(event.rawX, event.rawY)
                        justStartedPickDrag = true
                    }
                }
                if (pickDragStarted && !justStartedPickDrag) {
                    onPickDrag?.invoke(event.rawX, event.rawY)
                }
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                updateGestureArmState(totalDx, totalDy, incrementalDx = dx, incrementalDy = dy)
                emitGestureHint(totalDx, totalDy)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ignoreMultiTouchTail) return true
                // 圆环接管/移动模式有自己的 UP 语义，不参与取词的多指取消。
                if (launcherCaptureMode || moveModeActive) return true
                // 黄框/区域选择时，第二根手指按下 = 取消当前选中态，回红十字继续取词。
                // 只认这一下：后续第二根手指的 MOVE/UP 都忽略，主手指仍是第一根。
                if (pickDragStarted && pausePickLocked && !selectionCancelActive) {
                    beginSelectionCancel()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ignoreMultiTouchTail) return true
                if (launcherCaptureMode || moveModeActive) return true
                // 第一根手指抬起时若还有第二根按着，直接收尾，绝不让第二根接管。
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    finishCancelledPickAndIgnoreTail()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (ignoreMultiTouchTail) {
                    resetTouchSession()
                    return true
                }
                // 第二根手指已经取消选中：本次抬手只收尾，不提交、不识别手势。
                if (selectionCancelActive) {
                    onGestureHint?.invoke(null)
                    cancelDeferredCallbacks()
                    finishGestureOnly()
                    resetTouchSession()
                    return true
                }
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                gesturePath.add(event.rawX)
                gesturePath.add(event.rawY)
                // ★ 移动模式：抬手就是"拖动结束"，绝不执行任何手势。
                // 之前这里会落到下面的 returnGesture/compoundGesture/方向滑动分支，那些分支都以
                // finishGestureOnly() 收尾 → 覆盖层收到的是 onPickCancel（取消）→ 球被复位，
                // 于是"移动"看起来完全没生效。这里改走 finishPick() = 拖动正常结束，
                // 覆盖层才会执行"选边 + 存竖直位置 + 吸附回边缘"。
                if (moveModeActive) {
                    finishPick()
                    resetTouchSession()
                    return true
                }
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY
                lockSwipeAxisIfNeeded(totalDx, totalDy)
                updateGestureArmState(totalDx, totalDy, incrementalDx = 0f, incrementalDy = 0f)
                val (dx, dy) = projectedDisplacement(totalDx, totalDy)
                val totalDist = hypot(dx, dy)
                val locked = pickDragStarted && isPickCommitLocked()
                val returnGesture = classifyReturnGesture()
                val compoundGesture = classifyCompoundGesture()
                when {
                    launcherCaptureMode -> {
                        onLauncherCaptureUp?.invoke(event.rawX, event.rawY)
                        resetTouchSession()
                        launcherCaptureMode = false
                    }
                    longPressFired -> finishGestureOnly()
                    locked -> finishPick()
                    returnGesture != null -> {
                        onGesture?.invoke(returnGesture, event.rawX, event.rawY, downX, downY)
                        finishGestureOnly()
                    }
                    compoundGesture != null -> {
                        onGesture?.invoke(compoundGesture, event.rawX, event.rawY, downX, downY)
                        finishGestureOnly()
                    }
                    // FV「自定义手势」的位置（用户选定方案 A）：
                    //  - **原生"形状类"手势优先**：折返 / 混合（两段式）在上面已经先判，成立就归原生，绝不抢
                    //    （此前放在最前面，把"先下滑再内滑"这类混合手势吃掉了）。
                    //  - 这里只处理剩下的轨迹：**直线**由回调自己判定为"不介入"（返回 false）→ 继续落到下面的
                    //    方向滑动分支，行为与接入前完全一致；**折线/曲线**才交给自定义手势
                    //    （命中→执行动作；没命中→按 FV 语义本次什么都不做）。
                    onCustomGestureResolve?.invoke(
                        gesturePath.toFloatArray(),
                        event.rawX,
                        event.rawY,
                        downX,
                        downY
                    ) == true -> finishGestureOnly()
                    shouldCommitSwipeGesture(dx, dy) -> {
                        classifySwipe(dx, dy)
                            ?.let { onGesture?.invoke(it, event.rawX, event.rawY, downX, downY) }
                        finishGestureOnly()
                    }
                    movedBeyondSlop || totalDist > tapSlopPx -> finishGestureOnly()
                    else -> {
                        classifyTapRelease(event.rawX, event.rawY, downX, downY)
                        finishGestureOnly()
                    }
                }
                resetTouchSession()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onGestureHint?.invoke(null)
                cancelDeferredCallbacks()
                cancelPendingSingleTap()
                if (launcherCaptureMode) {
                    // CANCEL 不是"松手"：弹出圆环时我们会主动 cancelGesture 并隐藏触摸窗，
                    // 若把它当成 UP 就会立刻确认并执行第一个槽位（用户还没松手）。
                    launcherCaptureMode = false
                    onLauncherCaptureCancel?.invoke()
                } else if (pickActive) {
                    pickActive = false
                    if (pickDragStarted) {
                        onPickCancel?.invoke()
                    } else {
                        onPickPreviewCancel?.invoke()
                    }
                }
                resetTouchSession()
                return true
            }
        }
        return false
    }

    fun cancel() {
        cancelDeferredCallbacks()
        cancelPendingSingleTap()
        if (pickActive) {
            pickActive = false
            if (pickDragStarted) {
                onPickCancel?.invoke()
            } else {
                onPickPreviewCancel?.invoke()
            }
        }
        resetTouchSession()
    }

    /**
     * 第二根手指取消当前黄框/区域选择（回红十字、保留本次拖动）。
     *
     * @return true 表示确实取消了选中态；false 表示当前不在黄框/区域选择。
     */
    fun cancelPickSelectionFromPause(): Boolean {
        if (!pickActive || !pickDragStarted || !pausePickLocked) return false
        beginSelectionCancel()
        return true
    }

    private fun beginSelectionCancel() {
        pausePickLocked = false
        selectionCancelActive = true
        // 取消后不再累计滑动判定：本次手势要么重新黄框提交，要么抬手直接结束。
        gestureArmed = false
        peakForwardProgressPx = 0f
        reboundStartTime = 0L
        lockedSwipeAxis = null
        lockedAxisForwardSign = 0f
        firstSegmentAnchorX = null
        firstSegmentAnchorY = null
        onGestureHint?.invoke(null)
        onPickSelectionCancel?.invoke()
    }

    /** 主手指先抬起：收尾并吞掉仍在按着的其它手指，避免第二根手指接管。 */
    private fun finishCancelledPickAndIgnoreTail() {
        onGestureHint?.invoke(null)
        cancelDeferredCallbacks()
        if (pickActive) {
            pickActive = false
            if (pickDragStarted) {
                onPickCancel?.invoke()
            } else {
                onPickPreviewCancel?.invoke()
            }
        }
        resetTouchSession()
        ignoreMultiTouchTail = true
    }

    /** 黄框暂停：与超时锁定相同，抬手仅提交取词。 */
    fun lockPickFromPause() {
        if (!pickDragStarted) return
        pausePickLocked = true
        // 重新黄框后恢复“抬手提交”，清除上一次第二指取消留下的抑制。
        selectionCancelActive = false
        ignoreMultiTouchTail = false
        gestureArmed = false
        peakForwardProgressPx = 0f
        onGestureHint?.invoke(null)
    }

    /** 晃回暂停原点取消黄框时，若未到 [PICK_GESTURE_LOCK_MS] 则恢复手势判定。 */
    fun unlockPickFromPause() {
        if (!pickDragStarted) {
            pausePickLocked = false
            return
        }
        if (SystemClock.uptimeMillis() - pickDragStartTime < PICK_GESTURE_LOCK_MS) {
            pausePickLocked = false
        }
    }

    private fun isPickCommitLocked(): Boolean {
        return pickGestureLocked ||
            pausePickLocked ||
            (pickDragStarted &&
                SystemClock.uptimeMillis() - pickDragStartTime >= PICK_GESTURE_LOCK_MS)
    }

    private fun startPickDrag(fingerX: Float, fingerY: Float) {
        pickDragStarted = true
        pickDragStartTime = SystemClock.uptimeMillis()
        onPickStart?.invoke(downX, downY, fingerX, fingerY)
        handler.postDelayed(pickGestureLockRunnable, PICK_GESTURE_LOCK_MS)
    }

    private fun finishPick() {
        pickActive = false
        if (pickDragStarted) {
            onPickEnd?.invoke()
        }
    }

    private fun finishGestureOnly() {
        pickActive = false
        if (pickDragStarted) {
            onPickCancel?.invoke()
        } else {
            onPickPreviewCancel?.invoke()
        }
    }

    private fun cancelDeferredCallbacks() {
        handler.removeCallbacks(pickGestureLockRunnable)
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(singleTapRunnable)
    }

    private fun classifyTapRelease(upX: Float, upY: Float, touchDownX: Float, touchDownY: Float) {
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastTapUpTime <= DOUBLE_TAP_MS &&
            hypot(upX - lastTapUpX, upY - lastTapUpY) <= tapSlopPx * 2f
        if (isDoubleTap) {
            handler.removeCallbacks(singleTapRunnable)
            pendingSingleTap = false
            lastTapUpTime = 0L
            onGesture?.invoke(FloatBallGestureType.DOUBLE_TAP, upX, upY, touchDownX, touchDownY)
            return
        }
        // 双击槽位没有配置有效动作时不必再等双击窗口：单击立刻执行。
        // 否则「单击返回」这类动作永远要白等一个 DOUBLE_TAP_MS（300ms）。
        val doubleTapAction = configuredActions[FloatBallGestureType.DOUBLE_TAP] ?: GestureAction.None
        if (!doubleTapAction.isEffective()) {
            cancelPendingSingleTap()
            lastTapUpTime = 0L
            onGesture?.invoke(FloatBallGestureType.SINGLE_TAP, upX, upY, touchDownX, touchDownY)
            return
        }
        lastTapUpTime = now
        lastTapUpX = upX
        lastTapUpY = upY
        pendingSingleTap = true
        pendingSingleTapX = upX
        pendingSingleTapY = upY
        pendingSingleTapDownX = touchDownX
        pendingSingleTapDownY = touchDownY
        handler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
    }

    private fun lockSwipeAxisIfNeeded(totalDx: Float, totalDy: Float) {
        if (selectionCancelActive) return
        if (lockedSwipeAxis != null || isPickCommitLocked() || longPressFired) return
        val axis = resolveSwipeAxis(totalDx, totalDy) ?: return
        lockedSwipeAxis = axis
        lockedAxisForwardSign = forwardSignForAxis(axis, totalDx)
    }

    private fun forwardSignForAxis(axis: LockedSwipeAxis, totalDx: Float): Float =
        when (axis) {
            LockedSwipeAxis.UP -> -1f
            LockedSwipeAxis.DOWN -> 1f
            LockedSwipeAxis.SIDE -> if (totalDx >= 0f) 1f else -1f
        }

    fun updateGestureArmState(
        totalDx: Float,
        totalDy: Float,
        incrementalDx: Float,
        incrementalDy: Float,
        lockedAxis: LockedSwipeAxis? = lockedSwipeAxis,
        forwardSign: Float = lockedAxisForwardSign
    ) {
        if (selectionCancelActive) {
            gestureArmed = false
            peakForwardProgressPx = 0f
            reboundStartTime = 0L
            return
        }
        val axis = lockedAxis ?: run {
            gestureArmed = false
            peakForwardProgressPx = 0f
            reboundStartTime = 0L
            return
        }
        if (isPickCommitLocked() || longPressFired) {
            gestureArmed = false
            return
        }
        val (projDx, projDy) = projectedDisplacement(totalDx, totalDy, axis)
        val forwardProgress = forwardProgressAlongAxis(axis, projDx, projDy, forwardSign)
        if (forwardProgress > peakForwardProgressPx) {
            peakForwardProgressPx = forwardProgress
            reboundStartTime = 0L
        } else if (reboundStartTime == 0L && peakForwardProgressPx - forwardProgress >= REBOUND_THRESHOLD_DP * density) {
            reboundStartTime = SystemClock.uptimeMillis()
        }
        // 首段达标后记录拐点，供第二段判定使用：
        // - 上/下轴：向下/上滑出一小段即可；
        // - 侧轴且锁定方向朝屏幕内侧：向屏幕内侧滑出一小段即可（同样用统一短门槛）。
        if (firstSegmentAnchorX == null) {
            val candidateAxis = axis == LockedSwipeAxis.UP ||
                axis == LockedSwipeAxis.DOWN ||
                (axis == LockedSwipeAxis.SIDE && lockedAxisForwardSign * inwardSignProvider() > 0f)
            if (candidateAxis && forwardProgress >= COMPOUND_FIRST_DP * density) {
                firstSegmentAnchorX = lastX
                firstSegmentAnchorY = lastY
            }
        }
        gestureArmed = retainsGestureCommitment(forwardProgress) &&
            qualifiesAsSwipe(projDx, projDy)
    }

    fun isGestureArmedForTest(): Boolean = gestureArmed

    fun retainsGestureCommitment(forwardProgress: Float): Boolean {
        if (peakForwardProgressPx <= tapSlopPx) {
            return forwardProgress > tapSlopPx
        }
        return forwardProgress >= peakForwardProgressPx * GESTURE_CANCEL_RETAIN_FRACTION
    }

    private fun forwardProgressAlongAxis(
        axis: LockedSwipeAxis,
        projDx: Float,
        projDy: Float,
        forwardSign: Float
    ): Float {
        val axisValue = when (axis) {
            LockedSwipeAxis.UP, LockedSwipeAxis.DOWN -> projDy
            LockedSwipeAxis.SIDE -> projDx
        }
        return axisValue * forwardSign
    }

    private fun forwardProgressAlongLockedAxis(projDx: Float, projDy: Float): Float {
        val axis = lockedSwipeAxis ?: return 0f
        return forwardProgressAlongAxis(axis, projDx, projDy, lockedAxisForwardSign)
    }

    private fun shouldCommitSwipeGesture(projDx: Float, projDy: Float): Boolean {
        if (selectionCancelActive) return false
        if (isPickCommitLocked() || longPressFired) return false
        if (!qualifiesAsSwipe(projDx, projDy)) return false
        return retainsGestureCommitment(forwardProgressAlongLockedAxis(projDx, projDy))
    }

    fun resolveSwipeAxis(dx: Float, dy: Float): LockedSwipeAxis? {
        if (!qualifiesAsSwipe(dx, dy)) return null
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> LockedSwipeAxis.UP
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> LockedSwipeAxis.DOWN
            absDx > absDy * DIRECTION_RATIO -> LockedSwipeAxis.SIDE
            else -> null
        }
    }

    fun projectedDisplacement(
        totalDx: Float,
        totalDy: Float,
        lockedAxis: LockedSwipeAxis?
    ): Pair<Float, Float> {
        return when (lockedAxis) {
            LockedSwipeAxis.UP, LockedSwipeAxis.DOWN -> 0f to totalDy
            LockedSwipeAxis.SIDE -> totalDx to 0f
            null -> totalDx to totalDy
        }
    }

    private fun projectedDisplacement(totalDx: Float, totalDy: Float): Pair<Float, Float> =
        projectedDisplacement(totalDx, totalDy, lockedSwipeAxis)

    private fun qualifiesAsSwipe(dx: Float, dy: Float): Boolean {
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (hypot(dx, dy) <= tapSlopPx) return false
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> true
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> true
            absDx > absDy * DIRECTION_RATIO -> true
            else -> false
        }
    }

    fun predictSwipeGesture(dx: Float, dy: Float): FloatBallGestureType? {
        if (selectionCancelActive) return null
        if (isPickCommitLocked() || longPressFired) return null
        if (!qualifiesAsSwipe(dx, dy)) return null
        return classifySwipe(dx, dy)
    }

    private fun returnGestureForAxis(axis: LockedSwipeAxis): FloatBallGestureType = when (axis) {
        LockedSwipeAxis.UP -> FloatBallGestureType.SWIPE_UP_RETURN
        LockedSwipeAxis.DOWN -> FloatBallGestureType.SWIPE_DOWN_RETURN
        LockedSwipeAxis.SIDE -> FloatBallGestureType.SWIPE_SIDE_RETURN
    }

    private fun shortThresholdForAxis(axis: LockedSwipeAxis): Float = when (axis) {
        LockedSwipeAxis.UP -> upSwipeShortPx
        LockedSwipeAxis.DOWN -> downSwipeShortPx
        LockedSwipeAxis.SIDE -> sideSwipeShortPx
    }

    fun classifyReturnGesture(): FloatBallGestureType? {
        if (selectionCancelActive) return null
        if (isPickCommitLocked() || longPressFired) return null
        val axis = lockedSwipeAxis ?: return null
        val returnType = returnGestureForAxis(axis)
        val action = configuredActions[returnType] ?: GestureAction.None
        if (!action.isEffective()) return null

        val shortThreshold = shortThresholdForAxis(axis)
        if (peakForwardProgressPx < shortThreshold) return null

        val (projDx, projDy) = projectedDisplacement(lastX - downX, lastY - downY, axis)
        val currentForward = forwardProgressAlongAxis(axis, projDx, projDy, lockedAxisForwardSign)
        val reverseDistance = peakForwardProgressPx - currentForward
        val reboundThreshold = REBOUND_THRESHOLD_DP * density
        if (reverseDistance < reboundThreshold) return null

        val now = SystemClock.uptimeMillis()
        val heldAtOrigin = reboundStartTime > 0L &&
            now - reboundStartTime >= REBOUND_CANCEL_HOLD_MS &&
            hypot(lastX - downX, lastY - downY) <= tapSlopPx * 2.5f
        if (heldAtOrigin) return null

        return returnType
    }

    /**
     * 混合手势，两类方向相反的两段式（首段都只需滑出一小段 [COMPOUND_FIRST_DP]）：
     * - 先上/下滑再向内：第二段向内滑够 [COMPOUND_TURN_DP] 且以内向为主；
     * - 先向内再上/下滑：第二段以纵向为主且滑够 [COMPOUND_TURN_DP]。
     *
     * 未配置对应动作、首段未达标、或整体已超出取词时间窗时均不触发（回落为普通滑动）。
     */
    fun classifyCompoundGesture(): FloatBallGestureType? {
        if (selectionCancelActive) return null
        if (isPickCommitLocked() || longPressFired) return null
        val axis = lockedSwipeAxis ?: return null
        val anchorX = firstSegmentAnchorX ?: return null
        val anchorY = firstSegmentAnchorY ?: return null
        val inwardSign = inwardSignProvider()
        val type: FloatBallGestureType
        if (axis == LockedSwipeAxis.SIDE) {
            // 「先向内再上/下滑」：第二段必须是纵向上/下滑为主。
            val inwardFirst = lockedAxisForwardSign * inwardSign > 0f
            if (!inwardFirst || peakForwardProgressPx < COMPOUND_FIRST_DP * density) return null
            val secondAlong = lastY - anchorY
            val dominant = abs(secondAlong)
            if (dominant < COMPOUND_TURN_DP * density) return null
            val extraInward = (lastX - anchorX) * inwardSign
            if (extraInward > dominant * COMPOUND_MAX_ALONG_RATIO) return null
            type = if (secondAlong < 0f) {
                FloatBallGestureType.SWIPE_IN_UP
            } else {
                FloatBallGestureType.SWIPE_IN_DOWN
            }
        } else {
            // 「先上/下滑再向内」：第二段必须是向内为主。
            type = when (axis) {
                LockedSwipeAxis.DOWN -> FloatBallGestureType.SWIPE_DOWN_IN
                LockedSwipeAxis.UP -> FloatBallGestureType.SWIPE_UP_IN
                LockedSwipeAxis.SIDE -> return null
            }
            if (peakForwardProgressPx < COMPOUND_FIRST_DP * density) return null
            val secondInward = (lastX - anchorX) * inwardSign
            if (secondInward < COMPOUND_TURN_DP * density) return null
            val secondAlong = abs(lastY - anchorY)
            if (secondAlong > secondInward * COMPOUND_MAX_ALONG_RATIO) return null
        }
        val action = configuredActions[type] ?: GestureAction.None
        if (!action.isEffective()) return null
        return type
    }

    private fun emitGestureHint(totalDx: Float, totalDy: Float) {
        // 取消选中后本次手势不再显示/触发任何滑动手势。
        if (selectionCancelActive) {
            onGestureHint?.invoke(null)
            return
        }
        // 移动模式：只跟手挪球，不预测/不显示任何手势提示（用户会看到"手势图标"跳出来）。
        if (moveModeActive) {
            onGestureHint?.invoke(null)
            return
        }
        val returnGesture = classifyReturnGesture()
        if (returnGesture != null) {
            onGestureHint?.invoke(returnGesture)
            return
        }
        val compoundGesture = classifyCompoundGesture()
        if (compoundGesture != null) {
            onGestureHint?.invoke(compoundGesture)
            return
        }
        if (!gestureArmed) {
            onGestureHint?.invoke(null)
            return
        }
        val (dx, dy) = projectedDisplacement(totalDx, totalDy)
        onGestureHint?.invoke(predictSwipeGesture(dx, dy))
    }

    private fun classifySwipe(dx: Float, dy: Float): FloatBallGestureType? {
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDy > absDx * DIRECTION_RATIO && dy < 0f -> {
                if (absDy > upSwipeShortPx) {
                    FloatBallGestureType.SWIPE_UP_LONG
                } else {
                    FloatBallGestureType.SWIPE_UP_SHORT
                }
            }
            absDy > absDx * DIRECTION_RATIO && dy > 0f -> {
                if (dy > downSwipeShortPx) {
                    FloatBallGestureType.SWIPE_DOWN_LONG
                } else {
                    FloatBallGestureType.SWIPE_DOWN_SHORT
                }
            }
            absDx > absDy * DIRECTION_RATIO -> {
                if (absDx > sideSwipeShortPx) {
                    FloatBallGestureType.SWIPE_SIDE_LONG
                } else {
                    FloatBallGestureType.SWIPE_SIDE_SHORT
                }
            }
            else -> null
        }
    }

    private fun cancelPendingSingleTap() {
        handler.removeCallbacks(singleTapRunnable)
        pendingSingleTap = false
    }

    private fun resetTouchSession() {
        launcherCaptureMode = false
        longPressFired = false
        movedBeyondSlop = false
        pickActive = false
        pickDragStarted = false
        pickDragStartTime = 0L
        pickGestureLocked = false
        pausePickLocked = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        selectionCancelActive = false
        ignoreMultiTouchTail = false
        lockedSwipeAxis = null
        lockedAxisForwardSign = 0f
        gestureArmed = false
        peakForwardProgressPx = 0f
        reboundStartTime = 0L
        firstSegmentAnchorX = null
        firstSegmentAnchorY = null
        gesturePath.clear()
    }

    private fun swipeThresholdPx(percent: Float, density: Float): Float =
        (percent.coerceIn(50f, 500f) * SWIPE_BASE_DP * density) / 100f
}
