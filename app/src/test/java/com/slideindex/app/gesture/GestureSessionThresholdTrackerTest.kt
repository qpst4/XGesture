package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], instrumentedPackages = ["com.slideindex.app.gesture"])
class GestureSessionThresholdTrackerTest {
    private lateinit var pathRecognizer: SwipePathRecognizer
    private val leftStrip = RectF(0f, 0f, 20f, 2000f)
    private var gestureStartCount = 0
    private var longThresholdCount = 0
    private var cancelLongPressCount = 0
    private lateinit var tracker: GestureSessionThresholdTracker

    private val callbacks = object : GestureSession.Callbacks {
        override fun onSessionStart(mode: com.slideindex.app.overlay.OverlayPanelMode) = Unit
        override fun onOpenShellCommandPanel(continuousPick: Boolean) = Unit
        override fun onShellCommandPanelContinuousRelease() = Unit
        override fun onShowHoneycombLauncher(
            continuousPick: Boolean,
            rawX: Float,
            rawY: Float,
            forceBrowseMode: Boolean,
        ): Boolean = true
        override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) = Unit
        override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
        override fun onShowRingLauncher(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
        override fun onRingLauncherPointerMove(rawX: Float, rawY: Float) = Unit
        override fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
        override fun onShowFingertipRing(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
        override fun onFingertipRingPointerMove(rawX: Float, rawY: Float) = Unit
        override fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float) = Unit
        override fun onShowAdjustPanel(
            mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
            fraction: Float,
            anchorRawY: Float,
            deferWindowLayout: Boolean,
        ) = Unit
        override fun onSessionEnd() = Unit
        override fun onRequestInvalidate() = Unit
        override fun hapticGestureStart() {
            gestureStartCount++
        }
        override fun hapticLongThreshold() {
            longThresholdCount++
        }
        override fun hapticConfirmLaunch() = Unit
        override fun scheduleDelayed(runnable: Runnable, delayMs: Long) = Unit
        override fun cancelDelayed(runnable: Runnable) = Unit
    }

    @Before
    fun setUp() {
        pathRecognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f).apply {
            applyDistances(shortDp = 60f, longDp = 120f)
            applyAngles(GestureAngles())
            onTouchDown(0f, 0f, leftStrip)
        }
        gestureStartCount = 0
        longThresholdCount = 0
        cancelLongPressCount = 0
        tracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { cancelLongPressCount++ },
        )
    }

    @Test
    fun trackDistanceHaptics_firesStartOnceWhenCrossingShortThreshold() {
        tracker.trackDistanceHaptics(30f, 0f)
        assertEquals(0, gestureStartCount)
        assertEquals(0, cancelLongPressCount)

        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(1, gestureStartCount)
        assertEquals(1, cancelLongPressCount)

        tracker.trackDistanceHaptics(80f, 0f)
        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_firesLongThresholdOnceWhenCrossingLongThreshold() {
        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(0, longThresholdCount)

        tracker.trackDistanceHaptics(130f, 0f)
        assertEquals(1, longThresholdCount)

        tracker.trackDistanceHaptics(140f, 0f)
        assertEquals(1, longThresholdCount)
    }

    @Test
    fun trackDistanceHaptics_dipBelowShortThresholdThenCrossAgain_doesNotFireAgain() {
        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(1, gestureStartCount)

        // 本段峰值只增不减：回退时"距离回升"不再重复提示（退回起点清零后才允许再响）。
        tracker.trackDistanceHaptics(50f, 0f)
        tracker.trackDistanceHaptics(65f, 0f)

        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_backToStartThenCrossAgain_firesAgain() {
        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(1, gestureStartCount)

        // 退回起手位置（撤销清零）后再重新滑过门槛 → 再次提示。
        pathRecognizer.onTouchMove(4f, 0f)
        tracker.trackDistanceHaptics(4f, 0f)
        pathRecognizer.onTouchMove(70f, 0f)
        tracker.trackDistanceHaptics(70f, 0f)

        assertEquals(2, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_topStripSwipeFromInsideStrip_firesStartOnlyOnce() {
        // 顶部触钮的触摸窗口高约 181px，手指按下点距屏幕顶边就有 ~150px。
        // 「退回起手位置」必须按相对位移判断；一旦混入起手点的绝对深度，
        // 整段前 (起手深度 + 12dp) 的滑动都会被当成撤销、反复清零，
        // 于是每帧重新越过短滑阈值 → 每帧补一声 GESTURE_START（真机表现出来就是"一直震"）。
        val topStrip = RectF(0f, 0f, 1080f, 181f)
        val topRecognizer = SwipePathRecognizer(PanelSide.TOP, density = 2.75f).apply {
            applyDistances(shortDp = 60f, longDp = 120f)
            applyAngles(GestureAngles())
            onTouchDown(800f, 150f, topStrip)
            applyCompoundGestureGate(SwipePathRecognizer.ClassifyOptions.DEFAULT)
        }
        val topTracker = GestureSessionThresholdTracker(
            pathRecognizer = topRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { },
            isTriggerConfigured = { false },
        )

        // 从 y=150 平滑下滑到 430（向内 280px < 长滑 120dp×2.75=330px）。
        var y = 150f
        while (y <= 430f) {
            topRecognizer.onTouchMove(800f, y)
            topTracker.trackDistanceHaptics(800f, y)
            y += 4f
        }

        assertEquals(1, gestureStartCount)
        assertEquals(0, longThresholdCount)
    }

    @Test
    fun maybeHapticLongPress_firesOnceWhenLongPressArmed() {
        pathRecognizer.onTouchDown(0f, 0f, leftStrip)
        ShadowSystemClock.advanceBy(SwipePathRecognizer.LONG_PRESS_MS + 50L, TimeUnit.MILLISECONDS)

        tracker.maybeHapticLongPress(0f, 0f)
        assertEquals(1, longThresholdCount)

        tracker.maybeHapticLongPress(0f, 0f)
        assertEquals(1, longThresholdCount)
    }

    @Test
    fun reset_allowsShortThresholdHapticAgain() {
        tracker.trackDistanceHaptics(70f, 0f)
        tracker.reset()
        pathRecognizer.onTouchDown(0f, 0f, leftStrip)

        tracker.trackDistanceHaptics(70f, 0f)

        assertEquals(2, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_unconfiguredCornerResolved_doesNotFireCompoundHaptic() {
        // 几何上转向成立（第二段沿边为主 ≥32dp），但该组合槽位没配置动作：
        // 不应该给它响"第二段短距"的震动（幽灵震动），而应走第一段分档。
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.SHORT_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_AND_BACK
            },
        )
        val localTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { },
            isTriggerConfigured = { trigger -> options.isTriggerConfigured?.invoke(trigger) == true },
        )
        pathRecognizer.applyDistances(shortDp = 60f, longDp = 120f)
        pathRecognizer.applyAngles(GestureAngles())
        pathRecognizer.onTouchDown(0f, 100f, leftStrip)
        pathRecognizer.applyCompoundGestureGate(options)
        pathRecognizer.onTouchMove(80f, 100f)
        localTracker.trackDistanceHaptics(80f, 100f) // 已达短距：1 声
        assertEquals(1, gestureStartCount)

        pathRecognizer.onTouchMove(80f, 40f) // 第二段沿边 60dp：几何上像 L 形
        localTracker.trackDistanceHaptics(80f, 40f)

        // 未配置的 L 形不再单独补一声（第二段的 32dp 门槛震动不响）。
        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_whenReturnConfigured_firesHapticOnReturn() {
        var returnConfigured = true
        val configuredTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = object : GestureSession.Callbacks {
                override fun onSessionStart(mode: com.slideindex.app.overlay.OverlayPanelMode) = Unit
                override fun onOpenShellCommandPanel(continuousPick: Boolean) = Unit
                override fun onShellCommandPanelContinuousRelease() = Unit
                override fun onShowHoneycombLauncher(
                    continuousPick: Boolean,
                    rawX: Float,
                    rawY: Float,
                    forceBrowseMode: Boolean,
                ): Boolean = true
                override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowRingLauncher(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
                override fun onRingLauncherPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowFingertipRing(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
                override fun onFingertipRingPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowAdjustPanel(
                    mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
                    fraction: Float,
                    anchorRawY: Float,
                    deferWindowLayout: Boolean,
                ) = Unit
                override fun onSessionEnd() = Unit
                override fun onRequestInvalidate() = Unit
                override fun hapticGestureStart() {
                    gestureStartCount++
                }
                override fun hapticLongThreshold() {
                    longThresholdCount++
                }
                override fun hapticConfirmLaunch() = Unit
                override fun scheduleDelayed(runnable: Runnable, delayMs: Long) = Unit
                override fun cancelDelayed(runnable: Runnable) = Unit
            },
            cancelLongPressCheck = { },
            isTriggerConfigured = { returnConfigured },
        )

        // Slide in to 80dp (crosses 60dp short threshold) -> fires 1st haptic
        pathRecognizer.onTouchMove(80f, 0f)
        configuredTracker.trackDistanceHaptics(80f, 0f)
        assertEquals(1, gestureStartCount)

        // Retract to 40dp (retraction = 40dp >= 28dp) -> fires 2nd haptic for return
        pathRecognizer.onTouchMove(40f, 0f)
        configuredTracker.trackDistanceHaptics(40f, 0f)
        assertEquals(2, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_whenReturnUnconfigured_doesNotFireHapticOnReturn() {
        val unconfiguredTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = object : GestureSession.Callbacks {
                override fun onSessionStart(mode: com.slideindex.app.overlay.OverlayPanelMode) = Unit
                override fun onOpenShellCommandPanel(continuousPick: Boolean) = Unit
                override fun onShellCommandPanelContinuousRelease() = Unit
                override fun onShowHoneycombLauncher(
                    continuousPick: Boolean,
                    rawX: Float,
                    rawY: Float,
                    forceBrowseMode: Boolean,
                ): Boolean = true
                override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowRingLauncher(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
                override fun onRingLauncherPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowFingertipRing(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean = true
                override fun onFingertipRingPointerMove(rawX: Float, rawY: Float) = Unit
                override fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float) = Unit
                override fun onShowAdjustPanel(
                    mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
                    fraction: Float,
                    anchorRawY: Float,
                    deferWindowLayout: Boolean,
                ) = Unit
                override fun onSessionEnd() = Unit
                override fun onRequestInvalidate() = Unit
                override fun hapticGestureStart() {
                    gestureStartCount++
                }
                override fun hapticLongThreshold() {
                    longThresholdCount++
                }
                override fun hapticConfirmLaunch() = Unit
                override fun scheduleDelayed(runnable: Runnable, delayMs: Long) = Unit
                override fun cancelDelayed(runnable: Runnable) = Unit
            },
            cancelLongPressCheck = { },
            isTriggerConfigured = { false }, // Return gesture is unconfigured
        )

        // Slide in to 80dp -> fires 1st haptic
        unconfiguredTracker.trackDistanceHaptics(80f, 0f)
        assertEquals(1, gestureStartCount)

        // Retract to 40dp -> should NOT fire 2nd haptic
        unconfiguredTracker.trackDistanceHaptics(40f, 0f)
        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_inwardReturnUnconfiguredAlongReturnConfigured_doesNotFireGhostHaptic() {
        // 复现"幽灵震动"：向内折返设成「无」，但沿边折返还配着动作。
        // 早前震动闸门是三个折返槽位「任一配置即开」，于是向内折返时也白响一声；
        // 识别器那边会过滤掉未配置的折返（松手只执行基础内滑），震动必须同口径。
        val alongOnlyTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { },
            isTriggerConfigured = { trigger -> trigger == GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK },
        )

        // 向内滑过短距：第 1 声（正常）。
        pathRecognizer.onTouchMove(80f, 0f)
        alongOnlyTracker.trackDistanceHaptics(80f, 0f)
        assertEquals(1, gestureStartCount)

        // 折返回缩：向内折返未配置 → 不应再有第 2 声。
        pathRecognizer.onTouchMove(40f, 0f)
        alongOnlyTracker.trackDistanceHaptics(40f, 0f)
        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_firesHoverHapticAfterHoldWithoutIntermediateMoves() {
        pathRecognizer.applyHoverSettings(durationMs = 250L)
        pathRecognizer.onTouchDown(0f, 100f, leftStrip)
        // hover 只在对应触发已配置时才会进入计时（生产由 applyCompoundGestureGate 传入设置里的动作表）。
        // 注意顺序：onTouchDown 会重置该配置，必须在其之后应用，与 GestureSession.onTouchDown 一致。
        pathRecognizer.applyCompoundGestureGate(
            SwipePathRecognizer.ClassifyOptions(
                isTriggerConfigured = { trigger -> trigger == GestureTriggerType.SHORT_SWIPE_IN_HOVER },
            ),
        )
        pathRecognizer.onTouchMove(80f, 100f)
        tracker.trackDistanceHaptics(80f, 100f)
        assertEquals(1, gestureStartCount)

        // 生产里由 GestureSession.hoverHapticCheckRunnable 在 hover 时长后回调驱动。
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        pathRecognizer.onTouchMove(80f, 100f)
        tracker.trackDistanceHaptics(80f, 100f)

        assertEquals(2, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_firesCompoundSecondSegmentShortAndLongThresholds() {
        pathRecognizer.applyDistances(shortDp = 60f, longDp = 120f)
        pathRecognizer.applyAngles(GestureAngles())
        pathRecognizer.applyHoverSettings(durationMs = 250L)
        pathRecognizer.onTouchDown(0f, 100f, leftStrip)
        pathRecognizer.applyCompoundGestureGate(
            SwipePathRecognizer.ClassifyOptions(
                isTriggerConfigured = { trigger ->
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP ||
                        trigger == GestureTriggerType.LONG_SWIPE_IN_UP ||
                        trigger == GestureTriggerType.SHORT_SWIPE_IN_HOVER
                },
            ),
        )
        pathRecognizer.onTouchMove(80f, 100f)
        tracker.trackDistanceHaptics(80f, 100f)
        assertEquals(1, gestureStartCount)

        // 同 hover 用例：由 hover 定时 tick 在时长到达后判定满足。
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        pathRecognizer.onTouchMove(80f, 100f)
        tracker.trackDistanceHaptics(80f, 100f)
        assertEquals(2, gestureStartCount)
        assertEquals(0, longThresholdCount)

        // 复合第二段的短阈值是距转向锚点 TURN_SLOP_DP(32dp)，(80,60) 距锚点 (80,100) 已有 40dp。
        pathRecognizer.onTouchMove(80f, 60f)
        tracker.trackDistanceHaptics(80f, 60f)
        assertEquals(3, gestureStartCount)
        assertEquals(0, longThresholdCount)

        pathRecognizer.onTouchMove(80f, 20f)
        tracker.trackDistanceHaptics(80f, 20f)
        assertEquals(3, gestureStartCount)
        assertEquals(0, longThresholdCount)

        pathRecognizer.onTouchMove(80f, -40f)
        tracker.trackDistanceHaptics(80f, -40f)
        assertEquals(3, gestureStartCount)
        assertEquals(1, longThresholdCount)
    }
}
