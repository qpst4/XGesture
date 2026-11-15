package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Replays independent float-ball pick drag from a docked ball at vertical [gestureStartRawY]
 * to the edge-gesture trigger point, so edge regional pick matches float-ball pick semantics.
 */
internal object FloatBallEdgePickReplay {
    private const val REPLAY_STEP_DP = 6f

    fun replayToTrigger(
        session: FloatBallDragSession,
        settings: AppSettings,
        edgeSide: FloatBallSide,
        gestureStartRawY: Float,
        triggerRawX: Float,
        triggerRawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        marginPx: Int
    ) {
        session.reset()
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val ballWidthPx = ballSize.widthPx.toFloat()
        val ballHeightPx = ballSize.heightPx.toFloat()
        val dockLeft = FloatBallLayout.dockedBallLeftPx(
            activeSide = edgeSide,
            ballWidthPx = ballSize.widthPx,
            screenWidth = screenWidth.roundToInt(),
            edgeOffsetPx = FloatBallLayout.edgeInsetPx(settings, density),
        )
        val ballCenterX = dockLeft + ballWidthPx / 2f
        val minCenterY = marginPx + ballHeightPx / 2f
        val maxCenterY = screenHeight - marginPx - ballHeightPx / 2f
        val ballCenterY = gestureStartRawY.coerceIn(minCenterY, maxCenterY.coerceAtLeast(minCenterY))

        session.armAtTouch(
            settings = settings,
            touchDownX = ballCenterX,
            touchDownY = ballCenterY,
            fingerX = ballCenterX,
            fingerY = ballCenterY,
            ballCenterX = ballCenterX,
            ballCenterY = ballCenterY,
            ballSize = ballSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            pickDockSide = edgeSide
        )

        val totalDistance = hypot(triggerRawX - ballCenterX, triggerRawY - ballCenterY)
        if (totalDistance < 1f) {
            advancePickState(
                session = session,
                settings = settings,
                ballSize = ballSize,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
                marginPx = marginPx
            )
            return
        }

        val stepPx = (REPLAY_STEP_DP * density).coerceAtLeast(2f)
        val steps = (totalDistance / stepPx).roundToInt().coerceIn(1, 512)
        var previousX = ballCenterX
        var previousY = ballCenterY
        for (step in 1..steps) {
            val progress = step.toFloat() / steps.toFloat()
            val fingerX = ballCenterX + (triggerRawX - ballCenterX) * progress
            val fingerY = ballCenterY + (triggerRawY - ballCenterY) * progress
            session.onFingerMove(fingerX - previousX, fingerY - previousY)
            previousX = fingerX
            previousY = fingerY
            advancePickState(
                session = session,
                settings = settings,
                ballSize = ballSize,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
                marginPx = marginPx
            )
        }
    }

    private fun advancePickState(
        session: FloatBallDragSession,
        settings: AppSettings,
        ballSize: FloatBallSize,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        marginPx: Int
    ) {
        session.computePick(
            settings = settings,
            ballSize = ballSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx
        )
    }
}

internal fun PanelSide.toFloatBallPickDockSide(rawX: Float, screenWidth: Float): FloatBallSide =
    when (this) {
        PanelSide.LEFT -> FloatBallSide.LEFT
        PanelSide.RIGHT -> FloatBallSide.RIGHT
        PanelSide.TOP, PanelSide.BOTTOM ->
            if (rawX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
    }
