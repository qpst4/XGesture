package com.slideindex.app.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSessionContinuousPickTest {

    @Test
    fun initialFlags_areFalse() {
        val pick = GestureSessionContinuousPick()

        assertFalse(pick.taskSwitcherActive())
        assertFalse(pick.quickLauncherActive())
        assertFalse(pick.shellActive())
        assertFalse(pick.honeycombActive())
        assertFalse(pick.floatNavActive())
    }

    @Test
    fun reset_clearsAllFlags() {
        val pick = GestureSessionContinuousPick().apply {
            taskSwitcher = true
            quickLauncher = true
            shell = true
            honeycomb = true
        }

        pick.reset()

        assertFalse(pick.taskSwitcherActive())
        assertFalse(pick.quickLauncherActive())
        assertFalse(pick.shellActive())
        assertFalse(pick.honeycombActive())
    }

    @Test
    fun clearQuickLauncher_onlyClearsQuickLauncher() {
        val pick = GestureSessionContinuousPick().apply {
            taskSwitcher = true
            quickLauncher = true
            shell = true
            honeycomb = true
        }

        pick.clearQuickLauncher()

        assertTrue(pick.taskSwitcherActive())
        assertFalse(pick.quickLauncherActive())
        assertTrue(pick.shellActive())
        assertTrue(pick.honeycombActive())
    }

    @Test
    fun clearShell_onlyClearsShell() {
        val pick = GestureSessionContinuousPick().apply {
            taskSwitcher = true
            quickLauncher = true
            shell = true
            honeycomb = true
        }

        pick.clearShell()

        assertTrue(pick.taskSwitcherActive())
        assertTrue(pick.quickLauncherActive())
        assertFalse(pick.shellActive())
        assertTrue(pick.honeycombActive())
    }

    @Test
    fun clearHoneycomb_onlyClearsHoneycomb() {
        val pick = GestureSessionContinuousPick().apply {
            taskSwitcher = true
            quickLauncher = true
            shell = true
            honeycomb = true
        }

        pick.clearHoneycomb()

        assertTrue(pick.taskSwitcherActive())
        assertTrue(pick.quickLauncherActive())
        assertTrue(pick.shellActive())
        assertFalse(pick.honeycombActive())
    }

    @Test
    fun floatNav_isClearedIndependentlyAndByReset() {
        val pick = GestureSessionContinuousPick().apply {
            quickLauncher = true
            floatNav = true
        }

        pick.clearFloatNav()

        assertFalse(pick.floatNavActive())
        assertTrue(pick.quickLauncherActive())

        pick.floatNav = true
        pick.reset()
        assertFalse(pick.floatNavActive())
    }
}
