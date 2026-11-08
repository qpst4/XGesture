package com.slideindex.app.overlay.filebrowser

import android.content.Context
import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrashedItem(val originalPath: String, val trashPath: String)

data class TrashReport(
    val trashed: List<TrashedItem>,
    val failures: List<FileOperationFailure>,
)

/**
 * 删除到 App cache 的回收站，并支持撤销。
 *
 * 只保留最近一批；cache 被系统清理后无法恢复。跨卷移动先复制再删源。
 */
object FileBrowserTrash {
    private const val TIMEOUT_SECONDS = 60L

    suspend fun trash(
        context: Context,
        paths: List<String>,
        access: FileBrowserAccessInfo,
    ): TrashReport = withContext(Dispatchers.IO) {
        val batch = File(context.cacheDir, "trash/${System.currentTimeMillis()}")
        if (!batch.mkdirs() && !batch.isDirectory) {
            return@withContext TrashReport(
                emptyList(),
                paths.map { FileOperationFailure(it, "无法创建回收站") }, // i18n-ignore
            )
        }
        val trashed = mutableListOf<TrashedItem>()
        val failures = mutableListOf<FileOperationFailure>()
        for (path in paths) {
            val source = File(path)
            if (!source.exists()) {
                failures += FileOperationFailure(path, "源文件不存在") // i18n-ignore
                continue
            }
            val destination = FileBrowserOperations.uniqueDestination(batch, source.name)
            if (move(source, destination, access)) {
                trashed += TrashedItem(source.absolutePath, destination.absolutePath)
            } else {
                failures += FileOperationFailure(path, "删除失败") // i18n-ignore
            }
        }
        TrashReport(trashed, failures)
    }

    suspend fun restore(
        items: List<TrashedItem>,
        access: FileBrowserAccessInfo,
    ): List<FileOperationFailure> = withContext(Dispatchers.IO) {
        val failures = mutableListOf<FileOperationFailure>()
        for (item in items) {
            val source = File(item.trashPath)
            val destination = File(item.originalPath)
            if (!source.exists()) {
                failures += FileOperationFailure(item.originalPath, "回收站文件不存在") // i18n-ignore
                continue
            }
            if (destination.exists()) {
                failures += FileOperationFailure(item.originalPath, "原位置已有同名文件") // i18n-ignore
                continue
            }
            if (!move(source, destination, access)) {
                failures += FileOperationFailure(item.originalPath, "恢复失败") // i18n-ignore
            }
        }
        failures
    }

    private fun move(source: File, destination: File, access: FileBrowserAccessInfo): Boolean {
        if (source.renameTo(destination)) return true
        if (access.mode != AccessMode.ROOT) {
            // 共享存储跨卷：尽量 Java 复制后删除。
            return copyDirect(source, destination) && source.deleteRecursively()
        }
        val src = TaskManagerShellExecutor.shellQuote(source.absolutePath)
        val dst = TaskManagerShellExecutor.shellQuote(destination.absolutePath)
        if (runRoot("mv -f $src $dst") && destination.exists()) return true
        return runRoot("cp -a $src $dst") && destination.exists() &&
            runRoot("rm -rf $src") && !source.exists()
    }

    private fun copyDirect(source: File, destination: File): Boolean = runCatching {
        if (source.isDirectory) {
            if (!destination.mkdirs()) return false
            source.listFiles().orEmpty().all { copyDirect(it, File(destination, it.name)) }
        } else {
            source.copyTo(destination, overwrite = false)
            true
        }
    }.getOrDefault(false)

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
