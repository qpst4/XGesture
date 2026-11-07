package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Quick Cursor [mt0]: one [dispatchGesture] at a time; each segment uses
 * [GestureDescription.StrokeDescription.continueStroke] with 1 ms duration.
 */
class FloatingPointerRealtimeGesture(
    private val service: AccessibilityService,
    startX: Float,
    startY: Float,
    private val onError: () -> Unit = {},
    private val onFinished: () -> Unit = {}
) {
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var lastDispatchedPoint: Point
    private var dispatchInFlight = false
    private var finishing = false
    private val callback = RealtimeGestureCallback()
    private val mainHandler = Handler(Looper.getMainLooper())

    var currentX: Float = startX
        private set
    var currentY: Float = startY
        private set

    init {
        val x = startX.toInt()
        val y = startY.toInt()
        lastDispatchedPoint = Point(x, y)
        currentX = startX
        currentY = startY
        val path = segmentPath(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat())
        currentStroke = GestureDescription.StrokeDescription(path, 0L, SEGMENT_DURATION_MS, true)
        dispatchStroke("init")
    }

    fun updatePosition(x: Float, y: Float) {
        currentX = x
        currentY = y
        tryDispatchMove()
    }

    fun finish() {
        finishing = true
        if (dispatchInFlight) {
            mainHandler.postDelayed({ finish() }, FINISH_RETRY_DELAY_MS)
            return
        }
        dispatchFinishStroke()
    }

    private fun tryDispatchMove() {
        if (dispatchInFlight || finishing) return
        val x = currentX.toInt()
        val y = currentY.toInt()
        if (x == lastDispatchedPoint.x && y == lastDispatchedPoint.y) return
        val stroke = currentStroke ?: return
        val path = segmentPath(
            lastDispatchedPoint.x.toFloat(),
            lastDispatchedPoint.y.toFloat(),
            x.toFloat(),
            y.toFloat()
        )
        val continued = try {
            stroke.continueStroke(path, 0L, SEGMENT_DURATION_MS, true)
        } catch (e: Exception) {
            Log.e(TAG, "continueStroke failed", e)
            reportError("continueStroke")
            return
        }
        currentStroke = continued
        lastDispatchedPoint = Point(x, y)
        dispatchStroke("move")
    }

    private fun dispatchFinishStroke() {
        val stroke = currentStroke ?: run {
            onFinished()
            return
        }
        val last = lastDispatchedPoint
        val endX = currentX.toInt()
        val endY = currentY.toInt()
        val path = segmentPath(
            last.x.toFloat(),
            last.y.toFloat(),
            endX.toFloat(),
            endY.toFloat()
        )
        val continued = try {
            stroke.continueStroke(path, 0L, SEGMENT_DURATION_MS, false)
        } catch (e: Exception) {
            Log.e(TAG, "finish continueStroke failed", e)
            reportError("finish")
            return
        }
        currentStroke = continued
        lastDispatchedPoint = Point(endX, endY)
        dispatchStroke("finish")
    }

    private fun dispatchStroke(label: String) {
        dispatchInFlight = true
        val stroke = currentStroke ?: return
        val builder = GestureDescription.Builder().addStroke(stroke)
        val dispatched = try {
            service.dispatchGesture(builder.build(), callback, null)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture failed ($label)", e)
            false
        }
        if (!dispatched) {
            dispatchInFlight = false
            reportError(label)
        }
    }

    private fun onSegmentCompleted() {
        dispatchInFlight = false
        if (finishing) {
            if (currentStroke?.willContinue() != true) {
                onFinished()
            } else {
                dispatchFinishStroke()
            }
            return
        }
        tryDispatchMove()
    }

    private fun reportError(label: String) {
        Log.e(TAG, "RealtimeGesture error: $label")
        onError()
    }

    private fun segmentPath(startX: Float, startY: Float, endX: Float, endY: Float): Path =
        Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

    private inner class RealtimeGestureCallback : AccessibilityService.GestureResultCallback() {
        override fun onCancelled(gestureDescription: GestureDescription?) {
            dispatchInFlight = false
            if (finishing) {
                onFinished()
            } else {
                reportError("cancelled")
            }
        }

        override fun onCompleted(gestureDescription: GestureDescription?) {
            onSegmentCompleted()
        }
    }

    companion object {
        private const val TAG = "FpRealtimeGesture"
        private const val SEGMENT_DURATION_MS = 1L
        private const val FINISH_RETRY_DELAY_MS = 1L
    }
}
