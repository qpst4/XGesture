package com.slideindex.app.overlay.filebrowser

import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 当前目录下的递归搜索（最多 200 条）。
 *
 * 可直读路径走 Java walk；Root/Shizuku 走 shell find。结果只带名称/路径/类型，
 * 大小与时间留给详情页按需读取，避免搜索时对每个结果再 stat。
 */
object FileBrowserSearch {
    private const val LIMIT = 200
    private const val MAX_DEPTH = 6
    private const val SHELL_TIMEOUT_MS = 8_000L

    suspend fun search(
        basePath: String,
        query: String,
        access: FileBrowserAccessInfo,
    ): List<FileBrowserEntry> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        val direct = searchDirect(basePath, needle)
        if (direct != null) return@withContext direct
        if (!access.privileged) return@withContext emptyList()
        searchShell(basePath, needle, access)
    }

    private fun searchDirect(basePath: String, needle: String): List<FileBrowserEntry>? {
        val root = File(basePath)
        if (!root.isDirectory || !root.canRead()) return null
        val result = ArrayList<FileBrowserEntry>(LIMIT)
        runCatching {
            root.walkTopDown()
                .maxDepth(MAX_DEPTH)
                .onEnter { dir -> dir == root || !dir.name.startsWith(".") }
                .forEach { file ->
                    if (file != root && file.name.contains(needle, ignoreCase = true)) {
                        result += FileBrowserEntry(
                            name = file.name,
                            path = file.absolutePath,
                            kind = if (file.isDirectory) {
                                FileEntryKind.DIRECTORY
                            } else {
                                FileEntryKind.FILE
                            },
                            sizeBytes = if (file.isFile) file.length() else 0L,
                            modifiedEpochSeconds = file.lastModified() / 1000L,
                        )
                    }
                    if (result.size >= LIMIT) return@forEach
                }
        }
        return result
    }

    private fun searchShell(
        basePath: String,
        needle: String,
        access: FileBrowserAccessInfo,
    ): List<FileBrowserEntry> {
        val quotedBase = TaskManagerShellExecutor.shellQuote(basePath)
        val quotedPattern = TaskManagerShellExecutor.shellQuote("*$needle*")
        val command = buildString {
            append("find ").append(quotedBase)
            append(" -maxdepth ").append(MAX_DEPTH)
            append(" -iname ").append(quotedPattern)
            append(" 2>/dev/null | head -n ").append(LIMIT)
            append(" | while IFS= read -r f; do ")
            append("if [ -d \"\$f\" ]; then t=d; else t=f; fi; ")
            append("printf '%s|%s\\n' \"\$t\" \"\$f\"; done")
        }
        val result = FileBrowserHostCalls.runShellCommandLine(
            command = command,
            useRoot = access.mode == AccessMode.ROOT,
            timeoutMs = SHELL_TIMEOUT_MS,
        )
        if (result.exitCode != 0 || result.output.isBlank()) return emptyList()
        return result.output.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('|')
                if (separator <= 0) return@mapNotNull null
                val kind = line.substring(0, separator)
                val path = line.substring(separator + 1)
                if (path.isBlank()) return@mapNotNull null
                FileBrowserEntry(
                    name = path.trimEnd('/').substringAfterLast('/'),
                    path = path,
                    kind = if (kind == "d") FileEntryKind.DIRECTORY else FileEntryKind.FILE,
                    sizeBytes = 0L,
                    modifiedEpochSeconds = 0L,
                )
            }
            .take(LIMIT)
            .toList()
    }
}
