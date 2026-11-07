package com.slideindex.app.perf

import android.os.SystemClock
import android.util.Log

/**
 * Regional pick pipeline timing. Filter logcat with: PickPerf
 */
object PickPerf {
    private const val TAG = "PickPerf"

    @Volatile
    private var sessionId = 0

    @Volatile
    private var sessionStartMs = 0L

    fun beginSession(label: String) {
        sessionId += 1
        sessionStartMs = SystemClock.elapsedRealtime()
        log("BEGIN | $label")
    }

    fun mark(step: String, detail: String = "") {
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs
        val thread = Thread.currentThread().name
        val suffix = if (detail.isEmpty()) "" else " | $detail"
        Log.i(TAG, "[$sessionId] +${elapsedMs}ms | $step | thread=$thread$suffix")
    }

    fun markStepDuration(step: String, startedAtMs: Long, detail: String = "") {
        val stepMs = SystemClock.elapsedRealtime() - startedAtMs
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs
        val thread = Thread.currentThread().name
        val suffix = if (detail.isEmpty()) "" else " | $detail"
        Log.i(
            TAG,
            "[$sessionId] +${elapsedMs}ms | $step | step=${stepMs}ms | thread=$thread$suffix"
        )
    }

    fun endSession(step: String = "END", detail: String = "") {
        mark(step, detail)
    }

    private fun log(message: String) {
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs
        val thread = Thread.currentThread().name
        Log.i(TAG, "[$sessionId] +${elapsedMs}ms | $message | thread=$thread")
    }
}
