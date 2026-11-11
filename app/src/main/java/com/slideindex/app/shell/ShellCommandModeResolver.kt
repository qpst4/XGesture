package com.slideindex.app.shell

import com.slideindex.app.privilege.PrivilegeGateway

/**
 * 命令没有显式执行方式时的解析规则。
 *
 * 只有一条：显式设置优先；没有显式设置就跟随全局权限模式。
 * 不探测 root、不做隐式提权——探测会弹授权框、抢焦点，对「判断前台再点击」的脚本是干扰。
 */
object ShellCommandModeResolver {

    /** 全局权限模式对应的默认执行方式。 */
    fun currentDefault(): ShellExecutionMode =
        if (PrivilegeGateway.isRootMode()) ShellExecutionMode.ROOT else ShellExecutionMode.SHIZUKU

    /**
     * 应用内直达 / 快捷方式里的 shell 条目只存了命令文本。
     * 能按 id + 文本命中命令面板里的原命令时沿用它的执行方式；
     * 命不中就跟随全局默认，避免这些入口悄悄按另一种身份执行。
     */
    fun forShortcut(
        shortcutId: String?,
        command: String,
        savedCommands: List<ShellCommand>,
        defaultMode: ShellExecutionMode,
    ): ShellExecutionMode {
        val text = command.trim()
        if (text.isEmpty()) return defaultMode
        savedCommands.firstOrNull { it.id == shortcutId && it.command.trim() == text }
            ?.let { return it.executionMode ?: defaultMode }
        val matches = savedCommands.filter { it.command.trim() == text }
        if (matches.size == 1) return matches.single().executionMode ?: defaultMode
        return defaultMode
    }
}
