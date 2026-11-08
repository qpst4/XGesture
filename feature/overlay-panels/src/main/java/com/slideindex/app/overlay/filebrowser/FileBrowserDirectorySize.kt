package com.slideindex.app.overlay.filebrowser

import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 当前目录的递归大小（**手动触发**，不自动跑）。
 *
 * - Root：单独起一个 `su -c 'nsenter ... du'` 进程，**不占用** RootShellSession 的交互 shell，
 *   避免 `du` 把列目录/Root 探测锁死；
 * - Shizuku：走 UserService 里的 `du`；
 * - 普通可读路径：Java 递归遍历。
 *
 * 这是尽力而为：大目录/超时/没有 du 都返回 null，UI 保留“本层文件大小”而不是卡住。
 */
object FileBrowserDirectorySize {
    private const val ROOT_TIMEOUT_SECONDS = 5L
    private const val SHIZUKU_TIMEOUT_MS = 8_000L
    private const val DIRECT_WALK_LIMIT = 50_000

    suspend fun compute(path: String, access: FileBrowserAccessInfo): Long? =
        withContext(Dispatchers.IO) {
            // 根、/proc、/sys 递归统计没有意义且会卡很久；直接放弃，让 UI 保留本层大小。
            if (path == "/" ||
                path.startsWith("/proc") ||
                path.startsWith("/sys") ||
                path.startsWith("/dev")
            ) {
                return@withContext null
            }
            when (access.mode) {
                AccessMode.ROOT -> shellSize(path, useRoot = true)
                AccessMode.SHIZUKU -> shellSize(path, useRoot = false)
                AccessMode.ALL_FILES, AccessMode.LIMITED -> directSize(path)
            }
        }

    private suspend fun shellSize(path: String, useRoot: Boolean): Long? {
        val command = "du -sk ${TaskManagerShellExecutor.shellQuote(path)} 2>/dev/null"
        if (!useRoot) {
            val result = FileBrowserHostCalls.runShellCommandLine(
                command = command,
                useRoot = false,
                timeoutMs = SHIZUKU_TIMEOUT_MS,
            )
            return parseDuKb(result.output)
        }
        return withContext(Dispatchers.IO) {
            val sh = TaskManagerShellExecutor.resolveShPath()
            val inner = if (RootNamespace.nsenterBinaryExists()) {
                "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                    TaskManagerShellExecutor.shellQuote(command)
            } else {
                command
            }
            val process = runCatching {
                ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", inner)
                    .redirectErrorStream(true)
                    .directory(File("/"))
                    .start()
            }.getOrNull() ?: return@withContext null
            try {
                if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext null
                }
                val output = runCatching {
                    process.inputStream.bufferedReader().use { it.readText() }
                }.getOrDefault("")
                parseDuKb(output)
            } finally {
                runCatching { process.destroy() }
            }
        }
    }

    private suspend fun directSize(path: String): Long? {
        val root = File(path)
        if (!root.isDirectory) return null
        var total = 0L
        var visited = 0
        return runCatching {
            root.walkTopDown().forEach { file ->
                coroutineContext.ensureActive()
                visited++
                if (visited > DIRECT_WALK_LIMIT) return null
                if (file.isFile) total += file.length()
            }
            total
        }.getOrNull()
    }

    /** `du -sk` 输出："12345\t/path" → 12345 * 1024。 */
    fun parseDuKb(output: String): Long? {
        val line = output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val kb = line.trim().substringBefore(' ').substringBefore('\t').toLongOrNull() ?: return null
        return kb * 1024L
    }
}
