package com.slideindex.app.shell

/**
 * Shell 命令的自定义名称解析。
 *
 * [com.slideindex.app.gesture.GestureAction.ExecuteShellCommand] 只保存命令文本（没有 id），
 * 所以这里按 `trim()` 后的命令文本精确匹配 Shell 命令面板里的条目；命中且名称非空时
 * 返回用户自定义的名字，否则返回 null，由调用方回退到自己的默认文案（如命令预览）。
 *
 * 快速启动器、边角轮盘、快捷轮盘、动作选择列表都走这一处，避免各自写一份匹配逻辑。
 */
object ShellCommandLabelResolver {

    /** 命令文本 → 自定义名称；找不到或名称为空时返回 null。 */
    fun customLabelFor(
        commandLine: String,
        shellCommands: List<ShellCommand>,
    ): String? {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return null
        return shellCommands
            .firstOrNull { it.command.trim() == trimmed }
            ?.label
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
