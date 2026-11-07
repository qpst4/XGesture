package com.slideindex.app.overlay.filebrowser

import java.util.Locale

sealed interface FileShellResult {
    data class Success(
        val entries: List<FileBrowserEntry>,
        val truncated: Boolean,
    ) : FileShellResult

    data class Failure(val error: FileBrowserError) : FileShellResult
}

/**
 * 特权 shell 列目录的**纯逻辑**部分：命令拼装 + 输出解析。
 *
 * 为什么不解析 `ls -l`：字段（日期/权限/链接）格式随 toybox 版本漂移；`stat -c` 能直接
 * 输出固定格式，且一次进程就能处理整个目录。文件名含 `|` 用 `limit = 4` 保留；
 * 文件名含换行是已知限制（shell 输出按行传输）。
 */
object FileBrowserShell {
    const val MAX_ENTRIES = 2000

    const val MARKER_NOT_FOUND = "XG_NOT_FOUND"
    const val MARKER_NOT_DIR = "XG_NOT_DIR"
    const val MARKER_CD_DENIED = "XG_CD_DENIED"
    const val MARKER_LIST_DENIED = "XG_LIST_DENIED"
    private const val MARKER_COUNT_PREFIX = "XG_COUNT:"
    private const val LINE_SEPARATOR = '|'

    fun buildListingCommand(
        path: String,
        internalRoot: String = FileBrowserPaths.DEFAULT_INTERNAL_ROOT,
    ): String {
        val normalized = FileBrowserPaths.normalize(path, internalRoot)
        val quoted = shellQuote(normalized)
        return buildString {
            appendLine(
                "if [ ! -e $quoted ] && [ ! -L $quoted ]; then echo $MARKER_NOT_FOUND; exit 0; fi",
            )
            appendLine("if [ ! -d $quoted ]; then echo $MARKER_NOT_DIR; exit 0; fi")
            appendLine("cd $quoted 2>/dev/null || { echo $MARKER_CD_DENIED; exit 0; }")
            appendLine("ls -1A >/dev/null 2>&1 || { echo $MARKER_LIST_DENIED; exit 0; }")
            appendLine("n=\$(ls -1A 2>/dev/null | wc -l)")
            appendLine("echo \"$MARKER_COUNT_PREFIX\$n\"")
            appendLine(
                "stat -c '%A|%s|%Y|%n' ./.[!.]* ./..?* ./* 2>/dev/null | " +
                    "head -n ${MAX_ENTRIES + 1}",
            )
        }.trimEnd()
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun parseListing(
        output: String,
        parentPath: String,
        internalRoot: String = FileBrowserPaths.DEFAULT_INTERNAL_ROOT,
    ): FileShellResult {
        var totalCount = -1
        var failure: FileBrowserError? = null
        var sawPermissionError = false
        var sawNotFoundError = false
        var sawDirectoryError = false
        var sawTimeout = false
        var sawNoOutputSentinel = false
        val entries = ArrayList<FileBrowserEntry>()

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            when {
                line.isEmpty() -> Unit
                line == MARKER_NOT_FOUND -> {
                    failure = FileBrowserError.NotFound
                    break
                }
                line == MARKER_NOT_DIR -> {
                    failure = FileBrowserError.NotDirectory
                    break
                }
                line == MARKER_CD_DENIED || line == MARKER_LIST_DENIED -> {
                    failure = FileBrowserError.PermissionDenied
                    break
                }
                line.startsWith(MARKER_COUNT_PREFIX) -> {
                    totalCount = line.removePrefix(MARKER_COUNT_PREFIX).trim().toIntOrNull() ?: -1
                }
                else -> {
                    val entry = parseEntry(line, parentPath, internalRoot)
                    if (entry != null) {
                        entries += entry
                    } else {
                        val lower = line.lowercase(Locale.ROOT)
                        if (lower.contains("permission denied")) sawPermissionError = true
                        if (lower.contains("no such file")) sawNotFoundError = true
                        if (lower.contains("not a directory")) sawDirectoryError = true
                        if (lower.contains("timed out") ||
                            lower.contains("timeout") ||
                            lower.contains("session dead")
                        ) {
                            sawTimeout = true
                        }
                        if (line.contains("(no output")) sawNoOutputSentinel = true
                    }
                }
            }
        }

        failure?.let { return FileShellResult.Failure(it) }

        if (entries.isEmpty()) {
            return when {
                sawPermissionError -> FileShellResult.Failure(FileBrowserError.PermissionDenied)
                sawDirectoryError -> FileShellResult.Failure(FileBrowserError.NotDirectory)
                sawNotFoundError -> FileShellResult.Failure(FileBrowserError.NotFound)
                sawTimeout -> FileShellResult.Failure(FileBrowserError.Timeout)
                sawNoOutputSentinel -> FileShellResult.Failure(FileBrowserError.Unknown(null))
                totalCount > 0 -> FileShellResult.Failure(FileBrowserError.Unknown(null))
                totalCount == 0 -> FileShellResult.Success(emptyList(), truncated = false)
                else -> FileShellResult.Failure(FileBrowserError.Unknown(null))
            }
        }

        val truncated = totalCount > MAX_ENTRIES || entries.size > MAX_ENTRIES
        return FileShellResult.Success(
            entries = entries.take(MAX_ENTRIES),
            truncated = truncated,
        )
    }

    private fun parseEntry(
        line: String,
        parentPath: String,
        internalRoot: String,
    ): FileBrowserEntry? {
        val parts = line.split(LINE_SEPARATOR, limit = 4)
        if (parts.size != 4) return null
        val mode = parts[0]
        val size = parts[1].toLongOrNull() ?: return null
        val modified = parts[2].toLongOrNull() ?: return null
        val name = parts[3].removePrefix("./")
        if (name.isEmpty() || name == "." || name == "..") return null
        val kind = when (mode.firstOrNull()) {
            'd' -> FileEntryKind.DIRECTORY
            'l' -> FileEntryKind.SYMLINK
            '-' -> FileEntryKind.FILE
            'c', 'b', 'p', 's' -> FileEntryKind.OTHER
            else -> return null
        }
        return FileBrowserEntry(
            name = name,
            path = FileBrowserPaths.child(parentPath, name, internalRoot),
            kind = kind,
            sizeBytes = size,
            modifiedEpochSeconds = modified,
            permissions = mode,
        )
    }
}
