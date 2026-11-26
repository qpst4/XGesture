package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「循环切换输入法」的目标决策：配置目标后必须能在「当前 ↔ 目标」之间来回切，
 * 配置为空或目标已失效时必须退回旧的轮换行为。
 */
class CycleInputMethodPlannerTest {

    private val a = "com.a/.A"
    private val b = "com.b/.B"
    private val c = "com.c/.C"
    private val enabled = listOf(a, b, c)

    @Test
    fun noTarget_rotatesToNextEnabled() {
        val decision = CycleInputMethodPlanner.decide(enabled, a, "", "")

        assertEquals(b, decision.switchToImeId)
        assertNull(decision.rememberPreviousImeId)
        assertEquals(false, decision.clearPrevious)
        assertNull(decision.singleEnabledImeId)
    }

    @Test
    fun noTarget_currentNotInList_startsAtFirst() {
        val decision = CycleInputMethodPlanner.decide(enabled, "com.gone/.X", "", "")

        assertEquals(a, decision.switchToImeId)
    }

    @Test
    fun noTarget_singleEnabled_reportsSingleInputMethod() {
        val decision = CycleInputMethodPlanner.decide(listOf(a), a, "", "")

        assertNull(decision.switchToImeId)
        assertEquals(a, decision.singleEnabledImeId)
    }

    @Test
    fun noEnabledInputMethod_doesNothing() {
        val decision = CycleInputMethodPlanner.decide(emptyList(), a, "", "")

        assertNull(decision.switchToImeId)
        assertNull(decision.singleEnabledImeId)
    }

    @Test
    fun configuredTarget_switchesAndRemembersCurrent() {
        val decision = CycleInputMethodPlanner.decide(enabled, a, b, "")

        assertEquals(b, decision.switchToImeId)
        assertEquals(a, decision.rememberPreviousImeId)
        assertEquals(false, decision.clearPrevious)
    }

    @Test
    fun atConfiguredTarget_switchesBackToPreviousAndClearsIt() {
        val decision = CycleInputMethodPlanner.decide(enabled, b, b, a)

        assertEquals(a, decision.switchToImeId)
        assertEquals(true, decision.clearPrevious)
        assertNull(decision.rememberPreviousImeId)
    }

    @Test
    fun atConfiguredTarget_stalePrevious_rotatesInsteadOfSticking() {
        val decision = CycleInputMethodPlanner.decide(enabled, b, b, "com.gone/.X")

        assertEquals(c, decision.switchToImeId)
    }

    @Test
    fun atConfiguredTarget_previousEqualsTarget_isIgnored() {
        val decision = CycleInputMethodPlanner.decide(enabled, b, b, b)

        assertEquals(c, decision.switchToImeId)
    }

    @Test
    fun staleConfiguredTarget_rotates() {
        val decision = CycleInputMethodPlanner.decide(enabled, a, "com.gone/.X", "")

        assertEquals(b, decision.switchToImeId)
        assertNull(decision.rememberPreviousImeId)
    }

    @Test
    fun currentMissing_withConfiguredTarget_switchesWithoutRemembering() {
        val decision = CycleInputMethodPlanner.decide(enabled, "", b, "")

        assertEquals(b, decision.switchToImeId)
        assertNull(decision.rememberPreviousImeId)
    }

    @Test
    fun singleEnabled_atConfiguredTarget_reportsSingleInputMethod() {
        val decision = CycleInputMethodPlanner.decide(listOf(b), b, b, "")

        assertNull(decision.switchToImeId)
        assertEquals(b, decision.singleEnabledImeId)
    }

    @Test
    fun excludedIme_isSkippedDuringRotation() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = a,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = setOf(b),
        )

        assertEquals(c, decision.switchToImeId)
    }

    @Test
    fun excludedCurrent_startsAtFirstRemainingCandidate() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = b,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = setOf(b),
        )

        assertEquals(a, decision.switchToImeId)
    }

    @Test
    fun allExcluded_fallsBackToAllEnabled() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = a,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = enabled.toSet(),
        )

        assertEquals(b, decision.switchToImeId)
    }

    @Test
    fun onlyOneCandidateLeft_andItIsCurrent_doesNothing() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = b,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = setOf(a, c),
        )

        assertNull(decision.switchToImeId)
        assertNull(decision.singleEnabledImeId)
    }

    @Test
    fun onlyOneCandidateLeft_andNotCurrent_switchesToIt() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = b,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = setOf(a, b),
        )

        assertEquals(c, decision.switchToImeId)
    }

    @Test
    fun configuredTarget_isNotAffectedByExclusion() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabled,
            currentImeId = a,
            targetImeId = c,
            previousImeId = "",
            excludedImeIds = setOf(c),
        )

        assertEquals(c, decision.switchToImeId)
    }

    @Test
    fun singleEnabled_isNotAffectedByExclusion() {
        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = listOf(a),
            currentImeId = a,
            targetImeId = "",
            previousImeId = "",
            excludedImeIds = setOf(a),
        )

        assertEquals(a, decision.singleEnabledImeId)
    }
}
