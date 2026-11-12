package com.slideindex.app.gesture

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「打开小组件面板」动作的页引用：编码往返、归一化、删页重映射。
 *
 * 覆盖两种落库形态：动作负载（`\u001C页 id`）与手势规则相邻字段（`\u001F页 id`）。
 */
class WidgetPanelActionRefsTest {

    private val removedPageId = "2"
    private val fallbackPageId = "1"

    @Test
    fun actionPayloadRoundTripsPageId() {
        val item = QuickLauncherItem.action(GestureAction.WidgetPopupOverlay(removedPageId), "小组件面板")

        val parsed = QuickLauncherItemCodec.parseActionPayload(item.payload)

        assertEquals(GestureAction.WidgetPopupOverlay(removedPageId), parsed)
    }

    @Test
    fun blankPayloadDecodesToFirstPage() {
        val item = QuickLauncherItem.action(GestureAction.WidgetPopupOverlay(), "小组件面板")

        val parsed = QuickLauncherItemCodec.parseActionPayload(item.payload)

        assertTrue(parsed is GestureAction.WidgetPopupOverlay)
        assertEquals("", (parsed as GestureAction.WidgetPopupOverlay).panelId)
    }

    @Test
    fun gestureRuleRoundTripsPageId() {
        val rule = GestureRule(
            id = GestureRule.slotId(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, "default"),
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.WidgetPopupOverlay(removedPageId),
        )

        val decoded = GestureRuleCodec.decode(GestureRuleCodec.encode(rule))

        assertEquals(GestureAction.WidgetPopupOverlay(removedPageId), decoded?.action)
    }

    @Test
    fun remapsQuickLauncherItemActionPayload() {
        val item = QuickLauncherItem.action(GestureAction.WidgetPopupOverlay(removedPageId), "小组件面板")

        val remapped = WidgetPanelActionRefs.remap(
            raw = QuickLauncherItemCodec.encode(item),
            removedPageId = removedPageId,
            fallbackPageId = fallbackPageId,
        )

        assertEquals(
            GestureAction.WidgetPopupOverlay(fallbackPageId),
            QuickLauncherItemCodec.decode(remapped)?.payload?.let(QuickLauncherItemCodec::parseActionPayload),
        )
    }

    @Test
    fun remapsGestureRuleActionField() {
        val rule = GestureRule(
            id = GestureRule.slotId(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, "default"),
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.WidgetPopupOverlay(removedPageId),
        )

        val remapped = WidgetPanelActionRefs.remap(
            raw = GestureRuleCodec.encode(rule),
            removedPageId = removedPageId,
            fallbackPageId = fallbackPageId,
        )

        assertEquals(GestureAction.WidgetPopupOverlay(fallbackPageId), GestureRuleCodec.decode(remapped)?.action)
    }

    @Test
    fun keepsOtherPageReferencesUntouched() {
        val raw = QuickLauncherItemCodec.encode(
            QuickLauncherItem.action(GestureAction.WidgetPopupOverlay("7"), "小组件面板"),
        )

        val remapped = WidgetPanelActionRefs.remap(raw, removedPageId, fallbackPageId)

        assertEquals(raw, remapped)
    }

    @Test
    fun doesNotRewritesLongerPageIdWithSamePrefix() {
        // 页 id 是十进制 Long，"1" 是 "12" 的前缀：删 1 不能把 12 改成 22/1x。
        val raw = QuickLauncherItemCodec.encode(
            QuickLauncherItem.action(GestureAction.WidgetPopupOverlay("12"), "小组件面板"),
        )

        val remapped = WidgetPanelActionRefs.remap(raw, removedPageId = "1", fallbackPageId = "9")

        assertEquals(
            GestureAction.WidgetPopupOverlay("12"),
            QuickLauncherItemCodec.decode(remapped)?.payload?.let(QuickLauncherItemCodec::parseActionPayload),
        )
    }

    @Test
    fun remapsSetOfValues() {
        val raw = QuickLauncherItemCodec.encode(
            QuickLauncherItem.action(GestureAction.WidgetPopupOverlay(removedPageId), "小组件面板"),
        )

        val remapped = WidgetPanelActionRefs.remap(setOf(raw), removedPageId, fallbackPageId)

        assertEquals(1, remapped.size)
        assertEquals(
            GestureAction.WidgetPopupOverlay(fallbackPageId),
            QuickLauncherItemCodec.decode(remapped.first())?.payload?.let(QuickLauncherItemCodec::parseActionPayload),
        )
    }

    @Test
    fun normalizePinsBlankAndStaleReferences() {
        val valid = setOf("1", "2", "3")

        assertEquals(
            GestureAction.WidgetPopupOverlay("1"),
            WidgetPanelActionRefs.normalize(GestureAction.WidgetPopupOverlay(""), valid, "1"),
        )
        assertEquals(
            GestureAction.WidgetPopupOverlay("1"),
            WidgetPanelActionRefs.normalize(GestureAction.WidgetPopupOverlay("9"), valid, "1"),
        )
        assertEquals(
            GestureAction.WidgetPopupOverlay("2"),
            WidgetPanelActionRefs.normalize(GestureAction.WidgetPopupOverlay("2"), valid, "1"),
        )
    }

    @Test
    fun normalizeKeepsOtherActions() {
        val action = GestureAction.QuickLauncher("panel")

        assertSame(action, WidgetPanelActionRefs.normalize(action, setOf("1"), "1"))
    }
}
