package com.slideindex.app.shell

/**
 * 按命令文本回查命令面板里的条目，供「动作只存了命令文本」的入口取这条命令的显示方式。
 *
 * 为什么不用 id：快速启动器 / 圆环 / 快捷轮盘 / 悬浮球 / 应用内直达里的
 * `GestureAction.ExecuteShellCommand` 只保存命令文本（payload 格式是既定存储协议，
 * 不能为了显示方式去改它），所以和 [ShellCommandLabelResolver] 一样，执行时按文本回查。
 *
 * 注意：回查只解决「设置从哪儿读」，不改变执行方式——
 * 动作的执行方式仍跟随全局权限模式（见 app 模块的 `ShellCommandModeResolver.currentDefault()`）。
 */
object ShellCommandDisplayResolver {

    /**
     * 命中时返回来源命令；同文本有多条（用户存了重复命令）或没有命中时返回 null，
     * 由调用方回落到自己的默认行为（当前是「成功不提示、失败提示」）。
     *
     * [preferredId] 给「应用内直达」这类带来源 id 的入口用：
     * 先按 id + 文本精确命中，命不中再退回唯一的文本匹配。
     */
    fun resolve(
        commandLine: String,
        savedCommands: List<ShellCommand>,
        preferredId: String? = null,
    ): ShellCommand? {
        val text = commandLine.trim()
        if (text.isEmpty()) return null
        if (!preferredId.isNullOrBlank()) {
            savedCommands
                .firstOrNull { it.id == preferredId && it.command.trim() == text }
                ?.let { return it }
        }
        return savedCommands.filter { it.command.trim() == text }.singleOrNull()
    }
}
