package com.slideindex.app.overlay.filebrowser

import android.content.Context
import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root 文件导出：把受保护路径复制到 App cache，再交给 FileProvider 打开/分享。
 *
 * 只走 root 一次性 `su`，不占用 RootShellSession；Shizuku 模式下没有 root 写权限，直接失败。
 */
object FileBrowserExport {
    private const val TIMEOUT_SECONDS = 12L

    suspend fun exportToCache(context: Context, sourcePath: String): File? =
        withContext(Dispatchers.IO) {
            val source = File(sourcePath)
            if (!source.isFile) return@withContext null
            val dir = File(context.cacheDir, "filebrowser").apply { mkdirs() }
            val name = sourcePath.substringAfterLast('/').ifBlank { "exported_file" }
            val destination = File(dir, name)
            runCatching { destination.delete() }

            val sh = TaskManagerShellExecutor.resolveShPath()
            val command = "cp -f ${TaskManagerShellExecutor.shellQuote(sourcePath)} " +
                TaskManagerShellExecutor.shellQuote(destination.absolutePath)
            val launcher = if (RootNamespace.nsenterBinaryExists()) {
                "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                    TaskManagerShellExecutor.shellQuote(command)
            } else {
                command
            }
            val process = runCatching {
                ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", launcher)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return@withContext null
            try {
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext null
                }
                destination.takeIf { it.isFile && it.length() > 0L }
            } finally {
                runCatching { process.destroy() }
            }
        }
}
