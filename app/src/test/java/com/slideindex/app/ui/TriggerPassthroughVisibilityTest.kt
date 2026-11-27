package com.slideindex.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerPassthroughVisibilityTest {

    @Test
    fun moduleReady_showsSwitch() {
        assertEquals(
            PassthroughRowVisibility.Enabled,
            triggerPassthroughVisibility(moduleReady = true, passthrough = false),
        )
    }

    @Test
    fun moduleNotReady_andOff_hidesSwitch() {
        assertEquals(
            PassthroughRowVisibility.Hidden,
            triggerPassthroughVisibility(moduleReady = false, passthrough = false),
        )
    }

    @Test
    fun moduleNotReady_butAlreadyOn_stillShowsSoItCanBeTurnedOff() {
        assertEquals(
            PassthroughRowVisibility.Enabled,
            triggerPassthroughVisibility(moduleReady = false, passthrough = true),
        )
    }

    @Test
    fun moduleUnknown_andOff_isDisabledWhileProbing() {
        assertEquals(
            PassthroughRowVisibility.Disabled,
            triggerPassthroughVisibility(moduleReady = null, passthrough = false),
        )
    }

    @Test
    fun moduleUnknown_butAlreadyOn_stillShows() {
        assertEquals(
            PassthroughRowVisibility.Enabled,
            triggerPassthroughVisibility(moduleReady = null, passthrough = true),
        )
    }
}
