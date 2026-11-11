package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandCodecTest {
    @Test
    fun legacyCommandWithoutModeStaysUnset() {
        val legacy = listOf("id-1", "旧命令", "echo hi").joinToString("\u001E")

        val decoded = ShellCommandCodec.decode(legacy)

        assertNull(decoded?.executionMode)
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
