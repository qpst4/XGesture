package com.slideindex.app.overlay.dragdrop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 拖拽源会话的契约：流转栏认领后源才让位，结束以后不能把恢复动作留给下一次拖拽。
 *
 * 这些行为直接决定"恢复 / 取消"是否还有区别，回归时优先跑这组。
 */
@RunWith(RobolectricTestRunner::class)
@org.junit.Ignore("Robolectric 在当前 JDK 上无法操作 raw FileDescriptor，环境问题待修")
class DragFlowSessionTest {

    private var source: DragFlowSource? = null

    @After
    fun tearDown() {
        DragFlowSession.end(source)
        source = null
    }

    @Test
    fun `claim marks the registered source`() {
        var restored = 0
        var discarded = 0
        val created = DragFlowSource(
            onRestore = { restored++ },
            onDiscard = { discarded++ },
        )
        source = created

        DragFlowSession.begin(created)
        assertFalse(created.claimed)

        val claimed = DragFlowSession.claim()
        assertSame(created, claimed)
        assertTrue(created.claimed)

        claimed?.restore()
        claimed?.discard()
        assertEquals(1, restored)
        assertEquals(1, discarded)
    }

    @Test
    fun `claim without a registered source returns null`() {
        assertNull(DragFlowSession.claim())
    }

    @Test
    fun `end clears the session so the next drag cannot reuse it`() {
        val created = DragFlowSource(onRestore = {}, onDiscard = {})
        source = created
        DragFlowSession.begin(created)
        DragFlowSession.end(created)

        assertNull(DragFlowSession.claim())
        assertFalse(created.claimed)
    }

    @Test
    fun `ending another source does not clear the current one`() {
        val first = DragFlowSource(onRestore = {}, onDiscard = {})
        val second = DragFlowSource(onRestore = {}, onDiscard = {})
        source = second
        DragFlowSession.begin(second)
        DragFlowSession.end(first)

        assertSame(second, DragFlowSession.claim())
    }
}
