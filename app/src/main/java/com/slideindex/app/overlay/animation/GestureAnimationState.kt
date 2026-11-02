package com.slideindex.app.overlay.animation

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.perf.PerfProbe
import com.slideindex.app.settings.WaveStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Animation state ported from SideGesture [SideGestureState] — position tracking only.
 */
class GestureAnimationState(
    private val scope: CoroutineScope,
    private val side: PanelSide,
) {
    var button: GestureAnimationButton? by mutableStateOf(null)
        private set
    var triggerDirection: GestureAnimationTriggerDirection by mutableStateOf(GestureAnimationTriggerDirection.Center2)
        private set
    var swipeDirection: SwipeDirection? by mutableStateOf(null)
        private set
    var currentTrigger: GestureTriggerType? by mutableStateOf(null)
        private set
    var currentDistancePx: Float by mutableFloatStateOf(0f)
        private set
    var isActive by mutableStateOf(false)
        private set

    /**
     * 动画帧计数器：Canvas 读它来触发重组。
     *
     * 由 [scheduleRedrawFrame] 在**每个 Choreographer 帧**最多递增一次，而不是每个触摸事件递增：
     * 同一帧内到达的多个 MOVE 只重组一次。实测（Perfetto，120Hz）拖动期同一帧最多 2 个 MOVE，
     * 按事件重组会让全屏 Canvas 重组次数翻倍。
     */
    internal var redrawTick by mutableIntStateOf(0)
        private set

    /**
     * 绘制进度的时间入场系数。
     *
     * 恒为 1：按下瞬间画出来的进度就等于实际位移。原先启动后 100ms 内从 0 拉满，
     * 手指只动几像素时几乎不可见，观感像"按下后还在等"。
     */
    internal val entryRamp: Float get() = 1f

    private var origin = Offset.Unspecified
    private var finger = Offset.Unspecified

    val originXAnimVal: Float get() = originXAnim.value
    val originYAnimVal: Float get() = originYAnim.value
    val fingerXAnimVal: Float get() = fingerXAnim.value
    val fingerYAnimVal: Float get() = fingerYAnim.value

    private val originXAnim = Animatable(Float.NaN)
    private val originYAnim = Animatable(Float.NaN)
    private val fingerXAnim = Animatable(Float.NaN)
    private val fingerYAnim = Animatable(Float.NaN)

    private val animationSpec = spring<Float>(stiffness = 3000f)
    private var animJob: Job? = null

    /** 兜底关 ticker 用：协程可能在任意线程完成，而 Choreographer 只接受主线程操作。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 帧驱动的重绘泵。
     *
     * 只在"有值没画、且本帧还没排帧"时挂一次回调，保证**每帧最多一次**
     * `redrawTick++`（即每帧最多一次全屏 Canvas 重组），同时不丢任何一帧的视觉更新。
     */
    private lateinit var choreographer: Choreographer
    private var frameScheduled = false
    private var redrawPending = false

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        if (redrawPending) {
            redrawPending = false
            redrawTick++
            // 拖动期会不停有新值进来；这里不主动续帧，等下一次值变化再排，避免空转。
        }
    }

    private fun scheduleRedrawFrame() {
        if (!::choreographer.isInitialized) {
            choreographer = Choreographer.getInstance()
        }
        redrawPending = true
        if (frameScheduled) return
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun cancelRedrawFrame() {
        redrawPending = false
        if (frameScheduled && ::choreographer.isInitialized) {
            choreographer.removeFrameCallback(frameCallback)
        }
        frameScheduled = false
    }

    // ---- 收束动画期间：整段动画需要逐帧重绘（animateTo 的值变化不会自动触发重组）----

    private var animTickerRunning = false

    /**
     * 收束动画已跑的帧数，用来给 ticker 兜底。
     *
     * 正常情况下 `animateTo` 200ms 内就结束；一旦它不收敛（例如起点是 NaN，弹簧永远不会
     * 满足收敛条件），ticker 会每帧 `redrawTick++` → 全屏 Canvas 重组，把主线程钉死，
     * 且永远等不到 `finally` 里的 [stopAnimTicker]。到上限就强制停。
     */
    private var animTickerFrames = 0

    private val animTickCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animTickerRunning) return
            redrawTick++
            if (++animTickerFrames >= MAX_ANIM_TICKER_FRAMES) {
                stopAnimTicker()
                return
            }
            if (::choreographer.isInitialized) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    private fun startAnimTicker() {
        if (animTickerRunning) return
        if (!::choreographer.isInitialized) {
            choreographer = Choreographer.getInstance()
        }
        animTickerRunning = true
        animTickerFrames = 0
        choreographer.postFrameCallback(animTickCallback)
    }

    private fun stopAnimTicker() {
        if (!animTickerRunning) return
        animTickerRunning = false
        if (::choreographer.isInitialized) {
            choreographer.removeFrameCallback(animTickCallback)
        }
    }


    var shortTriggerDistancePx: Float = 0f
    var longTriggerDistancePx: Float = 0f
    var stickySlideEnabled: Boolean = false
    var stickySlidePx: Float = 0f

    var hintFingerOffsetPx: Float = 0f

    /** 仅用于绘制：相对手指的视觉偏移（屏幕 Y）；触发改动仍按真实手指位置。 */
    fun displayYOffset(position: GestureAnimationPosition): Float {
        if (hintFingerOffsetPx <= 0f) return 0f
        return when (position) {
            GestureAnimationPosition.Top -> hintFingerOffsetPx
            GestureAnimationPosition.Left,
            GestureAnimationPosition.Right,
            GestureAnimationPosition.Bottom,
            -> -hintFingerOffsetPx
        }
    }

    fun onDragStart(rawX: Float, rawY: Float) {
        animJob?.cancel()
        isActive = true
        val position = GestureAnimationPosition.fromPanelSide(side)
        button = GestureAnimationButton(position)
        origin = Offset(rawX, rawY)
        finger = Offset(rawX, rawY)
        triggerDirection = GestureAnimationTriggerDirection.Center2
        swipeDirection = null
        currentTrigger = null
        currentDistancePx = 0f

        scope.launch {
            originXAnim.snapTo(rawX)
            originYAnim.snapTo(rawY)
            when (position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                    fingerXAnim.snapTo(stickySlideOffset(position, horizontal = true))
                    fingerYAnim.snapTo(rawY)
                }
                GestureAnimationPosition.Bottom -> {
                    fingerXAnim.snapTo(rawX)
                    fingerYAnim.snapTo(stickySlideOffset(position, horizontal = false))
                }
                GestureAnimationPosition.Top -> {
                    fingerXAnim.snapTo(rawX)
                    fingerYAnim.snapTo(stickySlideOffset(position, horizontal = false))
                }
            }
        }
        scheduleRedrawFrame()
    }

    fun onDrag(
        rawX: Float,
        rawY: Float,
        swipeDirection: SwipeDirection?,
        inwardPx: Float,
        currentTrigger: GestureTriggerType? = null,
        currentDistancePx: Float = 0f,
    ) {
        if (!isActive) return
        PerfProbe.probe("Edge.animState.onDrag") {
            val dragAmount = Offset(rawX - finger.x, rawY - finger.y)
            finger = Offset(rawX, rawY)

            val longDistance = inwardPx >= longTriggerDistancePx || currentDistancePx >= longTriggerDistancePx
            this.swipeDirection = swipeDirection
            this.currentTrigger = currentTrigger
            this.currentDistancePx = currentDistancePx
            triggerDirection = swipeDirection.toGestureTriggerDirection(longDistance)

            // 拖动跟手是硬实时路径：不再抢互斥锁（原实现每个 MOVE 都要 animMutex.withLock），
            // 只提交数值更新。重绘统一交给帧泵，保证同一帧内多个 MOVE 只触发一次全屏重组。
            scope.launch {
                PerfProbe.probeSuspend("Edge.animState.snapTo") {
                    fingerXAnim.snapTo(fingerXAnimVal + dragAmount.x)
                    fingerYAnim.snapTo(fingerYAnimVal + dragAmount.y)
                }
            }
            scheduleRedrawFrame()
        }
    }

    fun onDragEnd() {
        reset(endInteraction = true)
    }

    fun onDragCancel() {
        reset(endInteraction = true)
    }

    /** 无收束动画地清除（单击等不应出现手势提示时）。 */
    fun cancelSilently() {
        animJob?.cancel()
        isActive = false
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        triggerDirection = GestureAnimationTriggerDirection.Center2
        swipeDirection = null
        currentTrigger = null
        currentDistancePx = 0f
        button = null
        scope.launch {
            originXAnim.snapTo(Float.NaN)
            originYAnim.snapTo(Float.NaN)
            fingerXAnim.snapTo(Float.NaN)
            fingerYAnim.snapTo(Float.NaN)
        }
        cancelRedrawFrame()
        redrawTick++
    }

    private fun reset(endInteraction: Boolean) {
        if (endInteraction) {
            isActive = false
        }
        origin = Offset.Unspecified
        finger = Offset.Unspecified

        val position = button?.position ?: run {
            triggerDirection = GestureAnimationTriggerDirection.Center2
            swipeDirection = null
            currentTrigger = null
            currentDistancePx = 0f
            clearAnimValues()
            return
        }
        // 起点是 NaN 时弹簧永远不收敛，会把 ticker 拖到上限；这种状态本来也没有可收束的画面。
        if (fingerXAnimVal.isNaN() || fingerYAnimVal.isNaN()) {
            clearAnimValues()
            return
        }
        animJob?.cancel()
        startAnimTicker()
        val job = scope.launch {
            try {
                when (position) {
                    GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                        fingerXAnim.animateTo(0f, animationSpec)
                        fingerYAnim.animateTo(originYAnimVal, animationSpec)
                    }
                    GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> {
                        fingerYAnim.animateTo(0f, animationSpec)
                        fingerXAnim.animateTo(originXAnimVal, animationSpec)
                    }
                }
                redrawTick++
            } finally {
                stopAnimTicker()
            }
            triggerDirection = GestureAnimationTriggerDirection.Center2
            swipeDirection = null
            currentTrigger = null
            currentDistancePx = 0f
            clearAnimValues()
        }
        animJob = job
        // 协程体可能压根没执行（scope 已取消），那样 finally 不会跑、ticker 就没人关。
        // 这里按「任务结束」兜底，并且只在它仍是当前任务时才关，避免误杀下一次收束的 ticker。
        job.invokeOnCompletion {
            mainHandler.post {
                if (animJob === job) stopAnimTicker()
            }
        }
    }

    private fun clearAnimValues() {
        stopAnimTicker()
        scope.launch {
            originXAnim.snapTo(Float.NaN)
            originYAnim.snapTo(Float.NaN)
            fingerXAnim.snapTo(Float.NaN)
            fingerYAnim.snapTo(Float.NaN)
        }
        cancelRedrawFrame()
        redrawTick++
        button = null
    }

    fun canDistanceTriggered(target: GestureAnimationButton, isLongSlide: Boolean): Boolean {
        if (!isActive) return false
        val threshold = if (isLongSlide) longTriggerDistancePx else shortTriggerDistancePx
        if (currentDistancePx >= threshold) return true
        if (currentTrigger?.isReturnSwipe == true) {
            return !isLongSlide
        }
        if (currentTrigger?.isCornerSwipe == true) {
            return if (isLongSlide) currentTrigger?.isLongDistance == true else true
        }
        val originX = origin.x
        val originY = origin.y
        val fingerX = finger.x + stickySlideOffset(target.position, horizontal = true)
        val fingerY = finger.y + stickySlideOffset(target.position, horizontal = false)
        val direction = triggerDirection

        if (direction == GestureAnimationTriggerDirection.Center2) {
            return false
        }

        val slideDistance = slideDistanceFor(
            position = target.position,
            direction = direction,
            originX = originX,
            originY = originY,
            fingerX = fingerX,
            fingerY = fingerY,
        )

        if (slideDistance < 0 &&
            !direction.isDiagonalAlongEdge(target.position)
        ) {
            return false
        }

        return when (direction) {
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                slideDistance >= threshold
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Down,
            GestureAnimationTriggerDirection.Up2, GestureAnimationTriggerDirection.Down2,
            -> {
                val edge2 = secondarySlideDistanceFor(
                    position = target.position,
                    direction = direction,
                    originX = originX,
                    originY = originY,
                    fingerX = fingerX,
                    fingerY = fingerY,
                )
                hypot(slideDistance.toDouble(), edge2.toDouble()) >= threshold
            }
            GestureAnimationTriggerDirection.Click -> false
        }
    }

    private fun slideDistanceFor(
        position: GestureAnimationPosition,
        direction: GestureAnimationTriggerDirection,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
    ): Float = when (position) {
        GestureAnimationPosition.Top, GestureAnimationPosition.Bottom -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originX - fingerX
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerX - originX
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                if (position == GestureAnimationPosition.Top) {
                    fingerY - originY
                } else {
                    originY - fingerY
                }
            GestureAnimationTriggerDirection.Click -> 0f
        }
        GestureAnimationPosition.Left -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originY - fingerY
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerY - originY
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                fingerX - originX
            GestureAnimationTriggerDirection.Click -> 0f
        }
        GestureAnimationPosition.Right -> when (direction) {
            GestureAnimationTriggerDirection.Up, GestureAnimationTriggerDirection.Up2 ->
                originY - fingerY
            GestureAnimationTriggerDirection.Down, GestureAnimationTriggerDirection.Down2 ->
                fingerY - originY
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                originX - fingerX
            GestureAnimationTriggerDirection.Click -> 0f
        }
    }

    private fun secondarySlideDistanceFor(
        position: GestureAnimationPosition,
        direction: GestureAnimationTriggerDirection,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
    ): Float = when (position) {
        GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
            abs(fingerY - originY)
        GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> when (direction) {
            GestureAnimationTriggerDirection.Center, GestureAnimationTriggerDirection.Center2 ->
                abs(fingerX - originX)
            else ->
                if (position == GestureAnimationPosition.Top) {
                    abs(fingerY - originY)
                } else {
                    abs(fingerY - originY)
                }
        }
    }

    private fun GestureAnimationTriggerDirection.isDiagonalAlongEdge(
        position: GestureAnimationPosition,
    ): Boolean = when (position) {
        GestureAnimationPosition.Top, GestureAnimationPosition.Bottom,
        GestureAnimationPosition.Left, GestureAnimationPosition.Right,
        ->
            this == GestureAnimationTriggerDirection.Up2 ||
                this == GestureAnimationTriggerDirection.Down2
    }

    fun applyWaveStyle(waveStyle: WaveStyle) {
        stickySlideEnabled = waveStyle.stickySlideEnabled
        stickySlidePx = if (waveStyle.stickySlideEnabled) waveStyle.stickySlidePx.toFloat() else 0f
    }

    private fun stickySlideOffset(position: GestureAnimationPosition, horizontal: Boolean): Float {
        if (!stickySlideEnabled || stickySlidePx <= 0f) return 0f
        return if (horizontal) {
            when (position) {
                GestureAnimationPosition.Left -> -stickySlidePx
                GestureAnimationPosition.Right -> stickySlidePx
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> stickySlidePx
            }
        } else {
            when (position) {
                GestureAnimationPosition.Bottom -> stickySlidePx
                GestureAnimationPosition.Top -> -stickySlidePx
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> stickySlidePx
            }
        }
    }
}

/**
 * 收束动画 ticker 的硬上限：120Hz 下约 3 秒、60Hz 下约 6 秒。
 *
 * 正常收束（spring stiffness 3000）200ms 内就结束，跑满上限只可能是 `animateTo` 没收敛。
 */
private const val MAX_ANIM_TICKER_FRAMES = 360
