package com.slideindex.app.overlay

import android.os.Looper
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OverlayHostLeaseTest {

    @After
    fun tearDown() {
        OverlayHostLease.resetForTest()
    }

    @Test
    fun `owner gone triggers teardown on next check`() {
        var torndown = false
        OverlayHostLease.register(
            key = "edge",
            isOwnerAlive = { false },
            teardown = { torndown = true },
        )

        idleFor(3_000L)

        assertTrue("宿主服务已不在时必须摘掉残留浮层", torndown)
    }

    @Test
    fun `renewed lease keeps overlays alive`() {
        var torndown = false
        OverlayHostLease.register(
            key = "edge",
            isOwnerAlive = { true },
            teardown = { torndown = true },
        )

        repeat(6) {
            OverlayHostLease.renew()
            idleFor(1_500L)
        }

        assertFalse("服务持续续租时不得拆卸浮层", torndown)
    }

    @Test
    fun `alive owner is never torn down without heartbeat`() {
        // 单进程改造后租约不再靠「心跳过期」判定（renew 已是空实现），只要宿主实例还在就不得拆卸。
        var torndown = false
        OverlayHostLease.register(
            key = "edge",
            isOwnerAlive = { true },
            teardown = { torndown = true },
        )

        idleFor(9_000L)

        assertFalse("宿主实例仍在本进程时不得兜底拆卸", torndown)
    }

    @Test
    fun `unregister stops watchdog`() {
        var torndown = false
        OverlayHostLease.register(
            key = "edge",
            isOwnerAlive = { false },
            teardown = { torndown = true },
        )
        idleFor(500L)
        OverlayHostLease.unregister("edge")

        idleFor(6_000L)

        assertFalse("正常停止的宿主不该被兜底逻辑再次拆卸", torndown)
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }
}
