package com.slideindex.app.overlay.filebrowser

import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class FileTransferMode { COPY, MOVE }

enum class FileConflictPolicy { RENAME, OVERWRITE, SKIP }

data class FileOperationFailure(val path: String, val reason: String)

data class FileOperationReport(
    val succeeded: Int,
    val failures: List<FileOperationFailure>,
    val skipped: Int = 0,
) {
    val total: Int get() = succeeded + skipped + failures.size
}

/**
 * 批量移动/复制/删除。
 *
 * 优先 Java File（可直写路径）；Root 模式退回一次性 su + nsenter。跨卷移动按
 * “复制到最终目标 → 校验 → 删除源”处理，失败时保留源。
 */
object FileBrowserOperations {
    private const val ROOT_TIMEOUT_SECONDS = 60L

    suspend fun transfer(
        paths: List<String>,
        targetDir: String,
        mode: FileTransferMode,
        access: FileBrowserAccessInfo,
        policy: FileConflictPolicy = FileConflictPolicy.RENAME,
        onProgress: (done: Int, total: Int) -> Unit,
    ): FileOperationReport = withContext(Dispatchers.IO) {
        val target = File(targetDir)
        if (!target.isDirectory) {
            return@withContext FileOperationReport(
                0,
                paths.map { FileOperationFailure(it, "目标目录不存在") }, // i18n-ignore
            )
        }
        var done = 0
        var succeeded = 0
        var skipped = 0
        val failures = mutableListOf<FileOperationFailure>()
        for (path in paths) {
            coroutineContext.ensureActive()
            onProgress(done, paths.size)
            val source = File(path)
            val failure = when {
                !source.exists() -> "源文件不存在" // i18n-ignore
                mode == FileTransferMode.MOVE &&
                    isInside(source.absolutePath, target.absolutePath) ->
                    "不能移动到自身或子目录" // i18n-ignore
                !canWriteTarget(target, access) -> "目标目录不可写" // i18n-ignore
                else -> null
            }
            if (failure != null) {
                failures += FileOperationFailure(path, failure)
                done++
                continue
            }
            val directDestination = File(target, source.name)
            val destination = when {
                !directDestination.exists() -> directDestination
                policy == FileConflictPolicy.SKIP -> {
                    skipped++
                    done++
                    continue
                }
                policy == FileConflictPolicy.OVERWRITE -> {
                    if (!removeDestination(directDestination, access)) {
                        failures += FileOperationFailure(path, "无法覆盖目标") // i18n-ignore
                        done++
                        continue
                    }
                    directDestination
                }
                else -> uniqueDestination(target, source.name)
            }
            val ok = transferOne(source, destination, mode, access)
            if (ok) succeeded++ else failures += FileOperationFailure(path, "操作失败") // i18n-ignore
            done++
        }
        onProgress(paths.size, paths.size)
        FileOperationReport(succeeded, failures, skipped)
    }

    /** 目标目录里已经存在的同名项数量。 */
    fun conflicts(paths: List<String>, targetDir: String): Int {
        val target = File(targetDir)
        return paths.count { File(target, File(it).name).exists() }
    }

    private fun removeDestination(destination: File, access: FileBrowserAccessInfo): Boolean {
        if (!destination.exists()) return true
        if (destination.canWrite() && destination.deleteRecursively()) return true
        if (access.mode != AccessMode.ROOT) return false
        return deleteRoot(destination.absolutePath)
    }

    suspend fun delete(
        paths: List<String>,
        access: FileBrowserAccessInfo,
        onProgress: (done: Int, total: Int) -> Unit,
    ): FileOperationReport = withContext(Dispatchers.IO) {
        var succeeded = 0
        val failures = mutableListOf<FileOperationFailure>()
        paths.forEachIndexed { index, path ->
            coroutineContext.ensureActive()
            onProgress(index, paths.size)
            val file = File(path)
            val ok = when {
                !file.exists() -> false
                file.canWrite() -> file.deleteRecursively()
                access.mode == AccessMode.ROOT -> deleteRoot(path)
                else -> false
            }
            if (ok) succeeded++ else failures += FileOperationFailure(path, "删除失败") // i18n-ignore
        }
        onProgress(paths.size, paths.size)
        FileOperationReport(succeeded, failures)
    }

    private fun transferOne(
        source: File,
        destination: File,
        mode: FileTransferMode,
        access: FileBrowserAccessInfo,
    ): Boolean {
        if (destination.exists()) return false
        if (source.canRead() && destination.parentFile?.canWrite() == true) {
            val direct = if (mode == FileTransferMode.MOVE) {
                source.renameTo(destination)
            } else {
                copyDirect(source, destination)
            }
            if (direct) return true
            runCatching { destination.deleteRecursively() }
        }
        if (access.mode != AccessMode.ROOT) return false
        val sourcePath = quote(source.absolutePath)
        val destinationPath = quote(destination.absolutePath)
        return when (mode) {
            FileTransferMode.COPY -> {
                runRoot("cp -a $sourcePath $destinationPath") && destination.exists()
            }
            FileTransferMode.MOVE -> {
                if (runRoot("mv -f $sourcePath $destinationPath") && destination.exists()) {
                    true
                } else {
                    // 跨文件系统 mv 可能失败：复制成功后再删源，失败保留源。
                    runRoot("cp -a $sourcePath $destinationPath") &&
                        destination.exists() &&
                        deleteRoot(source.absolutePath)
                }
            }
        }
    }

    private fun copyDirect(source: File, destination: File): Boolean = runCatching {
        if (source.isDirectory) {
            if (!destination.mkdirs()) return false
            source.listFiles().orEmpty().forEach { child ->
                if (!copyDirect(child, File(destination, child.name))) return false
            }
            destination.setLastModified(source.lastModified())
            true
        } else {
            source.copyTo(destination, overwrite = false)
            destination.setLastModified(source.lastModified())
            true
        }
    }.getOrDefault(false)

    private fun deleteRoot(path: String): Boolean {
        val quoted = quote(path)
        return runRoot("rm -rf $quoted") && !File(path).exists()
    }

    private fun canWriteTarget(target: File, access: FileBrowserAccessInfo): Boolean =
        target.canWrite() || access.mode == AccessMode.ROOT

    private fun runRoot(command: String): Boolean {
        val sh = TaskManagerShellExecutor.resolveShPath()
        val inner = if (RootNamespace.nsenterBinaryExists()) {
            "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c ${quote(command)}"
        } else {
            command
        }
        val process = runCatching {
            ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", inner)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return false
        return try {
            process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            runCatching { process.destroy() }
        }
    }

    /** 同名时生成 `name (1).ext`。 */
    fun uniqueDestination(targetDir: File, name: String): File {
        val first = File(targetDir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(targetDir, String.format(Locale.ROOT, "%s (%d)%s", base, index, ext))
            if (!candidate.exists()) return candidate
            index++
        }
    }

    /** child 是否等于 parent 或位于 parent 内。 */
    fun isInside(child: String, parent: String): Boolean {
        val normalizedChild = child.trimEnd('/')
        val normalizedParent = parent.trimEnd('/')
        return normalizedChild == normalizedParent ||
            normalizedChild.startsWith("$normalizedParent/")
    }

    private fun quote(value: String): String = TaskManagerShellExecutor.shellQuote(value)
}
