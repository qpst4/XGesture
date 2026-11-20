package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「声音模式」的数据层契约。
 *
 * 与其它开关的 [ToggleMode] 不同：它是系统真实的三种铃声状态 + 一个循环操作。
 * 旧数据（空 payload）必须是「切换」，否则升级后老绑定会失效。
 * 三个显式档是**开关**：不在该档就进该档，已在该档再触发一次就退出（[RingerMode.resolve]）。
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

    @Test
    fun resolve_toggleKeepsThreeModeCycle() {
        // 旧绑定（空 payload）的行为不能变：响铃 → 振动 → 静音 → 响铃。
        assertEquals(RingerMode.VIBRATE, RingerMode.TOGGLE.resolve(RingerMode.RING))
        assertEquals(RingerMode.SILENT, RingerMode.TOGGLE.resolve(RingerMode.VIBRATE))
        assertEquals(RingerMode.RING, RingerMode.TOGGLE.resolve(RingerMode.SILENT))
    }

    @Test
    fun resolve_explicitModeEntersWhenNotInThatMode() {
        // 不在该档 → 切到该档（B/C 都进 A）。
        assertEquals(RingerMode.SILENT, RingerMode.SILENT.resolve(RingerMode.RING))
        assertEquals(RingerMode.SILENT, RingerMode.SILENT.resolve(RingerMode.VIBRATE))
        assertEquals(RingerMode.VIBRATE, RingerMode.VIBRATE.resolve(RingerMode.RING))
        assertEquals(RingerMode.VIBRATE, RingerMode.VIBRATE.resolve(RingerMode.SILENT))
        assertEquals(RingerMode.RING, RingerMode.RING.resolve(RingerMode.VIBRATE))
        assertEquals(RingerMode.RING, RingerMode.RING.resolve(RingerMode.SILENT))
    }

    @Test
    fun resolve_explicitModeExitsWhenAlreadyInThatMode() {
        // 已在该档 → 再触发一次取消：静音/振动回到响铃；响铃只能往安静的方向走到静音。
        assertEquals(RingerMode.RING, RingerMode.SILENT.resolve(RingerMode.SILENT))
        assertEquals(RingerMode.RING, RingerMode.VIBRATE.resolve(RingerMode.VIBRATE))
        assertEquals(RingerMode.SILENT, RingerMode.RING.resolve(RingerMode.RING))
    }

    @Test
    fun resolve_neverReturnsToggle() {
        // 目标必须是系统真实档位；TOGGLE 只是"操作方式"，不能写回系统。
        RingerMode.entries.forEach { mode ->
            RingerMode.entries.forEach { current ->
                assertNotEquals(RingerMode.TOGGLE, mode.resolve(current))
            }
        }
    }

    @Test
    fun resolveRinger_onEntersTargetOnlyWhenNeeded() {
        // 「打开」= 设成该档；已经在该档就不写系统（null）。
        assertEquals(RingerMode.SILENT, ToggleMode.ON.resolveRinger(RingerMode.RING, RingerMode.SILENT))
        assertEquals(RingerMode.VIBRATE, ToggleMode.ON.resolveRinger(RingerMode.SILENT, RingerMode.VIBRATE))
        assertNull(ToggleMode.ON.resolveRinger(RingerMode.SILENT, RingerMode.SILENT))
    }

    @Test
    fun resolveRinger_offExitsTargetOnlyWhenInside() {
        // 「关闭」= 退出该档；本来就不在该档就不动。
        assertEquals(RingerMode.RING, ToggleMode.OFF.resolveRinger(RingerMode.SILENT, RingerMode.SILENT))
        assertEquals(RingerMode.RING, ToggleMode.OFF.resolveRinger(RingerMode.VIBRATE, RingerMode.VIBRATE))
        assertEquals(RingerMode.SILENT, ToggleMode.OFF.resolveRinger(RingerMode.RING, RingerMode.RING))
        assertNull(ToggleMode.OFF.resolveRinger(RingerMode.RING, RingerMode.SILENT))
    }

    @Test
    fun resolveRinger_toggleMatchesLegacyExplicitMode() {
        // 「切换」必须与旧「声音模式 · 档位」绑定逐格一致。
        RingerMode.entries.forEach { target ->
            if (target == RingerMode.TOGGLE) return@forEach
            RingerMode.entries.forEach { current ->
                if (current == RingerMode.TOGGLE) return@forEach
                assertEquals(target.resolve(current), ToggleMode.TOGGLE.resolveRinger(current, target))
            }
        }
    }
}
