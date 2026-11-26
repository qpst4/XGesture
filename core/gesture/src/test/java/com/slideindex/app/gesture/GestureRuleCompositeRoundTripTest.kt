package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 触钮规则（GestureRuleCodec）必须能安全承载「组合动作」。
 *
 * 回归背景：组合的子动作分隔符曾是 `\u001F`，与规则字段分隔符相同；
 * 组合 2 个以上动作时 payload 里多出分隔符，规则解码按段数判断失败，
 * 动作变成 None（反馈：触钮里配两个动作后“没选中组合动作”）。
 */
class GestureRuleCompositeRoundTripTest {

    private fun rule(action: GestureAction) = GestureRule(
        id = "rule-1",
        side = PanelSide.LEFT,
        trigger = GestureTriggerType.SHORT_SINGLE_TAP,
        action = action,
        priority = 3,
        enabled = true,
        triggerMode = GestureTriggerMode.DEFAULT,
        handleId = "handle-1",
    )

    @Test
    fun compositeWithTwoChildrenSurvivesRuleCodec() {
        val action = GestureAction.Composite(
            listOf(GestureAction.FreezerPanel, GestureAction.LockScreen),
        )
        val decoded = GestureRuleCodec.decode(GestureRuleCodec.encode(rule(action)))
        assertNotNull(decoded)
        assertEquals(action, decoded!!.action)
        assertEquals(2, (decoded.action as GestureAction.Composite).actions.size)
    }

    @Test
    fun compositeWithThreeChildrenSurvivesRuleCodec() {
        val action = GestureAction.Composite(
            listOf(GestureAction.FreezerPanel, GestureAction.LockScreen, GestureAction.Home),
        )
        val decoded = GestureRuleCodec.decode(GestureRuleCodec.encode(rule(action)))
        assertNotNull(decoded)
        assertEquals(action, decoded!!.action)
    }

    @Test
    fun legacyEightFieldRuleStillDecodes() {
        // 旧格式：id, side, trigger, type, body, priority, enabled, triggerMode
        val raw = listOf(
            "legacy",
            PanelSide.RIGHT.ordinal.toString(),
            GestureTriggerType.SHORT_SINGLE_TAP.id.toString(),
            GestureActionType.HOME.id.toString(),
            "",
            "0",
            "1",
            GestureTriggerMode.DEFAULT.id.toString(),
        ).joinToString("\u001F")
        val decoded = GestureRuleCodec.decode(raw)
        assertNotNull(decoded)
        assertEquals("legacy", decoded!!.id)
        assertEquals(GestureAction.Home, decoded.action)
    }
}
