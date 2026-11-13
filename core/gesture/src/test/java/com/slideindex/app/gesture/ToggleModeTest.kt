package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「切换 / 打开 / 关闭」的数据层契约。
 *
 * 重点不是枚举本身，而是**旧数据兼容**：历史动作的 payload 都是空串，
 * 必须解析成「切换」，否则升级后所有 WiFi / 蓝牙 / NFC / 移动数据动作都会失效。
 */
class ToggleModeTest {

    @Test
    fun decode_roundTripsKnownPayloads() {
        assertEquals(ToggleMode.TOGGLE, ToggleMode.decode(""))
        assertEquals(ToggleMode.ON, ToggleMode.decode("1"))
        assertEquals(ToggleMode.OFF, ToggleMode.decode("0"))
    }

    @Test
    fun decode_fallsBackToToggleForGarbage() {
        // 坏存档 / 手改 / 未来新增的模式：宁可按旧行为（切换），也不能让动作整个失效。
        assertEquals(ToggleMode.TOGGLE, ToggleMode.decode("garbage"))
        assertEquals(ToggleMode.TOGGLE, ToggleMode.decode(" "))
    }

    @Test
    fun payload_keepsOldEncodingForToggle() {
        assertEquals("", GestureAction.ToggleWifi().payload)
        assertEquals("1", GestureAction.ToggleWifi(ToggleMode.ON).payload)
        assertEquals("0", GestureAction.ToggleWifi(ToggleMode.OFF).payload)
    }

    @Test
    fun from_decodesModeFromPayload() {
        assertEquals(
            GestureAction.ToggleWifi(ToggleMode.ON),
            GestureAction.from(GestureActionType.TOGGLE_WIFI, "1"),
        )
        assertEquals(
            GestureAction.ToggleBluetooth(ToggleMode.OFF),
            GestureAction.from(GestureActionType.TOGGLE_BLUETOOTH, "0"),
        )
        // 旧数据：空载荷 = 切换（默认值）。
        assertEquals(
            GestureAction.ToggleNfc(),
            GestureAction.from(GestureActionType.TOGGLE_NFC, ""),
        )
        // 新增的三个开关也按同一套编码解析。
        assertEquals(
            GestureAction.ToggleAirplane(ToggleMode.OFF),
            GestureAction.from(GestureActionType.TOGGLE_AIRPLANE, "0"),
        )
        assertEquals(
            GestureAction.ToggleSync(),
            GestureAction.from(GestureActionType.TOGGLE_SYNC, ""),
        )
        assertEquals(
            GestureAction.ToggleDarkMode(ToggleMode.ON),
            GestureAction.from(GestureActionType.TOGGLE_DARK_MODE, "1"),
        )
        assertEquals(
            GestureAction.ToggleLocation(ToggleMode.OFF),
            GestureAction.from(GestureActionType.TOGGLE_LOCATION, "0"),
        )
    }

    @Test
    fun withToggleMode_onlyTouchesToggleActions() {
        val wifi = GestureAction.ToggleWifi(ToggleMode.OFF)
        assertEquals(
            GestureAction.ToggleWifi(ToggleMode.ON),
            wifi.withToggleModeOrSelf(ToggleMode.ON),
        )
        assertEquals(
            GestureAction.None,
            GestureAction.None.withToggleModeOrSelf(ToggleMode.ON),
        )
        assertNull(GestureAction.None.toggleModeOrNull())
    }

    @Test
    fun resolve_mapsModeToTargetState() {
        assertEquals(true, ToggleMode.ON.resolve(current = false))
        assertEquals(true, ToggleMode.ON.resolve(current = true))
        assertEquals(false, ToggleMode.OFF.resolve(current = false))
        assertEquals(false, ToggleMode.OFF.resolve(current = true))
        // 切换 = 取反，与历史行为逐字节一致。
        assertEquals(false, ToggleMode.TOGGLE.resolve(current = true))
        assertEquals(true, ToggleMode.TOGGLE.resolve(current = false))
    }
}
