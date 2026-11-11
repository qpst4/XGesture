package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运行时协议嗅探：静态能力表判错时，用真实事件流纠正。
 */
class MtProtocolSnifferTest {

    @Test
    fun `type A stream is detected by SYN_MT_REPORT`() {
        val sniffer = MtProtocolSniffer()
        listOf(
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
            syn(MtTouchFrameDecoder.SYN_MT_REPORT),
            syn(MtTouchFrameDecoder.SYN_REPORT),
        ).forEach { (type, code, value) -> sniffer.onEvent(type, code, value) }

        assertEquals(MtTouchFrameDecoder.Protocol.TYPE_A, sniffer.protocol)
        assertFalse(sniffer.trackingIdOptional)
        assertTrue(sniffer.sawTouch)
    }

    @Test
    fun `type B stream is detected by slot and tracking id`() {
        val sniffer = MtProtocolSniffer()
        listOf(
            abs(MtTouchFrameDecoder.ABS_MT_SLOT, 0),
            abs(MtTouchFrameDecoder.ABS_MT_TRACKING_ID, 7),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
            syn(MtTouchFrameDecoder.SYN_REPORT),
        ).forEach { (type, code, value) -> sniffer.onEvent(type, code, value) }

        assertEquals(MtTouchFrameDecoder.Protocol.TYPE_B, sniffer.protocol)
        assertFalse(sniffer.trackingIdOptional)
        assertTrue(sniffer.sawTouch)
    }

    @Test
    fun `slot without tracking id is a type B variant`() {
        val sniffer = MtProtocolSniffer()
        listOf(
            abs(MtTouchFrameDecoder.ABS_MT_SLOT, 0),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
            syn(MtTouchFrameDecoder.SYN_REPORT),
        ).forEach { (type, code, value) -> sniffer.onEvent(type, code, value) }

        assertEquals(MtTouchFrameDecoder.Protocol.TYPE_B, sniffer.protocol)
        assertTrue(sniffer.trackingIdOptional)
    }

    @Test
    fun `positions without separators default to type A`() {
        val sniffer = MtProtocolSniffer()
        listOf(
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
            abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
            syn(MtTouchFrameDecoder.SYN_REPORT),
        ).forEach { (type, code, value) -> sniffer.onEvent(type, code, value) }

        assertEquals(MtTouchFrameDecoder.Protocol.TYPE_A, sniffer.protocol)
    }

    @Test
    fun `BTN_TOUCH press counts as touch evidence`() {
        val sniffer = MtProtocolSniffer()
        sniffer.onEvent(MtTouchFrameDecoder.EV_KEY, MtTouchFrameDecoder.BTN_TOUCH, 1)
        assertTrue(sniffer.sawTouch)

        val idle = MtProtocolSniffer()
        idle.onEvent(MtTouchFrameDecoder.EV_KEY, MtTouchFrameDecoder.BTN_TOUCH, 0)
        assertFalse(idle.sawTouch)
    }

    private fun abs(code: Int, value: Int): Triple<Int, Int, Int> =
        Triple(MtTouchFrameDecoder.EV_ABS, code, value)

    private fun syn(code: Int): Triple<Int, Int, Int> =
        Triple(MtTouchFrameDecoder.EV_SYN, code, 0)
}
