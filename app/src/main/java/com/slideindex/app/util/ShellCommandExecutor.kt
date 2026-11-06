package com.slideindex.app.util

import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandTemplate
import com.slideindex.app.shell.ShellTemplateContext

object ShellCommandExecutor {

    fun execute(
        command: ShellCommand,
        templateContext: ShellTemplateContext? = null,
    ): TaskManagerUtil.ShellCommandResult {
        val mode = command.executionMode
            ?: return TaskManagerUtil.ShellCommandResult(
                exitCode = -1,
                output = TaskManagerUtil.applicationContext()
                    .getString(R.string.shell_no_execution_mode),
            )
        val expanded = templateContext?.let {
            ShellCommandTemplate.expand(command.command, it)
        } ?: command.command
        return TaskManagerUtil.runShellCommandLineExplicit(
            command = expanded,
            mode = mode,
        )
    }

    fun probeRootAvailable(): Boolean = TaskManagerUtil.probeRootAvailable()

    fun invalidateRootCache() {
        // Strict per-command execution has no implicit root cache anymore.
    }
}
