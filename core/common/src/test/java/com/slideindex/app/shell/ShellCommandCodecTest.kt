package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandCodecTest {
    @Test
    fun legacyCommandWithoutModeDefaultsToShizuku() {
        val legacy = listOf("id-1", "旧命令", "echo hi").joinToString("\u001E")

        val decoded = ShellCommandCodec.decode(legacy)

        assertEquals(ShellExecutionMode.SHIZUKU, decoded?.executionMode)
    }

    @Test
    fun explicitModeIsPreserved() {
        val command = ShellCommand(
            label = "需要 Root",
            command = "id",
            executionMode = ShellExecutionMode.ROOT,
        )

        val decoded = ShellCommandCodec.decode(ShellCommandCodec.encode(command))

        assertEquals(ShellExecutionMode.ROOT, decoded?.executionMode)
    }
}
