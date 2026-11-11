package com.slideindex.app.util

import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandModeResolver
import com.slideindex.app.shell.ShellCommandTemplate
import com.slideindex.app.shell.ShellTemplateContext

object ShellCommandExecutor {

    fun execute(
        command: ShellCommand,
        templateContext: ShellTemplateContext? = null,
    ): TaskManagerUtil.ShellCommandResult {
        // 没有显式执行方式的命令（旧数据、动作现场填的文本）跟随全局权限模式，
        // 不再写死 Shizuku，也不再因为「未设置」直接拒绝执行。
        val mode = command.executionMode ?: ShellCommandModeResolver.currentDefault()
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
