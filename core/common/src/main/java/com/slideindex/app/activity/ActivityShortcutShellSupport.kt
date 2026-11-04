package com.slideindex.app.activity

import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconType

object ActivityShortcutShellSupport {
    const val HOST_PACKAGE = "com.slideindex.app.shell"
    private const val URI_PREFIX = "cebianshell:"

    fun fromShellCommand(cmd: ShellCommand): ActivityShortcut {
        val command = cmd.command.trim()
        return ActivityShortcut(
            label = cmd.label.ifBlank { command },
            packageName = HOST_PACKAGE,
            shortcutId = cmd.id,
            intentUris = listOf(encodeShellUri(command)),
            iconPath = cmd.iconPath,
        )
    }

    fun isShellShortcut(shortcut: ActivityShortcut): Boolean =
        shortcut.intentUris.any { isShellUri(it) }

    fun isShellUri(uri: String): Boolean = uri.startsWith(URI_PREFIX)

    fun decodeCommand(uri: String): String =
        if (!isShellUri(uri)) "" else uri.removePrefix(URI_PREFIX)

    fun shellCommandFrom(shortcut: ActivityShortcut): ShellCommand? {
        val uri = shortcut.intentUris.firstOrNull { isShellUri(it) } ?: return null
        val command = decodeCommand(uri).trim()
        if (command.isBlank()) return null
        return ShellCommand(
            id = shortcut.shortcutId,
            label = shortcut.label,
            command = command,
            iconType = inferShellIconType(shortcut.iconPath),
            iconPath = shortcut.iconPath,
        )
    }

    private fun inferShellIconType(iconPath: String?): ShellCommandIconType {
        if (iconPath.isNullOrBlank()) return ShellCommandIconType.OTHER
        if (iconPath.startsWith("shell_icons/") || iconPath.startsWith("shortcut_icons/")) {
            return ShellCommandIconType.URI
        }
        return ShellCommandIconType.OTHER
    }

    /** 把命令编码成条目内的 intent URI；编辑页只暴露命令本身，不暴露这个前缀。 */
    fun encodeShellUri(command: String): String = URI_PREFIX + command
}
