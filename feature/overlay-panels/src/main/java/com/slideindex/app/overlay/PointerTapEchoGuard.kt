package com.slideindex.app.overlay

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * QC `m81.r` + inject-coordinate echo absorption.
 *
 * After a pointer tap is injected, swallows:
 * - [MotionEvent.ACTION_OUTSIDE] (clears armed on first swallow, like QC)
 * - Synthetic DOWN/UP/MOVE near the inject point (our touch layer is larger than QC's)
 *
 * Does **not** swallow touches far from the inject point (e.g. a new tap on the joystick center).
 */
class PointerTapEchoGuard {
    private var armed = false
    private var injectX = 0f
    private var injectY = 0f
    private var echoSlopPx = DEFAULT_ECHO_SLOP_PX
    private var absorbUntilMs = 0L

    val isActive: Boolean
        get() {
            disarmIfExpired()
            return armed
        }

    fun arm(
        rawX: Float,
        rawY: Float,
        echoSlopPx: Float,
        durationMs: Long = ABSORB_MS
    ) {
        armed = true
        injectX = rawX
        injectY = rawY
        this.echoSlopPx = echoSlopPx.coerceAtLeast(8f)
        absorbUntilMs = System.currentTimeMillis() + durationMs
    }

    fun reset() {
        armed = false
        absorbUntilMs = 0L
    }

    fun disarmIfExpired() {
        if (armed && System.currentTimeMillis() >= absorbUntilMs) {
            armed = false
        }
    }

    /**
     * @return true if this event is an inject echo and must not trigger click / ring / dismiss.
     */
    fun shouldSwallow(event: MotionEvent): Boolean {
        disarmIfExpired()
        if (!armed) return false

        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            armed = false
            return true
        }

        if (!hasReliableCoordinates(event)) return false

        val distance = hypot(
            (event.rawX - injectX).toDouble(),
            (event.rawY - injectY).toDouble()
        ).toFloat()
        return distance <= echoSlopPx
    }

    private fun hasReliableCoordinates(event: MotionEvent): Boolean =
        event.rawX != 0f || event.rawY != 0f

    companion object {
        const val ABSORB_MS = 200L
        private const val DEFAULT_ECHO_SLOP_PX = 48f
    }
}
