package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ShakeMenuRuntimeTest {

    private val leftFlipMenu = ShakeMenu(
        id = "menu-left",
        name = "左翻菜单",
        actions = mapOf(
            ShakeGestureType.LEFT_FLIP to GestureAction.Home,
            ShakeGestureType.RIGHT_FLIP to GestureAction.Recents,
        ),
    )

    private val emptyMenu = ShakeMenu(id = "menu-empty", name = "空菜单")

    @Test
    fun decide_withoutSession_fallsThrough() {
        val outcome = ShakeMenuRuntime.decide(
            session = null,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.LEFT_FLIP,
            nowElapsedMs = 1_000L,
        )
        assertNull(outcome.session)
        assertSame(ShakeMenuDecision.NotInMenu, outcome.decision)
    }

    @Test
    fun decide_insideGuard_coolsDownAndKeepsSession() {
        val session = ShakeMenuRuntime.open(leftFlipMenu.id, nowElapsedMs = 1_000L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.LEFT_FLIP,
            nowElapsedMs = 1_100L,
            guardMs = 300L,
        )
        assertEquals(session, outcome.session)
        assertEquals(ShakeMenuDecision.CoolingDown(leftFlipMenu), outcome.decision)
    }

    @Test
    fun decide_afterGuard_executesAndClosesSession() {
        val session = ShakeMenuRuntime.open(leftFlipMenu.id, nowElapsedMs = 1_000L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.RIGHT_FLIP,
            nowElapsedMs = 1_400L,
            guardMs = 300L,
        )
        assertNull(outcome.session)
        assertEquals(
            ShakeMenuDecision.Execute(leftFlipMenu, GestureAction.Recents),
            outcome.decision,
        )
    }

    @Test
    fun decide_unboundDirection_closesSession() {
        val session = ShakeMenuRuntime.open(leftFlipMenu.id, nowElapsedMs = 1_000L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.FORWARD_FLIP,
            nowElapsedMs = 2_000L,
            guardMs = 300L,
        )
        assertNull(outcome.session)
        assertEquals(ShakeMenuDecision.Unbound(leftFlipMenu), outcome.decision)
    }

    @Test
    fun decide_noneAction_closesSession() {
        val menu = leftFlipMenu.copy(
            actions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.None),
        )
        val session = ShakeMenuRuntime.open(menu.id, nowElapsedMs = 0L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(menu),
            type = ShakeGestureType.LEFT_FLIP,
            nowElapsedMs = 1_000L,
        )
        assertEquals(ShakeMenuDecision.Unbound(menu), outcome.decision)
        assertNull(outcome.session)
    }

    @Test
    fun decide_timeout_expiresSession() {
        val session = ShakeMenuRuntime.open(leftFlipMenu.id, nowElapsedMs = 1_000L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.LEFT_FLIP,
            nowElapsedMs = 1_000L + ShakeMenuRuntime.DEFAULT_SESSION_TIMEOUT_MS,
            guardMs = 300L,
        )
        assertNull(outcome.session)
        assertEquals(ShakeMenuDecision.Expired(leftFlipMenu.id), outcome.decision)
    }

    @Test
    fun decide_removedMenu_expiresSession() {
        val session = ShakeMenuRuntime.open("deleted-menu", nowElapsedMs = 0L)
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = listOf(leftFlipMenu),
            type = ShakeGestureType.LEFT_FLIP,
            nowElapsedMs = 500L,
        )
        assertNull(outcome.session)
        assertEquals(ShakeMenuDecision.Expired("deleted-menu"), outcome.decision)
    }

    @Test
    fun resolveMenu_fallsBackToFirstWhenIdMissing() {
        val resolved = ShakeMenuRuntime.resolveMenu(listOf(leftFlipMenu, emptyMenu), "missing")
        assertEquals(leftFlipMenu, resolved)
    }

    @Test
    fun resolveMenu_blankIdPicksFirst() {
        assertEquals(emptyMenu, ShakeMenuRuntime.resolveMenu(listOf(emptyMenu), ""))
    }

    @Test
    fun resolveMenu_noMenusReturnsNull() {
        assertNull(ShakeMenuRuntime.resolveMenu(emptyList(), "any"))
    }

    @Test
    fun guardIsClamped() {
        assertEquals(ShakeMenuRuntime.GUARD_MS_MIN, ShakeMenuRuntime.clampGuardMs(0))
        assertEquals(ShakeMenuRuntime.GUARD_MS_MAX, ShakeMenuRuntime.clampGuardMs(9_999))
        assertEquals(300L, ShakeMenuRuntime.clampGuardMs(300))
    }

    @Test
    fun configuredDirections_excludesNoneAndKeepsOrder() {
        val menu = ShakeMenu(
            id = "m",
            actions = mapOf(
                ShakeGestureType.RIGHT_FLIP to GestureAction.Recents,
                ShakeGestureType.LEFT_FLIP to GestureAction.None,
                ShakeGestureType.BACKWARD_FLIP to GestureAction.Home,
            ),
        )
        assertEquals(
            listOf(ShakeGestureType.RIGHT_FLIP, ShakeGestureType.BACKWARD_FLIP),
            menu.configuredDirections,
        )
    }
}
