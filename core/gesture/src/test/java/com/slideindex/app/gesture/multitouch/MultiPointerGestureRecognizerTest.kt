package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MultiPointerGestureRecognizerTest {

    private val recognizer = MultiPointerGestureRecognizer()

    /** 生成以 (cx,cy) 为圆心、半径 r 的三指/四指布局。 */
    private fun ring(
        count: Int,
        cx: Float,
        cy: Float,
        radius: Float,
        startAngle: Float = 0f,
        clockwiseShift: Float = 0f,
    ): List<TouchPointer> = (0 until count).map { index ->
        val angle = startAngle + clockwiseShift + (index * 2f * PI.toFloat() / count)
        TouchPointer(
            id = index,
            x = cx + radius * cos(angle),
            y = cy + radius * sin(angle),
        )
    }

    private fun feed(
        frames: List<List<TouchPointer>>,
        startMs: Long = 1_000L,
        stepMs: Long = 16L,
    ): RecognizedMultiPointerGesture? {
        var time = startMs
        var result: RecognizedMultiPointerGesture? = null
        for (frame in frames) {
            val recognized = recognizer.onFrame(frame, time)
            if (result == null) {
                result = recognized.firstOrNull {
                    it.gesture != MultiPointerGesture.TOUCH
                }
            }
            time += stepMs
        }
        return result
    }

    @Test
    fun `pinch is recognized when radius shrinks`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.30f))
        repeat(8) { step ->
            frames += ring(3, 0.5f, 0.5f, 0.30f - step * 0.02f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.PINCH, result?.gesture)
        assertEquals(3, result?.fingerCount)
    }

    @Test
    fun `spread is recognized when radius grows`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.20f))
        repeat(8) { step ->
            frames += ring(3, 0.5f, 0.5f, 0.20f + step * 0.02f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.SPREAD, result?.gesture)
    }

    @Test
    fun `three finger swipe up is recognized`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.70f, 0.18f))
        repeat(8) { step ->
            frames += ring(3, 0.5f, 0.70f - step * 0.02f, 0.18f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.SWIPE_UP, result?.gesture)
    }

    @Test
    fun `swipe right is recognized`() {
        val frames = mutableListOf(ring(3, 0.20f, 0.5f, 0.18f))
        repeat(8) { step ->
            frames += ring(3, 0.20f + step * 0.02f, 0.5f, 0.18f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.SWIPE_RIGHT, result?.gesture)
    }

    @Test
    fun `rotation is recognized when fingers turn clockwise`() {
        val frames = mutableListOf(ring(4, 0.5f, 0.5f, 0.22f))
        repeat(6) { step ->
            frames += ring(
                count = 4,
                cx = 0.5f,
                cy = 0.5f,
                radius = 0.22f,
                clockwiseShift = (step + 1) * 0.12f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.ROTATE_CLOCKWISE, result?.gesture)
        assertEquals(4, result?.fingerCount)
    }

    @Test
    fun `rotation is recognized when fingers turn counter clockwise`() {
        val frames = mutableListOf(ring(4, 0.5f, 0.5f, 0.22f))
        repeat(6) { step ->
            frames += ring(
                count = 4,
                cx = 0.5f,
                cy = 0.5f,
                radius = 0.22f,
                clockwiseShift = -(step + 1) * 0.12f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE, result?.gesture)
    }

    @Test
    fun `small four finger rotation still triggers`() {
        // 每帧 2.9°，6 帧约 14.5°：小幅度的四指旋转也应识别。
        val frames = mutableListOf(ring(4, 0.5f, 0.5f, 0.20f))
        repeat(6) { step ->
            frames += ring(
                count = 4,
                cx = 0.5f,
                cy = 0.5f,
                radius = 0.20f,
                clockwiseShift = (step + 1) * 0.05f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.ROTATE_CLOCKWISE, result?.gesture)
        assertEquals(4, result?.fingerCount)
    }

    @Test
    fun `rotation wins over slight radius change`() {
        // 旋转时半径同时缩小约 9%：应判旋转而不是捏合。
        val frames = mutableListOf(ring(4, 0.5f, 0.5f, 0.22f))
        repeat(6) { step ->
            frames += ring(
                count = 4,
                cx = 0.5f,
                cy = 0.5f,
                radius = 0.22f - step * 0.003f,
                clockwiseShift = (step + 1) * 0.11f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.ROTATE_CLOCKWISE, result?.gesture)
    }

    @Test
    fun `pinch still wins when rotation is negligible`() {
        // 半径明显缩小 + 每帧 0.4° 的轻微转动：仍应判捏合。
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.30f))
        repeat(8) { step ->
            frames += ring(
                count = 3,
                cx = 0.5f,
                cy = 0.5f,
                radius = 0.30f - step * 0.02f,
                clockwiseShift = (step + 1) * 0.007f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.PINCH, result?.gesture)
    }

    @Test
    fun `four finger swipe is recognized`() {
        val frames = mutableListOf(ring(4, 0.65f, 0.5f, 0.16f))
        repeat(8) { step ->
            frames += ring(4, 0.65f - step * 0.02f, 0.5f, 0.16f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.SWIPE_LEFT, result?.gesture)
    }

    @Test
    fun `rotation survives centroid drift`() {
        // 转动手腕时质心会平移，旋转量不应被抵消。
        val frames = mutableListOf(ring(4, 0.45f, 0.55f, 0.20f))
        repeat(6) { step ->
            frames += ring(
                count = 4,
                cx = 0.45f + step * 0.010f,
                cy = 0.55f - step * 0.006f,
                radius = 0.20f,
                clockwiseShift = (step + 1) * 0.10f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.ROTATE_CLOCKWISE, result?.gesture)
    }

    @Test
    fun `pinch survives centroid drift`() {
        // 捏合时手会整体移动，判定不应要求质心原地不动。
        val frames = mutableListOf(ring(3, 0.30f, 0.55f, 0.28f))
        repeat(8) { step ->
            frames += ring(
                count = 3,
                cx = 0.30f + step * 0.012f,
                cy = 0.55f - step * 0.004f,
                radius = 0.28f - step * 0.022f,
            )
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.PINCH, result?.gesture)
    }

    @Test
    fun `tiny jitter does not trigger`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.20f))
        repeat(10) { step ->
            val jitter = if (step % 2 == 0) 0.001f else -0.001f
            frames += ring(3, 0.5f + jitter, 0.5f - jitter, 0.20f + jitter)
        }
        assertNull(feed(frames))
    }

    @Test
    fun `two finger pinch is recognized`() {
        val frames = mutableListOf(ring(2, 0.5f, 0.5f, 0.30f))
        repeat(8) { step ->
            frames += ring(2, 0.5f, 0.5f, 0.30f - step * 0.02f)
        }
        val result = feed(frames)
        assertEquals(MultiPointerGesture.PINCH, result?.gesture)
        assertEquals(2, result?.fingerCount)
    }

    @Test
    fun `gesture is latched until fingers lift`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.30f))
        repeat(8) { step ->
            frames += ring(3, 0.5f, 0.5f, 0.30f - step * 0.02f)
        }
        val first = feed(frames)
        assertEquals(MultiPointerGesture.PINCH, first?.gesture)

        // 继续按着不松手：不应再次触发。
        val more = mutableListOf<List<TouchPointer>>()
        repeat(5) { step ->
            more += ring(3, 0.5f, 0.5f, 0.10f - step * 0.005f)
        }
        assertNull(feed(more, startMs = 2_000L))

        // 抬起后可以重新识别。
        recognizer.onFrame(emptyList(), 2_500L)
        val again = mutableListOf(ring(3, 0.5f, 0.5f, 0.30f))
        repeat(8) { step ->
            again += ring(3, 0.5f, 0.5f, 0.30f - step * 0.02f)
        }
        assertEquals(MultiPointerGesture.PINCH, feed(again, startMs = 3_000L)?.gesture)
    }

    @Test
    fun `finger count change rebuilds baseline instead of triggering`() {
        val frames = mutableListOf(ring(3, 0.5f, 0.5f, 0.30f))
        repeat(2) { step ->
            frames += ring(3, 0.5f, 0.5f, 0.30f - step * 0.02f)
        }
        // 第四根手指加入：应重建基准，不触发。
        frames += ring(4, 0.5f, 0.5f, 0.18f)
        assertNull(feed(frames))
    }

    @Test
    fun `two finger tap is recognized`() {
        val down = ring(2, 0.5f, 0.5f, 0.2f)
        val downFrame = recognizer.onFrame(down, 1_000L)
        assertEquals(MultiPointerGesture.TOUCH, downFrame.firstOrNull()?.gesture)
        assertEquals(2, downFrame.firstOrNull()?.fingerCount)

        // 抬手立即产生点击候选（不等双击窗口）。
        val liftFrame = recognizer.onFrame(emptyList(), 1_100L)
        assertEquals(MultiPointerGesture.TAP, liftFrame.firstOrNull()?.gesture)
        assertEquals(2, liftFrame.firstOrNull()?.fingerCount)
    }

    @Test
    fun `two finger tap with staggered press is recognized`() {
        // 第一根手指先按：单指触摸就已经输出一次 TOUCH。
        val first = recognizer.onFrame(ring(1, 0.45f, 0.5f, 0.1f), 1_000L)
        assertEquals(MultiPointerGesture.TOUCH, first.firstOrNull()?.gesture)
        assertEquals(1, first.firstOrNull()?.fingerCount)

        // 第二根手指加入：本次触摸已经发过 TOUCH，不再重复。
        val second = recognizer.onFrame(ring(2, 0.5f, 0.5f, 0.2f), 1_030L)
        assertTrue(second.isEmpty())

        val lift = recognizer.onFrame(emptyList(), 1_100L)
        assertEquals(MultiPointerGesture.TAP, lift.firstOrNull()?.gesture)
        assertEquals(2, lift.firstOrNull()?.fingerCount)
    }
}
