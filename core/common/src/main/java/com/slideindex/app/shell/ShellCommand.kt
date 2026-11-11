package com.slideindex.app.shell

import java.util.UUID

data class ShellCommand(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val iconType: ShellCommandIconType = ShellCommandIconType.OTHER,
    /** Relative path under app files dir, e.g. shell_icons/custom-xxx.png */
    val iconPath: String? = null,
    val textIcon: String? = null,
    /** null only for legacy commands saved before per-command modes existed. */
    val executionMode: ShellExecutionMode? = ShellExecutionMode.SHIZUKU,
    val resultDisplay: ShellResultDisplay = ShellResultDisplay.DIALOG,
) {
    fun hasCustomIcon(): Boolean = when (iconType) {
        ShellCommandIconType.URI -> !iconPath.isNullOrBlank()
        ShellCommandIconType.TEXT -> !textIcon.isNullOrBlank()
        ShellCommandIconType.OTHER -> false
    }
}

object ShellCommandCodec {
    private const val SEP = "\u001E"
    private const val LIST_SEP = "\u001F"
    private const val SYSTEM_SH = "/system/bin/sh"
    private const val SYSTEM_SU = "/system/bin/su"

    fun encode(item: ShellCommand): String =
        listOf(
            item.id,
            item.label,
            item.command,
            item.iconType.name,
            item.iconPath.orEmpty(),
            item.textIcon.orEmpty(),
            item.executionMode?.storageValue.orEmpty(),
            item.resultDisplay.storageValue,
        ).joinToString(SEP)

    fun decode(raw: String): ShellCommand? {
        val parts = raw.split(SEP)
        if (parts.size < 3) return null
        val id = parts[0]
        val label = parts[1]
        val command = parts[2]
        if (label.isBlank() || command.isBlank()) return null
        val iconType = if (parts.size >= 4) {
            ShellCommandIconType.fromStored(parts[3])
        } else {
            ShellCommandIconType.OTHER
        }
        val iconPath = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val textIcon = parts.getOrNull(5)?.takeIf { it.isNotBlank() }
        // 旧版本没有 executionMode 字段。null 表示「没有显式设置」，
        // 执行时由 ShellCommandModeResolver 解析为跟随全局权限模式；这里不要写死。
        val executionMode = ShellExecutionMode.fromStorage(parts.getOrNull(6))
        val resultDisplay = ShellResultDisplay.fromStorage(parts.getOrNull(7))
        return ShellCommand(
            id = id,
            label = label,
            command = command,
            iconType = iconType,
            iconPath = iconPath,
            textIcon = textIcon,
            executionMode = executionMode,
            resultDisplay = resultDisplay,
        )
    }

    fun encodeAll(items: List<ShellCommand>): Set<String> =
        if (items.isEmpty()) {
            emptySet()
        } else {
            setOf(items.joinToString(LIST_SEP) { encode(it) })
        }

    fun decodeAll(raw: Set<String>): List<ShellCommand> {
        if (raw.isEmpty()) return emptyList()
        val decoded = if (raw.size == 1) {
            val only = raw.first()
            if (LIST_SEP in only) {
                only.split(LIST_SEP).mapNotNull { decode(it) }
            } else {
                listOfNotNull(decode(only))
            }
        } else {
            raw.mapNotNull { decode(it) }
        }
        return decoded
    }

    fun buildExecArgs(commandLine: String, useRoot: Boolean): Array<String> {
        val trimmed = commandLine.trim()
        require(trimmed.isNotEmpty()) { "Empty command" }
        return if (useRoot) {
            arrayOf(SYSTEM_SH, "-c", "${resolveSuInvocation()} -c ${shellQuote(trimmed)}")
        } else {
            arrayOf(SYSTEM_SH, "-c", trimmed)
        }
    }

    private fun resolveSuInvocation(): String {
        val candidates = listOf(
            "/sbin/su",
            "/system/xbin/su",
            SYSTEM_SU,
            "/vendor/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/magisk",
        )
        return candidates.firstOrNull { java.io.File(it).exists() } ?: "su"
    }

    private fun shellQuote(command: String): String =
        "'" + command.replace("'", "'\\''") + "'"
}
