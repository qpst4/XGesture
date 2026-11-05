package com.slideindex.app.util

import android.content.Context
import com.slideindex.app.R

object ShellResultFormatter {
    private const val MAX_TOAST_CHARS = 300

    fun toast(context: Context, exitCode: Int, output: String): String {
        val text = output.trim()
        return when {
            text.isNotEmpty() && exitCode == 0 -> text.take(MAX_TOAST_CHARS)
            text.isNotEmpty() -> "exit $exitCode\n${text.take(MAX_TOAST_CHARS)}"
            exitCode == 0 -> context.getString(R.string.shell_panel_result_exit_ok)
            else -> context.getString(R.string.shell_panel_result_exit_only, exitCode)
        }
    }
}
