package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * evdev 触摸帧解码：Type B（slot + tracking id）与 Type A（SYN_MT_REPORT 分隔）。
 *
 * 坐标归一化约定：x、y 都除以屏高。这里用 panelHeight = 2000 方便算。
 */
class MtTouchFrameDecoderTest {

    private val height = 2000f

    @Test
    fun `type B decodes two fingers and lifts one`() {
        val decoder = MtTouchFrameDecoder(MtTouchFrameDecoder.Protocol.TYPE_B, panelHeight = height)

        val down = feed(
            decoder,
            listOf(
                abs(MtTouchFrameDecoder.ABS_MT_SLOT, 0),
                abs(MtTouchFrameDecoder.ABS_MT_TRACKING_ID, 10),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
                abs(MtTouchFrameDecoder.ABS_MT_SLOT, 1),
                abs(MtTouchFrameDecoder.ABS_MT_TRACKING_ID, 11),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 300),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 400),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertEquals(
            listOf(
                TouchPointer(id = 0, x = 0.05f, y = 0.1f),
                TouchPointer(id = 1, x = 0.15f, y = 0.2f),
            ),
            down,
        )

        val afterLift = feed(
            decoder,
            listOf(
                abs(MtTouchFrameDecoder.ABS_MT_SLOT, 0),
                abs(MtTouchFrameDecoder.ABS_MT_TRACKING_ID, -1),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertEquals(listOf(TouchPointer(id = 1, x = 0.15f, y = 0.2f)), afterLift)
    }

    @Test
    fun `type A decodes contacts separated by SYN_MT_REPORT`() {
        val decoder = MtTouchFrameDecoder(MtTouchFrameDecoder.Protocol.TYPE_A, panelHeight = height)

        val pointers = feed(
            decoder,
            listOf(
                key(MtTouchFrameDecoder.BTN_TOUCH, 1),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
                syn(MtTouchFrameDecoder.SYN_MT_REPORT),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 300),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 400),
                syn(MtTouchFrameDecoder.SYN_MT_REPORT),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertEquals(
            listOf(
                TouchPointer(id = 0, x = 0.05f, y = 0.1f),
                TouchPointer(id = 1, x = 0.15f, y = 0.2f),
            ),
            pointers,
        )
    }

    @Test
    fun `type A reports no pointers after BTN_TOUCH release`() {
        val decoder = MtTouchFrameDecoder(MtTouchFrameDecoder.Protocol.TYPE_A, panelHeight = height)
        feed(
            decoder,
            listOf(
                key(MtTouchFrameDecoder.BTN_TOUCH, 1),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
                syn(MtTouchFrameDecoder.SYN_MT_REPORT),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )

        val afterUp = feed(
            decoder,
            listOf(
                key(MtTouchFrameDecoder.BTN_TOUCH, 0),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertTrue(afterUp.isEmpty())
    }

    @Test
    fun `type A works without BTN_TOUCH events`() {
        val decoder = MtTouchFrameDecoder(MtTouchFrameDecoder.Protocol.TYPE_A, panelHeight = height)
        val pointers = feed(
            decoder,
            listOf(
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 500),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 600),
                syn(MtTouchFrameDecoder.SYN_MT_REPORT),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertEquals(listOf(TouchPointer(id = 0, x = 0.25f, y = 0.3f)), pointers)

        // 抬手后驱动只发一个空帧（没有接触点）也要能判成「没有手指」。
        val empty = feed(decoder, listOf(syn(MtTouchFrameDecoder.SYN_REPORT)))
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `SYN_DROPPED clears pending state`() {
        val decoder = MtTouchFrameDecoder(MtTouchFrameDecoder.Protocol.TYPE_B, panelHeight = height)
        feed(
            decoder,
            listOf(
                abs(MtTouchFrameDecoder.ABS_MT_SLOT, 0),
                abs(MtTouchFrameDecoder.ABS_MT_TRACKING_ID, 10),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_X, 100),
                abs(MtTouchFrameDecoder.ABS_MT_POSITION_Y, 200),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )

        val afterDrop = feed(
            decoder,
            listOf(
                syn(MtTouchFrameDecoder.SYN_DROPPED),
                syn(MtTouchFrameDecoder.SYN_REPORT),
            ),
        )
        assertTrue(afterDrop.isEmpty())
    }

    private fun feed(
        decoder: MtTouchFrameDecoder,
        events: List<Triple<Int, Int, Int>>,
    ): List<TouchPointer> {
        events.forEach { (type, code, value) -> decoder.onEvent(type, code, value) }
        return decoder.pointers
    }

    private fun abs(code: Int, value: Int): Triple<Int, Int, Int> =
        Triple(MtTouchFrameDecoder.EV_ABS, code, value)

    private fun key(code: Int, value: Int): Triple<Int, Int, Int> =
        Triple(MtTouchFrameDecoder.EV_KEY, code, value)

    private fun syn(code: Int): Triple<Int, Int, Int> =
        Triple(MtTouchFrameDecoder.EV_SYN, code, 0)
}
