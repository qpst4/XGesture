package com.slideindex.app.overlay

import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class FloatBallGestureDetectorTest {
    @Test
    fun `predictSwipeGesture returns up short before threshold`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureType.SWIPE_UP_SHORT,
            detector.predictSwipeGesture(dx = 0f, dy = -80f),
        )
    }

    @Test
    fun `predictSwipeGesture returns up long past threshold`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureType.SWIPE_UP_LONG,
            detector.predictSwipeGesture(dx = 0f, dy = -320f),
        )
    }

    @Test
    fun `predictSwipeGesture returns null for ambiguous diagonal`() {
        val detector = newDetector()
        assertNull(detector.predictSwipeGesture(dx = 80f, dy = -80f))
    }

    @Test
    fun `locked side axis ignores later vertical displacement for hint`() {
        val detector = newDetector()
        val axis = FloatBallGestureDetector.LockedSwipeAxis.SIDE
        val (projDx, projDy) = detector.projectedDisplacement(
            totalDx = 120f,
            totalDy = 200f,
            lockedAxis = axis,
        )
        assertEquals(
            FloatBallGestureType.SWIPE_SIDE_SHORT,
            detector.predictSwipeGesture(projDx, projDy),
        )
        assertEquals(
            FloatBallGestureType.SWIPE_DOWN_SHORT,
            detector.predictSwipeGesture(dx = 120f, dy = 200f),
        )
    }

    @Test
    fun `resolveSwipeAxis locks first qualifying horizontal swipe`() {
        val detector = newDetector()
        assertEquals(
            FloatBallGestureDetector.LockedSwipeAxis.SIDE,
            detector.resolveSwipeAxis(dx = 120f, dy = 30f),
        )
    }

    @Test
    fun `hint layout is top-left of finger when dragging from right edge`() {
        val (x, y) = FloatBallGestureHintWindow.hintTopLeftForFingerPx(
            fingerX = 300f,
            fingerY = 500f,
            dockSide = FloatBallSide.RIGHT,
            hintSizePx = 48,
            gapPx = 48,
        )
        assertEquals(204, x)
        assertEquals(404, y)
    }

    @Test
    fun `hint layout is top-right of finger when dragging from left edge`() {
        val (x, y) = FloatBallGestureHintWindow.hintTopLeftForFingerPx(
            fingerX = 100f,
            fingerY = 500f,
            dockSide = FloatBallSide.LEFT,
            hintSizePx = 48,
            gapPx = 48,
        )
        assertEquals(148, x)
        assertEquals(404, y)
    }

    @Test
    fun `single tap fires immediately when double tap has no action`() {
        var fired: FloatBallGestureType? = null
        // 默认设置里 DOUBLE_TAP = None：不必再等双击窗口，单击当帧就执行。
        val detector = newDetector { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SINGLE_TAP, fired)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `single tap waits for double tap window when double tap has an action`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(FloatBallGestureType.DOUBLE_TAP to GestureAction.Back),
        ) { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(up)
        assertNull(fired)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.SINGLE_TAP, fired)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `drag away and back to origin does not fire single tap`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveAway = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 300f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveAway)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertNull(fired)
        down.recycle()
        moveAway.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `double tap cancels pending single tap`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(FloatBallGestureType.DOUBLE_TAP to GestureAction.Back),
        ) { type, _, _, _, _ -> fired = type }
        val firstDown = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val firstUp = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        val secondDown = MotionEvent.obtain(0, 100, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val secondUp = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(firstDown)
        detector.onTouchEvent(firstUp)
        detector.onTouchEvent(secondDown)
        detector.onTouchEvent(secondUp)
        assertEquals(FloatBallGestureType.DOUBLE_TAP, fired)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.DOUBLE_TAP, fired)
        firstDown.recycle()
        firstUp.recycle()
        secondDown.recycle()
        secondUp.recycle()
    }

    @Test
    fun `swipe down then reverse before release does not fire swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 250f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        assertNull(fired)
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `swipe down then reverse with return action configured fires SWIPE_DOWN_RETURN`() {
        var fired: FloatBallGestureType? = null
        val settings = AppSettings(
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            floatBall = AppSettings().floatBall.copy(
                floatBallGestureActions = mapOf(
                    FloatBallGestureType.SWIPE_DOWN_RETURN to com.slideindex.app.gesture.GestureAction.Back,
                ),
            ),
        )
        val detector = newDetector(
            settings = settings,
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        // 默认 DOWN 短滑阈值 = floatBallDownSwipeShortPercent(200) × SWIPE_BASE_DP(40dp) × 3 = 240px
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 500f, 0) // 300px >= 240px
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 350f, 0) // 回退 150px >= 28dp(84px)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_RETURN, fired)
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `swipe side then reverse with return action configured fires SWIPE_SIDE_RETURN`() {
        var fired: FloatBallGestureType? = null
        val settings = AppSettings(
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            floatBall = AppSettings().floatBall.copy(
                floatBallGestureActions = mapOf(
                    FloatBallGestureType.SWIPE_SIDE_RETURN to com.slideindex.app.gesture.GestureAction.Back,
                ),
            ),
        )
        val detector = newDetector(
            settings = settings,
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        // 默认 SIDE 短滑阈值 = floatBallSideSwipeShortPercent(320) × SWIPE_BASE_DP(40dp) × 3 = 384px
        val moveSide = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 550f, 200f, 0) // 450px >= 384px
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 250f, 200f, 0) // 回退 300px >= 84px
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 250f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveSide)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_SIDE_RETURN, fired)
        down.recycle()
        moveSide.recycle()
        moveBack.recycle()
        up.recycle()
    }

    @Test
    fun `swipe down reverse then swipe down again fires swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        val moveDownAgain = MotionEvent.obtain(0, 150, MotionEvent.ACTION_MOVE, 100f, 360f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 360f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        detector.onTouchEvent(moveDownAgain)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
        moveDownAgain.recycle()
        up.recycle()
    }

    @Test
    fun `swipe inward then down with action configured fires SWIPE_IN_DOWN`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_DOWN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        // 首段向内 500px ≥ 侧滑短滑阈值(默认 384px) 记录拐点，第二段向下 200px ≥ 32dp(96px) 且以纵向为主。
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 600f, 1200f, 0)
        val moveDown = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 600f, 1400f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 600f, 1400f, 0)
        listOf(down, moveInward, moveDown, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_IN_DOWN, fired)
        listOf(down, moveInward, moveDown, up).forEach { it.recycle() }
    }

    @Test
    fun `swipe inward then up with action configured fires SWIPE_IN_UP`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_UP to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 600f, 1200f, 0)
        val moveUp = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 600f, 1000f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 600f, 1000f, 0)
        listOf(down, moveInward, moveUp, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_IN_UP, fired)
        listOf(down, moveInward, moveUp, up).forEach { it.recycle() }
    }

    @Test
    fun `inward first then down works for right docked ball`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_DOWN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
            inwardSignProvider = { -1f },
        )
        // 右侧停靠：向内 = -x，首段同样要滑够侧滑短滑阈值。
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 980f, 1200f, 0)
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 480f, 1200f, 0)
        val moveDown = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 480f, 1400f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 480f, 1400f, 0)
        listOf(down, moveInward, moveDown, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_IN_DOWN, fired)
        listOf(down, moveInward, moveDown, up).forEach { it.recycle() }
    }

    @Test
    fun `short inward then down fires compound without long inward travel`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_DOWN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        // 首段只需 40dp(120px)：向内 150px 即记录拐点，远低于侧滑短滑阈值 384px。
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 250f, 1200f, 0)
        val moveDown = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 250f, 1400f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 250f, 1400f, 0)
        listOf(down, moveInward, moveDown, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_IN_DOWN, fired)
        listOf(down, moveInward, moveDown, up).forEach { it.recycle() }
    }

    @Test
    fun `short down then inward fires compound below plain swipe threshold`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_DOWN_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        // 首段 150px < 下滑短滑阈值(240px)，但已够混合手势首段 40dp(120px)。
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 1350f, 0)
        val moveInward = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 300f, 1350f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 300f, 1350f, 0)
        listOf(down, moveDown, moveInward, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_DOWN_IN, fired)
        listOf(down, moveDown, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `inward without vertical turn stays short side swipe`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_DOWN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        // 默认 SIDE 短滑阈值 = 320% × 40dp × 3 = 384px，300px 仍算短侧滑。
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 400f, 1200f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 400f, 1200f, 0)
        listOf(down, moveInward, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_SIDE_SHORT, fired)
        listOf(down, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `inward past threshold without vertical turn stays long side swipe`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_IN_DOWN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 1200f, 0)
        // 首段已达标但第二段没有纵向位移：仍按原来的长侧滑处理，不升级为两段式。
        val moveInward = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 600f, 1200f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 600f, 1200f, 0)
        listOf(down, moveInward, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_SIDE_LONG, fired)
        listOf(down, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `reverse along locked axis disarms gesture hint`() {
        val hints = mutableListOf<FloatBallGestureType?>()
        val detector = newDetector(
            onGesture = { _, _, _, _, _ -> },
        ).also {
            it.bind(
                settings = AppSettings(freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id)),
                density = 3f,
                systemTouchSlopPx = 8f,
                onPickStart = { _, _, _, _ -> },
                onPickDrag = { _, _ -> },
                onPickEnd = {},
                onPickCancel = {},
                onGesture = { _, _, _, _, _ -> },
                onGestureHint = { hint -> hints.add(hint) },
            )
        }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveBack = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 250f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveBack)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, hints.last { it != null })
        assertNull(hints.last())
        down.recycle()
        moveDown.recycle()
        moveBack.recycle()
    }

    @Test
    fun `updateGestureArmState disarms on reverse and re-arms on forward swipe`() {
        val detector = newDetector()
        val axis = FloatBallGestureDetector.LockedSwipeAxis.DOWN
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 150f,
            incrementalDx = 0f,
            incrementalDy = 150f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(true, detector.isGestureArmedForTest())
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 80f,
            incrementalDx = 0f,
            incrementalDy = -70f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(false, detector.isGestureArmedForTest())
        detector.updateGestureArmState(
            totalDx = 0f,
            totalDy = 150f,
            incrementalDx = 0f,
            incrementalDy = 70f,
            lockedAxis = axis,
            forwardSign = 1f,
        )
        assertEquals(true, detector.isGestureArmedForTest())
    }

    @Test
    fun `micro rebound before release still fires swipe gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector { type, _, _, _, _ -> fired = type }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val moveRebound = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 100f, 335f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 100f, 335f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(moveDown)
        detector.onTouchEvent(moveRebound)
        detector.onTouchEvent(up)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        moveDown.recycle()
        moveRebound.recycle()
        up.recycle()
    }

    @Test
    fun `single tap does not start pick drag`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onGesture = { _, _, _, _, _ -> },
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(up)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertFalse(pickStarted)
        assertFalse(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `long press does not start or finish pick`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _, _, _ -> fired = type },
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val up = MotionEvent.obtain(0, 900, MotionEvent.ACTION_UP, 100f, 200f, 0)
        detector.onTouchEvent(down)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(FloatBallGestureType.LONG_PRESS, fired)
        detector.onTouchEvent(up)
        assertFalse(pickStarted)
        assertFalse(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `custom long press duration delays long press`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = AppSettings(
                freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
                floatBall = FloatBallSettings(floatBallLongPressMs = 1200),
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        detector.onTouchEvent(down)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertNull(fired)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals(FloatBallGestureType.LONG_PRESS, fired)
        down.recycle()
    }

    @Test
    fun `drag beyond slop starts pick and drag hold finishes pick`() {
        var pickStarted = false
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 300f, 0)
        val up = MotionEvent.obtain(0, 900, MotionEvent.ACTION_UP, 100f, 300f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        assertTrue(pickStarted)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        detector.onTouchEvent(up)
        assertTrue(pickEnded)
        assertFalse(pickCancelled)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `pause lock finishes pick before gesture window`() {
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _, _, _ -> fired = type },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        detector.onTouchEvent(up)
        assertTrue(pickEnded)
        assertFalse(pickCancelled)
        assertNull(fired)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `unlock pause before gesture window restores swipe on release`() {
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onGesture = { type, _, _, _, _ -> fired = type },
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        detector.unlockPickFromPause()
        detector.onTouchEvent(up)
        assertFalse(pickEnded)
        assertTrue(pickCancelled)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_SHORT, fired)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `pick preview callbacks fire before drag starts and cancel on tap`() {
        var previewStarted = false
        var previewCancelled = false
        var previewProgress = -1f
        var pickStarted = false
        val detector = newDetector(
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickPreviewStart = { _, _ -> previewStarted = true },
            onPickPreviewProgress = { progress -> previewProgress = progress },
            onPickPreviewCancel = { previewCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 210f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 100f, 210f, 0)
        detector.onTouchEvent(down)
        assertTrue(previewStarted)
        assertFalse(pickStarted)
        detector.onTouchEvent(move)
        assertTrue(previewProgress in 0f..1f)
        detector.onTouchEvent(up)
        assertTrue(previewCancelled)
        assertFalse(pickStarted)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `slop phase move fires preview move before pick starts`() {
        var previewMoveCount = 0
        var pickStarted = false
        val detector = newDetector(
            onPickStart = { _, _, _, _ -> pickStarted = true },
            onPickPreviewMove = { _, _, _, _ -> previewMoveCount++ },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 205f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 100f, 205f, 0)
        detector.onTouchEvent(down)
        assertEquals(1, previewMoveCount)
        detector.onTouchEvent(move)
        assertEquals(2, previewMoveCount)
        assertFalse(pickStarted)
        detector.onTouchEvent(up)
        assertFalse(pickStarted)
        down.recycle()
        move.recycle()
        up.recycle()
    }

    @Test
    fun `lag jump starts pick without double calling drag on transition frame`() {
        var startFingerX = -1f
        var startFingerY = -1f
        var dragFingerX = -1f
        var dragFingerY = -1f
        var dragCallCount = 0

        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = AppSettings(freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id)),
            density = 3f,
            systemTouchSlopPx = 8f,
            onPickStart = { _, _, fingerX, fingerY ->
                startFingerX = fingerX
                startFingerY = fingerY
            },
            onPickDrag = { fingerX, fingerY ->
                dragFingerX = fingerX
                dragFingerY = fingerY
                dragCallCount++
            },
            onPickEnd = {},
            onPickCancel = {},
            onGesture = { _, _, _, _, _ -> },
        )

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1050f, 500f, 0)
        // Lag jump: first move is already 200px away
        val moveJump = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 850f, 500f, 0)
        val moveNext = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 800f, 500f, 0)

        detector.onTouchEvent(down)
        detector.onTouchEvent(moveJump)

        // onPickStart receives 850f, onPickDrag NOT called on same frame
        assertEquals(850f, startFingerX, 0.001f)
        assertEquals(500f, startFingerY, 0.001f)
        assertEquals(0, dragCallCount)

        // on next move, onPickDrag receives 800f
        detector.onTouchEvent(moveNext)
        assertEquals(800f, dragFingerX, 0.001f)
        assertEquals(500f, dragFingerY, 0.001f)
        assertEquals(1, dragCallCount)

        down.recycle()
        moveJump.recycle()
        moveNext.recycle()
    }

    @Test
    fun `swipe down then inward with action configured fires SWIPE_DOWN_IN`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_DOWN_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val (down, moveDown, moveInward, up) = compoundDownThenInward(detector)
        assertEquals(FloatBallGestureType.SWIPE_DOWN_IN, fired)
        listOf(down, moveDown, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `swipe down then inward without configured action stays plain swipe`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(onGesture = { type, _, _, _, _ -> fired = type })
        val (down, moveDown, moveInward, up) = compoundDownThenInward(detector)
        // 首段 300px 超过默认下滑短滑阈值(240px)，未配置混合手势时回落为下滑（长）。
        assertEquals(FloatBallGestureType.SWIPE_DOWN_LONG, fired)
        listOf(down, moveDown, moveInward, up).forEach { it.recycle() }
    }

    /**
     * 滑动在松手时判定：rawY 是松手点，touchDownY 是手指最初按下球的位置。
     * 开启「启动器对齐手指按下位置」后，悬浮球才会用后者当锚点。
     */
    @Test
    fun `swipe reports release point as rawY and touch-down point as origin`() {
        var fired: FloatBallGestureType? = null
        var rawY = 0f
        var touchDownY = 0f
        val detector = newDetector(
            onGesture = { type, _, y, _, downY ->
                fired = type
                rawY = y
                touchDownY = downY
            },
        )
        // 默认下滑短滑阈值 = 200% × 40dp × 3 = 240px，滑行 600px 判为长滑。
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 300f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 700f, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, 100f, 900f, 0)
        listOf(down, move, up).forEach { detector.onTouchEvent(it) }

        assertEquals(FloatBallGestureType.SWIPE_DOWN_LONG, fired)
        assertEquals(900f, rawY, 0.001f)
        assertEquals(300f, touchDownY, 0.001f)
        listOf(down, move, up).forEach { it.recycle() }
    }

    @Test
    fun `swipe up then inward with action configured fires SWIPE_UP_IN`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_UP_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 700f, 0)
        // 默认 UP 短滑阈值 = floatBallUpSwipeShortPercent(256) × 40dp × 3 ≈ 308px
        val moveUp = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 300f, 0)
        val moveInward = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 240f, 300f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 240f, 300f, 0)
        listOf(down, moveUp, moveInward, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_UP_IN, fired)
        listOf(down, moveUp, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `swipe down then inward for right docked ball uses negative inward sign`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_DOWN_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
            inwardSignProvider = { -1f },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 980f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 980f, 500f, 0)
        val moveInward = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 840f, 500f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 840f, 500f, 0)
        listOf(down, moveDown, moveInward, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_DOWN_IN, fired)
        listOf(down, moveDown, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `diagonal second segment does not fire compound gesture`() {
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_DOWN_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 500f, 0)
        // 第二段沿边(dy=300) 远大于向内(dx=140)，按斜滑处理。
        val moveDiagonal = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 240f, 800f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 240f, 800f, 0)
        listOf(down, moveDown, moveDiagonal, up).forEach { detector.onTouchEvent(it) }
        assertEquals(FloatBallGestureType.SWIPE_DOWN_LONG, fired)
        listOf(down, moveDown, moveDiagonal, up).forEach { it.recycle() }
    }

    @Test
    fun `compound gesture past pick window falls back to pick`() {
        var fired: FloatBallGestureType? = null
        var pickEnded = false
        val detector = newDetector(
            settings = compoundSettings(
                FloatBallGestureType.SWIPE_DOWN_IN to com.slideindex.app.gesture.GestureAction.Back,
            ),
            onGesture = { type, _, _, _, _ -> fired = type },
            onPickEnd = { pickEnded = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 500f, 0)
        val moveInward = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 240f, 500f, 0)
        listOf(down, moveDown, moveInward).forEach { detector.onTouchEvent(it) }
        // 超过 800ms 取词窗口后抬手：只提交取词，不触发混合手势。
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        val up = MotionEvent.obtain(0, 950, MotionEvent.ACTION_UP, 240f, 500f, 0)
        detector.onTouchEvent(up)
        assertNull(fired)
        assertTrue(pickEnded)
        listOf(down, moveDown, moveInward, up).forEach { it.recycle() }
    }

    @Test
    fun `second finger after pause cancels selection and release does not commit`() {
        var selectionCancelCount = 0
        var pickEnded = false
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
            onPickSelectionCancel = { selectionCancelCount++ },
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()

        val secondDown = multiTouchEvent(
            0, 100, MotionEvent.ACTION_POINTER_DOWN, 1,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondDown)
        assertEquals(1, selectionCancelCount)
        assertFalse(pickEnded)
        assertFalse(pickCancelled)

        val up = MotionEvent.obtain(0, 200, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(up)
        assertFalse(pickEnded)
        assertTrue(pickCancelled)
        assertNull(fired)
        listOf(down, move, secondDown, up).forEach { it.recycle() }
    }

    @Test
    fun `second finger cancel then re-pause commits on release`() {
        var pickEnded = false
        var pickCancelled = false
        val detector = newDetector(
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        val secondDown = multiTouchEvent(
            0, 100, MotionEvent.ACTION_POINTER_DOWN, 1,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondDown)
        val secondUp = multiTouchEvent(
            0, 150, MotionEvent.ACTION_POINTER_UP, 1,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondUp)
        // 重新黄框：恢复“抬手提交”。
        detector.lockPickFromPause()
        val up = MotionEvent.obtain(0, 300, MotionEvent.ACTION_UP, 100f, 350f, 0)
        detector.onTouchEvent(up)
        assertTrue(pickEnded)
        assertFalse(pickCancelled)
        listOf(down, move, secondDown, secondUp, up).forEach { it.recycle() }
    }

    @Test
    fun `second finger cancel suppresses swipe gesture on release`() {
        var selectionCancelCount = 0
        var pickCancelled = false
        var fired: FloatBallGestureType? = null
        val detector = newDetector(
            onPickCancel = { pickCancelled = true },
            onPickSelectionCancel = { selectionCancelCount++ },
            onGesture = { type, _, _, _, _ -> fired = type },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 500f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        val secondDown = multiTouchEvent(
            0, 100, MotionEvent.ACTION_POINTER_DOWN, 1,
            listOf(Triple(0, 100f, 500f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondDown)
        // 取消后继续下滑：不允许再武装滑动手势。
        val moveAfterCancel = MotionEvent.obtain(0, 150, MotionEvent.ACTION_MOVE, 100f, 700f, 0)
        detector.onTouchEvent(moveAfterCancel)
        assertFalse(detector.isGestureArmedForTest())
        val up = MotionEvent.obtain(0, 250, MotionEvent.ACTION_UP, 100f, 700f, 0)
        detector.onTouchEvent(up)
        assertEquals(1, selectionCancelCount)
        assertTrue(pickCancelled)
        assertNull(fired)
        listOf(down, move, secondDown, moveAfterCancel, up).forEach { it.recycle() }
    }

    @Test
    fun `second finger before pause does not cancel selection`() {
        var selectionCancelCount = 0
        val detector = newDetector(onPickSelectionCancel = { selectionCancelCount++ })
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        val secondDown = multiTouchEvent(
            0, 100, MotionEvent.ACTION_POINTER_DOWN, 1,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondDown)
        assertEquals(0, selectionCancelCount)
        listOf(down, move, secondDown).forEach { it.recycle() }
    }

    @Test
    fun `primary lift after second finger cancel does not hand over to second finger`() {
        var selectionCancelCount = 0
        var pickCancelled = false
        var pickEnded = false
        val detector = newDetector(
            onPickEnd = { pickEnded = true },
            onPickCancel = { pickCancelled = true },
            onPickSelectionCancel = { selectionCancelCount++ },
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        detector.lockPickFromPause()
        val secondDown = multiTouchEvent(
            0, 100, MotionEvent.ACTION_POINTER_DOWN, 1,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(secondDown)
        // 第一根手指先抬起：应当收尾，第二根不能接管。
        val primaryUp = multiTouchEvent(
            0, 150, MotionEvent.ACTION_POINTER_UP, 0,
            listOf(Triple(0, 100f, 350f), Triple(1, 600f, 800f)),
        )
        detector.onTouchEvent(primaryUp)
        assertTrue(pickCancelled)
        assertEquals(1, selectionCancelCount)
        assertFalse(pickEnded)

        val secondMove = multiTouchEvent(
            0, 200, MotionEvent.ACTION_MOVE, 0,
            listOf(Triple(1, 700f, 900f)),
        )
        detector.onTouchEvent(secondMove)
        val secondUp = multiTouchEvent(
            0, 250, MotionEvent.ACTION_UP, 0,
            listOf(Triple(1, 700f, 900f)),
        )
        detector.onTouchEvent(secondUp)
        assertEquals(1, selectionCancelCount)
        assertTrue(pickCancelled)
        assertFalse(pickEnded)
        listOf(down, move, secondDown, primaryUp, secondMove, secondUp).forEach { it.recycle() }
    }

    @Test
    fun `cancelPickSelectionFromPause only fires while pause is locked`() {
        var selectionCancelCount = 0
        val detector = newDetector(onPickSelectionCancel = { selectionCancelCount++ })
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        val move = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 350f, 0)
        detector.onTouchEvent(down)
        detector.onTouchEvent(move)
        // 红叉阶段没有可取消的选中态。
        assertFalse(detector.cancelPickSelectionFromPause())
        assertEquals(0, selectionCancelCount)
        detector.lockPickFromPause()
        assertTrue(detector.cancelPickSelectionFromPause())
        assertEquals(1, selectionCancelCount)
        // 同一次黄框已经取消过，不能重复触发。
        assertFalse(detector.cancelPickSelectionFromPause())
        assertEquals(1, selectionCancelCount)
        listOf(down, move).forEach { it.recycle() }
    }

    private fun multiTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        actionIndex: Int,
        pointers: List<Triple<Int, Float, Float>>,
    ): MotionEvent {
        val properties = Array(pointers.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].first
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointers.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = pointers[index].second
                y = pointers[index].third
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private fun compoundSettings(
        vararg actions: Pair<FloatBallGestureType, com.slideindex.app.gesture.GestureAction>,
    ): AppSettings = AppSettings(
        freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        floatBall = AppSettings().floatBall.copy(floatBallGestureActions = actions.toMap()),
    )

    private fun compoundDownThenInward(
        detector: FloatBallGestureDetector,
    ): List<MotionEvent> {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        // 默认 DOWN 短滑阈值 = 200% × 40dp × 3 = 240px，首段 300px 达标并记录拐点。
        val moveDown = MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 100f, 500f, 0)
        // 第二段向内 140px ≥ 32dp(96px)，且沿边分量为 0。
        val moveInward = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 240f, 500f, 0)
        val up = MotionEvent.obtain(0, 150, MotionEvent.ACTION_UP, 240f, 500f, 0)
        listOf(down, moveDown, moveInward, up).forEach { detector.onTouchEvent(it) }
        return listOf(down, moveDown, moveInward, up)
    }

    private fun newDetector(
        settings: AppSettings = AppSettings(freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id)),        onPickStart: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
        onPickEnd: () -> Unit = {},
        onPickCancel: () -> Unit = {},
        onPickSelectionCancel: () -> Unit = {},
        onPickPreviewStart: (Float, Float) -> Unit = { _, _ -> },
        onPickPreviewProgress: (Float) -> Unit = {},
        onPickPreviewMove: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
        onPickPreviewCancel: () -> Unit = {},
        inwardSignProvider: () -> Float = { 1f },
        onGesture: (FloatBallGestureType, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    ): FloatBallGestureDetector {
        val detector = FloatBallGestureDetector()
        detector.bind(
            settings = settings,
            density = 3f,
            systemTouchSlopPx = 8f,
            onPickStart = onPickStart,
            onPickDrag = { _, _ -> },
            onPickEnd = onPickEnd,
            onPickCancel = onPickCancel,
            onPickSelectionCancel = onPickSelectionCancel,
            onGesture = onGesture,
            onPickPreviewStart = onPickPreviewStart,
            onPickPreviewProgress = onPickPreviewProgress,
            onPickPreviewMove = onPickPreviewMove,
            onPickPreviewCancel = onPickPreviewCancel,
            inwardSignProvider = inwardSignProvider,
        )
        return detector
    }
}
