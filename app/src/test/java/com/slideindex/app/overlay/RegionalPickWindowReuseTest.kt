package com.slideindex.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalPickWindowReuseTest {
    @Test
    fun attachedWindowFromCurrentHostCanBeReused() {
        assertTrue(canReuseRegionalPickWindow(isAttached = true, windowGeneration = 4, hostGeneration = 4))
    }

    @Test
    fun detachedWindowFromCurrentHostMustBeRebuilt() {
        assertFalse(canReuseRegionalPickWindow(isAttached = false, windowGeneration = 4, hostGeneration = 4))
    }

    @Test
    fun windowFromPreviousHostMustBeRebuiltEvenIfItReportsAttached() {
        assertFalse(canReuseRegionalPickWindow(isAttached = true, windowGeneration = 4, hostGeneration = 5))
    }
}
