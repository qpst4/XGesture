package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandLabelResolverTest {

    private val commands = listOf(
        ShellCommand(label = "夜间模式", command = "cmd uimode night yes"),
        ShellCommand(label = "  ", command = "settings put global x 1"),
    )

    @Test
    fun `matches after trimming both sides`() {
        assertEquals(
            "夜间模式",
            ShellCommandLabelResolver.customLabelFor("  cmd uimode night yes  ", commands),
        )
    }

    @Test
    fun `blank custom label is ignored`() {
        assertNull(ShellCommandLabelResolver.customLabelFor("settings put global x 1", commands))
    }

    @Test
    fun `unknown or blank command returns null`() {
        assertNull(ShellCommandLabelResolver.customLabelFor("echo hi", commands))
        assertNull(ShellCommandLabelResolver.customLabelFor("   ", commands))
    }
}
