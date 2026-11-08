package com.slideindex.app.overlay.filebrowser

import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件写操作：新建目录、重命名、删除。
 *
 * 优先使用 Java File（“所有文件访问”/App 可写目录）；Root 模式下退回一次性 su + nsenter。
 * Shizuku 只有 shell 身份，写不了 /data；共享存储则仍可能通过 Java File 直写。
 */
object FileBrowserWrite {
    private const val TIMEOUT_SECONDS = 12L

    suspend fun createDirectory(
        contextPath: String,
        name: String,
        access: FileBrowserAccessInfo,
    ): Boolean = withContext(Dispatchers.IO) {
        val dirName = name.trim()
        if (!isValidName(dirName)) return@withContext false
        val target = File(contextPath, dirName)
        if (target.exists()) return@withContext false
        if (target.parentFile?.canWrite() == true && target.mkdirs()) return@withContext true
        if (access.mode != AccessMode.ROOT) return@withContext false
        runRoot("mkdir -p ${TaskManagerShellExecutor.shellQuote(target.absolutePath)}") &&
            verifyExists(target.absolutePath)
    }

    suspend fun rename(
        path: String,
        newName: String,
        access: FileBrowserAccessInfo,
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (!isValidName(trimmed)) return@withContext false
        val source = File(path)
        val parent = source.parent ?: return@withContext false
        val destination = File(parent, trimmed)
        if (destination.exists()) return@withContext false
        if (source.canWrite() && source.renameTo(destination)) return@withContext true
        if (access.mode != AccessMode.ROOT) return@withContext false
        runRoot(
            "mv ${TaskManagerShellExecutor.shellQuote(source.absolutePath)} " +
                TaskManagerShellExecutor.shellQuote(destination.absolutePath),
        )
        verifyExists(destination.absolutePath)    }

    suspend fun delete(
        path: String,
        access: FileBrowserAccessInfo,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = File(path)
        if (!target.exists()) return@withContext false
        if (target.canWrite() && target.deleteRecursively()) return@withContext true
        if (access.mode != AccessMode.ROOT) return@withContext false
        runRoot("rm -rf ${TaskManagerShellExecutor.shellQuote(path)}")
        !File(path).exists()
    }

    private fun isValidName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && name != "." && name != ".."

    private fun verifyExists(path: String): Boolean = File(path).exists()

    private fun runRoot(command: String): Boolean {
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
                .start()
        }.getOrNull() ?: return false
        return try {
            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            runCatching { process.destroy() }
        }
    }
}
