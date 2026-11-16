package com.slideindex.app.overlay

import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class StuckGestureWatchdogTest {

    @Test
    fun `stalls only after timeout without input`() {
        var holding = true
        var stalls = 0
        val watchdog = StuckGestureWatchdog(
            timeoutMs = 10_000L,
            isHolding = { _ -> holding },
            onStall = { stalls++ },
            tokenProvider = { 0L },
        )

        watchdog.onInput()
        idleFor(9_000L)
        assertEquals(0, stalls)

        idleFor(2_000L)
        assertEquals("超过超时时间没有输入必须收尾", 1, stalls)
    }

    @Test
    fun `continuous input keeps refreshing the deadline`() {
        var holding = true
        var stalls = 0
        val watchdog = StuckGestureWatchdog(
            timeoutMs = 10_000L,
            isHolding = { _ -> holding },
            onStall = { stalls++ },
            tokenProvider = { 0L },
        )

        repeat(6) {
            watchdog.onInput()
            idleFor(5_000L)
        }

        assertEquals("手指还在动时不能误判为卡死", 0, stalls)
    }

    @Test
    fun `released gesture never stalls`() {
        var holding = true
        var stalls = 0
        val watchdog = StuckGestureWatchdog(
            timeoutMs = 10_000L,
            isHolding = { _ -> holding },
            onStall = { stalls++ },
            tokenProvider = { 0L },
        )

        watchdog.onInput()
        holding = false
        watchdog.onStateChanged()
        idleFor(30_000L)

        assertEquals(0, stalls)
    }

    @Test
    fun `stale timer from previous session is ignored`() {
        var stalls = 0
        var sessionToken = 1L
        val watchdog = StuckGestureWatchdog(
            timeoutMs = 10_000L,
            isHolding = { token -> token == sessionToken },
            onStall = { stalls++ },
            tokenProvider = { sessionToken },
        )

        watchdog.onInput()
        // 上一轮会话结束、新会话开始：代次变了，旧定时器不能再复位当前会话。
        sessionToken = 2L
        idleFor(12_000L)

        assertEquals("上一轮会话遗留的定时器不能复位新会话", 0, stalls)
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }
}
