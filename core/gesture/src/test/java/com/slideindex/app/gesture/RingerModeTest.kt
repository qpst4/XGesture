package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「声音模式」的数据层契约。
 *
 * 与其它开关的 [ToggleMode] 不同：它是系统真实的三种铃声状态 + 一个循环操作。
 * 旧数据（空 payload）必须是「切换」，否则升级后老绑定会失效。
 */
class RingerModeTest {

    @Test
    fun decode_roundTripsKnownPayloads() {
        assertEquals(RingerMode.TOGGLE, RingerMode.decode(""))
        assertEquals(RingerMode.RING, RingerMode.decode("1"))
        assertEquals(RingerMode.VIBRATE, RingerMode.decode("2"))
        assertEquals(RingerMode.SILENT, RingerMode.decode("3"))
    }

    @Test
    fun decode_fallsBackToToggleForGarbage() {
        assertEquals(RingerMode.TOGGLE, RingerMode.decode("garbage"))
        assertEquals(RingerMode.TOGGLE, RingerMode.decode(" "))
    }

    @Test
    fun payload_keepsOldEncodingForToggle() {
        assertEquals("", GestureAction.ToggleMute().payload)
        assertEquals("1", GestureAction.ToggleMute(RingerMode.RING).payload)
        assertEquals("2", GestureAction.ToggleMute(RingerMode.VIBRATE).payload)
        assertEquals("3", GestureAction.ToggleMute(RingerMode.SILENT).payload)
    }

    @Test
    fun from_decodesModeFromPayload() {
        assertEquals(
            GestureAction.ToggleMute(RingerMode.VIBRATE),
            GestureAction.from(GestureActionType.TOGGLE_MUTE, "2"),
        )
        // 旧数据：空载荷 = 切换。
        assertEquals(
            GestureAction.ToggleMute(),
            GestureAction.from(GestureActionType.TOGGLE_MUTE, ""),
        )
    }

    @Test
    fun withRingerMode_onlyTouchesRingerActions() {
        val mute = GestureAction.ToggleMute(RingerMode.RING)
        assertEquals(
            GestureAction.ToggleMute(RingerMode.SILENT),
            mute.withRingerModeOrSelf(RingerMode.SILENT),
        )
        assertEquals(
            GestureAction.ToggleWifi(),
            GestureAction.ToggleWifi().withRingerModeOrSelf(RingerMode.SILENT),
        )
        assertNull(GestureAction.ToggleWifi().ringerModeOrNull())
    }
}
