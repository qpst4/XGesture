package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GestureActionNormalizedTest {

    @Test
    fun from_legacyRemindTypes_mapToRemind() {
        val legacyTypes = listOf(
            GestureActionType.REMIND_1M,
            GestureActionType.REMIND_3M,
            GestureActionType.REMIND_5M,
            GestureActionType.REMIND_10M,
            GestureActionType.REMIND_15M,
        )
        legacyTypes.forEach { type ->
            assertEquals(GestureAction.Remind, GestureAction.from(type, ""))
        }
    }

    @Test
    fun normalized_remind_isIdempotent() {
        assertSame(GestureAction.Remind, GestureAction.Remind.normalized())
    }

    @Test
    fun normalized_otherActions_unchanged() {
        assertSame(GestureAction.Back, GestureAction.Back.normalized())
    }

    @Test
    fun from_voiceSearchAndAssistant_mapCorrectly() {
        assertEquals(GestureAction.VoiceSearch, GestureAction.from(GestureActionType.VOICE_SEARCH, ""))
        assertEquals(GestureAction.VoiceAssistant, GestureAction.from(GestureActionType.VOICE_ASSISTANT, ""))
        assertEquals(GestureActionType.VOICE_SEARCH, GestureActionType.fromId(71))
        assertEquals(GestureActionType.VOICE_ASSISTANT, GestureActionType.fromId(72))
        assertEquals(GestureAction.ToggleAutoRotate, GestureAction.from(GestureActionType.TOGGLE_AUTO_ROTATE, ""))
        assertEquals(GestureAction.ForcePortrait, GestureAction.from(GestureActionType.FORCE_PORTRAIT, ""))
        assertEquals(GestureAction.ForceLandscape, GestureAction.from(GestureActionType.FORCE_LANDSCAPE, ""))
        assertEquals(GestureActionType.TOGGLE_AUTO_ROTATE, GestureActionType.fromId(73))
        assertEquals(GestureActionType.FORCE_PORTRAIT, GestureActionType.fromId(74))
        assertEquals(GestureActionType.FORCE_LANDSCAPE, GestureActionType.fromId(75))
        assertEquals(GestureAction.OpenInternetPanel, GestureAction.from(GestureActionType.OPEN_INTERNET_PANEL, ""))
        assertEquals(GestureAction.OpenVolumePanel, GestureAction.from(GestureActionType.OPEN_VOLUME_PANEL, ""))
        assertEquals(
            GestureAction.OpenLink(url = "https://example.com", label = "Example"),
            GestureAction.from(GestureActionType.OPEN_LINK, "https://example.com\u001DExample"),
        )
        assertEquals(
            GestureAction.OpenLink(url = "https://example.com", label = "Example"),
            GestureAction.from(GestureActionType.OPEN_LINK, "https://example.com\u001FExample"),
        )
        assertEquals(GestureAction.CurrentAppInfo, GestureAction.from(GestureActionType.CURRENT_APP_INFO, ""))
        val keyEvent = GestureAction.SimulateKeyEvent(82, "菜单", true)
        assertEquals(keyEvent, GestureAction.from(GestureActionType.SIMULATE_KEY_EVENT, keyEvent.payload))
        assertEquals(GestureActionType.OPEN_INTERNET_PANEL, GestureActionType.fromId(76))
        assertEquals(GestureActionType.OPEN_VOLUME_PANEL, GestureActionType.fromId(77))
        assertEquals(GestureActionType.OPEN_LINK, GestureActionType.fromId(78))
        assertEquals(GestureActionType.CURRENT_APP_INFO, GestureActionType.fromId(79))
        assertEquals(GestureActionType.SIMULATE_KEY_EVENT, GestureActionType.fromId(80))
        assertEquals(GestureAction.ScreenOffKeepAwake, GestureAction.from(GestureActionType.SCREEN_OFF_KEEP_AWAKE, ""))
        assertEquals(GestureActionType.SCREEN_OFF_KEEP_AWAKE, GestureActionType.fromId(81))
        assertEquals(GestureAction.PinToScreen, GestureAction.from(GestureActionType.PIN_TO_SCREEN, ""))
        assertEquals(GestureActionType.PIN_TO_SCREEN, GestureActionType.fromId(82))
        assertEquals(GestureAction.AppCarouselSwitcher, GestureAction.from(GestureActionType.APP_CAROUSEL_SWITCHER, ""))
        assertEquals(GestureActionType.APP_CAROUSEL_SWITCHER, GestureActionType.fromId(83))
        assertEquals(GestureAction.ForegroundActivityInspector, GestureAction.from(GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR, ""))
        assertEquals(GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR, GestureActionType.fromId(84))
    }

    @Test
    fun gestureActionType_idsAreStableAndUnique() {
        // id 是持久化 ABI：GestureRuleCodec 存的就是 type.id，ShakeSettingsMutator 亦然。
        // 所以既不允许重复，也不允许给已经发布过的动作改号。
        // 93 = 强行停止当前应用（main 上已发布）；94 = 快捷轮盘（PR #18 合入时顺延）。
        // 合 PR #18 时两边都写了 93，fromId(93) 只会命中先声明的那个，后果是用户存的
        // 「快捷轮盘」手势被解析成「强行停止当前应用」（真杀前台进程）——这条测试守住它。
        assertEquals(GestureActionType.FORCE_STOP_CURRENT_APP, GestureActionType.fromId(93))
        assertEquals(GestureActionType.QUICK_WHEEL, GestureActionType.fromId(94))

        val ids = GestureActionType.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun toggleFloatBall_roundTripsThroughId() {
        // 108 = 「切换悬浮球」：id 是持久化 ABI，新动作只能往后追加（见上一条测试）。
        assertEquals(GestureActionType.TOGGLE_FLOAT_BALL, GestureActionType.fromId(108))
        assertEquals(
            GestureAction.ToggleFloatBall,
            GestureAction.from(GestureActionType.TOGGLE_FLOAT_BALL, ""),
        )
    }

    @Test
    fun volumeSlider_roundTripsThroughId() {
        // 115 = 「音量条（不改音量）」：id 是持久化 ABI，新动作只能往后追加（见上一条测试）。
        assertEquals(GestureActionType.OPEN_VOLUME_SLIDER, GestureActionType.fromId(115))
        assertEquals(
            GestureAction.OpenVolumeSlider,
            GestureAction.from(GestureActionType.OPEN_VOLUME_SLIDER, ""),
        )
    }

    @Test
    fun gestureRuleCodec_decodeAll_migratesLegacyRemindActions() {
        val rule = GestureRule.slot(
            side = com.slideindex.app.overlay.PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Remind5m,
        )
        val encoded = GestureRuleCodec.encode(rule)
        val decoded = GestureRuleCodec.decodeAll(setOf(encoded)).single()
        assertEquals(GestureAction.Remind, decoded.action)
    }
}
