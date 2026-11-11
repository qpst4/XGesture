package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandModeResolverTest {

    private val rootCommand = ShellCommand(
        id = "root-1",
        label = "需要 Root",
        command = "id",
        executionMode = ShellExecutionMode.ROOT,
    )

    private val shizukuCommand = ShellCommand(
        id = "shizuku-1",
        label = "普通命令",
        command = "dumpsys battery",
        executionMode = ShellExecutionMode.SHIZUKU,
    )

    private val legacyCommand = ShellCommand(
        id = "legacy-1",
        label = "旧命令",
        command = "getprop ro.build.version.sdk",
        executionMode = null,
    )

    @Test
    fun shortcutMatchesSourceCommandByIdAndText() {
        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = "root-1",
            command = "id",
            savedCommands = listOf(rootCommand, shizukuCommand),
            defaultMode = ShellExecutionMode.SHIZUKU,
        )

        assertEquals(ShellExecutionMode.ROOT, mode)
    }

    @Test
    fun shortcutFallsBackToUniqueTextMatchWhenIdIsStale() {
        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = "deleted-command-id",
            command = "id",
            savedCommands = listOf(rootCommand, shizukuCommand),
            defaultMode = ShellExecutionMode.SHIZUKU,
        )

        assertEquals(ShellExecutionMode.ROOT, mode)
    }

    @Test
    fun shortcutFallsBackToDefaultWhenTextIsAmbiguous() {
        val duplicate = rootCommand.copy(id = "root-2", command = "id", executionMode = ShellExecutionMode.SHIZUKU)

        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = "missing",
            command = "id",
            savedCommands = listOf(rootCommand, duplicate),
            defaultMode = ShellExecutionMode.ROOT,
        )

        assertEquals(ShellExecutionMode.ROOT, mode)
    }

    @Test
    fun shortcutFallsBackToDefaultWhenNothingMatches() {
        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = null,
            command = "input tap 1 1",
            savedCommands = listOf(rootCommand, shizukuCommand),
            defaultMode = ShellExecutionMode.SHIZUKU,
        )

        assertEquals(ShellExecutionMode.SHIZUKU, mode)
    }

    @Test
    fun legacyCommandWithoutModeFollowsDefault() {
        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = "legacy-1",
            command = "getprop ro.build.version.sdk",
            savedCommands = listOf(legacyCommand),
            defaultMode = ShellExecutionMode.ROOT,
        )

        assertEquals(ShellExecutionMode.ROOT, mode)
    }

    @Test
    fun blankCommandFollowsDefault() {
        val mode = ShellCommandModeResolver.forShortcut(
            shortcutId = null,
            command = "   ",
            savedCommands = emptyList(),
            defaultMode = ShellExecutionMode.SHIZUKU,
        )

        assertEquals(ShellExecutionMode.SHIZUKU, mode)
    }
}
