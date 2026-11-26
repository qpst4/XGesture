package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合动作（Composite）的编码/解码与嵌套防护。
 */
class GestureActionCompositeTest {

    private fun assertSameActions(
        expected: List<GestureAction>,
        actual: List<GestureAction>,
    ) {
        // 只比较「类型 + payload」：label 之类纯展示字段本来就不进 payload（与单动作存槽一致）。
        assertEquals(
            expected.map { it.type to it.payload },
            actual.map { it.type to it.payload },
        )
    }

    @Test
    fun composite_roundTripsChildren() {
        val composite = GestureAction.Composite(
            listOf(
                GestureAction.Home,
                GestureAction.ToggleWifi(),
                GestureAction.LaunchApp("com.example.chat"),
                GestureAction.Recents,
            ),
        )
        val decoded = GestureAction.from(composite.type, composite.payload) as GestureAction.Composite
        assertSameActions(composite.actions, decoded.actions)
    }

    @Test
    fun composite_payloadKeepsPayloadsWithSeparatorChars() {
        // 子动作正文里带分隔符（LauncherShortcut 的 payloadKey 可能含 \u001D）时也要能还原。
        val uriLike = "intent:#Intent;component=com.example/.Main;i.data=1\u001D2;end"
        val composite = GestureAction.Composite(
            listOf(
                GestureAction.LaunchShortcut(payloadKey = uriLike, label = "示例"),
                GestureAction.LockScreen,
            ),
        )
        val decoded = GestureAction.from(composite.type, composite.payload) as GestureAction.Composite
        assertSameActions(composite.actions, decoded.actions)
    }

    @Test
    fun composite_dropsNestedCompositesAndNone() {
        val nested = GestureAction.Composite(
            listOf(
                GestureAction.None,
                GestureAction.Composite(listOf(GestureAction.Home)),
                GestureAction.Back,
            ),
        )
        val decoded = GestureAction.from(nested.type, nested.payload) as GestureAction.Composite
        assertEquals(listOf(GestureAction.Back), decoded.actions)
    }

    @Test
    fun composite_emptyPayloadDecodesToEmpty() {
        val decoded = GestureAction.from(GestureActionType.COMPOSITE, "")
        assertTrue(decoded is GestureAction.Composite)
        assertTrue((decoded as GestureAction.Composite).actions.isEmpty())
    }

    @Test
    fun compositeRef_roundTrip() {
        val ref = GestureAction.CompositeRef("preset-uuid")
        val decoded = GestureAction.from(GestureActionType.COMPOSITE_REF, ref.payload)
        assertEquals(ref, decoded)
        assertEquals("preset-uuid", decoded.payload)
    }
}
