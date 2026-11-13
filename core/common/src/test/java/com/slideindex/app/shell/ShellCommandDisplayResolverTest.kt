package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandDisplayResolverTest {

    private val saved = listOf(
        ShellCommand(id = "night", label = "夜间模式", command = "cmd uimode night yes"),
        ShellCommand(
            id = "toast",
            label = "电量",
            command = "dumpsys battery",
            resultDisplay = ShellResultDisplay.TOAST,
        ),
    )

    @Test
    fun `unique text match returns its display setting`() {
        val matched = ShellCommandDisplayResolver.resolve("  dumpsys battery  ", saved)

        assertEquals(ShellResultDisplay.TOAST, matched?.resultDisplay)
        assertEquals("电量", matched?.label)
    }

    @Test
    fun `duplicate command text is ambiguous and falls back`() {
        val duplicated = saved + ShellCommand(
            id = "toast-2",
            label = "电量（副本）",
            command = "dumpsys battery",
            resultDisplay = ShellResultDisplay.NONE,
        )

        assertNull(ShellCommandDisplayResolver.resolve("dumpsys battery", duplicated))
    }

    @Test
    fun `preferred id wins when the same text exists twice`() {
        val duplicated = saved + ShellCommand(
            id = "toast-2",
            label = "电量（副本）",
            command = "dumpsys battery",
            resultDisplay = ShellResultDisplay.NONE,
        )

        val matched = ShellCommandDisplayResolver.resolve(
            commandLine = "dumpsys battery",
            savedCommands = duplicated,
            preferredId = "toast-2",
        )

        assertEquals("toast-2", matched?.id)
        assertEquals(ShellResultDisplay.NONE, matched?.resultDisplay)
    }

    @Test
    fun `stale preferred id falls back to unique text match`() {
        val matched = ShellCommandDisplayResolver.resolve(
            commandLine = "dumpsys battery",
            savedCommands = saved,
            preferredId = "deleted-command-id",
        )

        assertEquals("toast", matched?.id)
    }

    @Test
    fun `preferred id with edited text does not match`() {
        val matched = ShellCommandDisplayResolver.resolve(
            commandLine = "dumpsys battery reset",
            savedCommands = saved,
            preferredId = "toast",
        )

        assertNull(matched)
    }

    @Test
    fun `unknown or blank command returns null`() {
        assertNull(ShellCommandDisplayResolver.resolve("echo hi", saved))
        assertNull(ShellCommandDisplayResolver.resolve("   ", saved))
        assertNull(ShellCommandDisplayResolver.resolve("dumpsys battery", emptyList()))
    }

    @Test
    fun `legacy command without explicit display resolves to dialog default`() {
        val legacy = ShellCommand(id = "legacy", label = "旧命令", command = "getprop ro.build.sdk")

        assertEquals(
            ShellResultDisplay.DIALOG,
            ShellCommandDisplayResolver.resolve("getprop ro.build.sdk", listOf(legacy))?.resultDisplay,
        )
    }
}
