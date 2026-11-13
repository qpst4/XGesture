package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellResultFeedbackPolicyTest {

    @Test
    fun `dialog always opens the result dialog`() {
        assertEquals(
            ShellResultFeedback.DIALOG,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.DIALOG, exitCode = 0),
        )
        assertEquals(
            ShellResultFeedback.DIALOG,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.DIALOG, exitCode = 2),
        )
    }

    @Test
    fun `toast always shows a toast`() {
        assertEquals(
            ShellResultFeedback.TOAST,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.TOAST, exitCode = 0),
        )
        assertEquals(
            ShellResultFeedback.TOAST,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.TOAST, exitCode = 2),
        )
    }

    @Test
    fun `none stays silent on success`() {
        assertEquals(
            ShellResultFeedback.SILENT,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.NONE, exitCode = 0),
        )
    }

    @Test
    fun `none still reports failures`() {
        assertEquals(
            ShellResultFeedback.TOAST,
            ShellResultFeedbackPolicy.forResult(ShellResultDisplay.NONE, exitCode = 1),
        )
    }
}
