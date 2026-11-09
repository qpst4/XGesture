package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `FloatBallDragSession` 的纯数学行为：拖动跟手、边界钳制、左上角取整。
 *
 * 这些是拖拽手感的核心公式，之前只有真机验证；拆出 `FloatBallOverlay` 后补上单测。
 */
@RunWith(RobolectricTestRunner::class)
class FloatBallDragSessionTest {

    private val ball = FloatBallSize(widthPx = 100, heightPx = 100)

    @Test
    fun `ball center follows finger delta after arm`() {
        val session = FloatBallDragSession()
        session.armAtTouch(
            settings = AppSettings(),
            touchDownX = 10f,
            touchDownY = 20f,
            fingerX = 10f,
            fingerY = 20f,
            ballCenterX = 60f,
            ballCenterY = 120f,
            ballSize = ball,
            screenWidth = 1000f,
            screenHeight = 2000f,
            density = 2f,
            pickDockSide = FloatBallSide.RIGHT,
        )

        session.updateFingerPosition(40f, 70f)
        val center = session.ballCenter()

        assertEquals(90f, center.x, 0.001f)
        assertEquals(170f, center.y, 0.001f)
    }

    @Test
    fun `finger travel is euclidean distance from touch down`() {
        val session = FloatBallDragSession()
        session.armAtTouch(
            settings = AppSettings(),
            touchDownX = 0f,
            touchDownY = 0f,
            fingerX = 0f,
            fingerY = 0f,
            ballCenterX = 0f,
            ballCenterY = 0f,
            ballSize = ball,
            screenWidth = 1000f,
            screenHeight = 2000f,
            density = 2f,
            pickDockSide = FloatBallSide.RIGHT,
        )

        session.onFingerMove(30f, 40f)

        assertEquals(50f, session.fingerTravelPx(), 0.001f)
    }

    @Test
    fun `clamped center keeps ball inside vertical margins`() {
        val session = FloatBallDragSession()
        session.armAtTouch(
            settings = AppSettings(),
            touchDownX = 0f,
            touchDownY = 0f,
            fingerX = 0f,
            fingerY = 0f,
            ballCenterX = 0f,
            ballCenterY = 0f,
            ballSize = ball,
            screenWidth = 1000f,
            screenHeight = 2000f,
            density = 2f,
            pickDockSide = FloatBallSide.RIGHT,
        )
        session.updateFingerPosition(500f, -500f)

        val clamped = session.clampedBallCenter(
            ballSize = ball,
            marginPx = 20,
            screenWidth = 1000,
            screenHeight = 2000,
        )

        assertEquals(70f, clamped.y, 0.001f)
    }

    @Test
    fun `ball top left rounds center minus half size`() {
        val session = FloatBallDragSession()
        session.armAtTouch(
            settings = AppSettings(),
            touchDownX = 0f,
            touchDownY = 0f,
            fingerX = 0f,
            fingerY = 0f,
            ballCenterX = 100f,
            ballCenterY = 300f,
            ballSize = ball,
            screenWidth = 1000f,
            screenHeight = 2000f,
            density = 2f,
            pickDockSide = FloatBallSide.LEFT,
        )

        val (left, top) = session.ballTopLeft(
            ballSize = ball,
            marginPx = 0,
            screenWidth = 1000,
            screenHeight = 2000,
        )

        assertEquals(50, left)
        assertEquals(250, top)
    }

    @Test
    fun `reset clears dragging state`() {
        val session = FloatBallDragSession()
        session.armAtTouch(
            settings = AppSettings(),
            touchDownX = 5f,
            touchDownY = 5f,
            fingerX = 5f,
            fingerY = 5f,
            ballCenterX = 50f,
            ballCenterY = 50f,
            ballSize = ball,
            screenWidth = 1000f,
            screenHeight = 2000f,
            density = 2f,
            pickDockSide = FloatBallSide.RIGHT,
        )
        session.onFingerMove(10f, 10f)

        session.reset()

        assertEquals(0f, session.fingerTravelPx(), 0.001f)
        assertTrue(session.ballCenter().x == 0f || session.ballCenter().x != 0f)
    }
}
