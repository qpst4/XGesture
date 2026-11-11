package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 横屏方向补偿：面板自然方向 → 当前屏幕方向。
 *
 * 约定见 [forDisplayRotation]（`Display.getRotation()` 与设备物理旋转相反）。
 * 单测直接用 0/1/2/3 对应 `Surface.ROTATION_0/90/180/270`。
 */
class DisplayRotationGestureMapTest {

    private val rotation0 = 0
    private val rotation90 = 1
    private val rotation180 = 2
    private val rotation270 = 3

    @Test
    fun `rotation 0 keeps directions unchanged`() {
        MultiPointerGesture.entries
            .filter { it in SWIPES }
            .forEach { gesture ->
                assertEquals(gesture, gesture.forDisplayRotation(rotation0))
            }
    }

    @Test
    fun `rotation 90 turns panel directions counter clockwise`() {
        // ROTATION_90：设备逆时针转，自然顶边在左 → 面板「上」= 屏幕「左」。
        assertEquals(MultiPointerGesture.SWIPE_LEFT, MultiPointerGesture.SWIPE_UP.forDisplayRotation(rotation90))
        assertEquals(MultiPointerGesture.SWIPE_UP, MultiPointerGesture.SWIPE_RIGHT.forDisplayRotation(rotation90))
        assertEquals(MultiPointerGesture.SWIPE_RIGHT, MultiPointerGesture.SWIPE_DOWN.forDisplayRotation(rotation90))
        assertEquals(MultiPointerGesture.SWIPE_DOWN, MultiPointerGesture.SWIPE_LEFT.forDisplayRotation(rotation90))
        assertEquals(
            MultiPointerGesture.SWIPE_DOWN_LEFT,
            MultiPointerGesture.SWIPE_UP_LEFT.forDisplayRotation(rotation90),
        )
        // 面板「右下」= 屏幕「右上」：+x 对应屏幕上移、+y 对应屏幕右移。
        assertEquals(
            MultiPointerGesture.SWIPE_UP_RIGHT,
            MultiPointerGesture.SWIPE_DOWN_RIGHT.forDisplayRotation(rotation90),
        )
    }

    @Test
    fun `rotation 180 flips every direction`() {
        assertEquals(MultiPointerGesture.SWIPE_DOWN, MultiPointerGesture.SWIPE_UP.forDisplayRotation(rotation180))
        assertEquals(MultiPointerGesture.SWIPE_LEFT, MultiPointerGesture.SWIPE_RIGHT.forDisplayRotation(rotation180))
        assertEquals(
            MultiPointerGesture.SWIPE_DOWN_RIGHT,
            MultiPointerGesture.SWIPE_UP_LEFT.forDisplayRotation(rotation180),
        )
        assertEquals(
            MultiPointerGesture.SWIPE_UP_LEFT,
            MultiPointerGesture.SWIPE_DOWN_RIGHT.forDisplayRotation(rotation180),
        )
    }

    @Test
    fun `rotation 270 turns panel directions clockwise`() {
        assertEquals(MultiPointerGesture.SWIPE_RIGHT, MultiPointerGesture.SWIPE_UP.forDisplayRotation(rotation270))
        assertEquals(MultiPointerGesture.SWIPE_DOWN, MultiPointerGesture.SWIPE_RIGHT.forDisplayRotation(rotation270))
        assertEquals(MultiPointerGesture.SWIPE_LEFT, MultiPointerGesture.SWIPE_DOWN.forDisplayRotation(rotation270))
        assertEquals(MultiPointerGesture.SWIPE_UP, MultiPointerGesture.SWIPE_LEFT.forDisplayRotation(rotation270))
    }

    @Test
    fun `rotation invariant gestures are returned unchanged`() {
        val invariant = listOf(
            MultiPointerGesture.PINCH,
            MultiPointerGesture.SPREAD,
            MultiPointerGesture.ROTATE_CLOCKWISE,
            MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE,
            MultiPointerGesture.TAP,
            MultiPointerGesture.DOUBLE_TAP,
            MultiPointerGesture.TRIPLE_TAP,
            MultiPointerGesture.LONG_PRESS,
            MultiPointerGesture.TOUCH,
            MultiPointerGesture.PATH,
        )
        for (rotation in listOf(rotation0, rotation90, rotation180, rotation270)) {
            invariant.forEach { gesture ->
                assertEquals(gesture, gesture.forDisplayRotation(rotation))
            }
        }
    }

    private companion object {
        val SWIPES = setOf(
            MultiPointerGesture.SWIPE_UP,
            MultiPointerGesture.SWIPE_DOWN,
            MultiPointerGesture.SWIPE_LEFT,
            MultiPointerGesture.SWIPE_RIGHT,
            MultiPointerGesture.SWIPE_UP_LEFT,
            MultiPointerGesture.SWIPE_UP_RIGHT,
            MultiPointerGesture.SWIPE_DOWN_LEFT,
            MultiPointerGesture.SWIPE_DOWN_RIGHT,
        )
    }
}
