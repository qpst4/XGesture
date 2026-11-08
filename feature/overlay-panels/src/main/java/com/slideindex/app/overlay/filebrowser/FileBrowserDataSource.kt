package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.os.Environment
import com.slideindex.app.util.TaskManagerUtil
import java.io.File
import java.nio.file.Files

enum class FileListingSource {
    DIRECT,
    SHELL,
}

sealed interface FileBrowserListingResult {
    data class Success(
        val entries: List<FileBrowserEntry>,
        val source: FileListingSource,
        val limited: Boolean,
        val truncated: Boolean,
    ) : FileBrowserListingResult

    data class Failure(val error: FileBrowserError) : FileBrowserListingResult
}

/**
 * 文件浏览器的唯一数据源：优先直读，必要时走 Root / Shizuku shell。
 *
 * 两条路径的取舍见 [preferShell]：`/`、`/data`、`Android/data|obb` 以及“没有所有文件访问”
 * 的外部存储必须先尝试特权 shell，否则直读会返回**不完整**列表（FUSE 只放行应用自己的文件）。
 * 其余路径直读更快，失败再回退 shell。
 */
internal class FileBrowserDataSource(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    @Suppress("DEPRECATION")
    val internalRoot: String = Environment.getExternalStorageDirectory()
        ?.absolutePath
        ?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }
        ?: FileBrowserPaths.DEFAULT_INTERNAL_ROOT

    fun initialPath(): String =
        if (File(internalRoot).isDirectory) internalRoot else "/"

    fun resolveAccess(): FileBrowserAccessInfo = FileBrowserAccess.resolve()

    fun list(
        path: String,
        access: FileBrowserAccessInfo,
    ): FileBrowserListingResult {
        val normalized = FileBrowserPaths.normalize(path, internalRoot)
        val limitedForDirect =
            FileBrowserPaths.isExternal(normalized, internalRoot) && !access.allFiles
        val shellFirst = preferShell(normalized, access.allFiles)

        if (!shellFirst) {
            when (val direct = listDirect(normalized, limitedForDirect)) {
                null -> Unit
                is FileBrowserListingResult.Success -> return direct
                is FileBrowserListingResult.Failure -> return direct
            }
        }

        if (access.privileged) {
            val shell = listViaShell(normalized, access)
            if (shell is FileBrowserListingResult.Success) return shell
            val direct = listDirect(normalized, limitedForDirect)
            if (direct is FileBrowserListingResult.Success) {
                val suspiciousEmpty = direct.entries.isEmpty() &&
                    (normalized == "/" ||
                        FileBrowserPaths.isRootOnly(normalized) ||
                        FileBrowserPaths.isAndroidDataOrObb(normalized, internalRoot))
                if (!suspiciousEmpty) return direct.copy(limited = true)
            }
            return shell
        }

        // 没有特权：shell-first 的路径退回直读做“尽力而为”，并标记 limited。
        val direct = if (shellFirst) listDirect(normalized, limited = true) else null
        if (direct is FileBrowserListingResult.Success) {
            // Android 对 Android/data、/、/data 的 listFiles() 可能返回空数组而不是 null；
            // 没有特权时不能把“空”当成“目录为空”，否则会给用户一个假目录。
            val suspiciousEmpty = direct.entries.isEmpty() &&
                (normalized == "/" ||
                    FileBrowserPaths.isRootOnly(normalized) ||
                    FileBrowserPaths.isAndroidDataOrObb(normalized, internalRoot))
            if (suspiciousEmpty) {
                return FileBrowserListingResult.Failure(noPrivilegeError(normalized))
            }
            return direct
        }
        return FileBrowserListingResult.Failure(noPrivilegeError(normalized))
    }

    private fun preferShell(path: String, allFiles: Boolean): Boolean =
        path == "/" ||
            FileBrowserPaths.isRootOnly(path) ||
            FileBrowserPaths.isAndroidDataOrObb(path, internalRoot) ||
            (FileBrowserPaths.isExternal(path, internalRoot) && !allFiles)

    /** 返回 null 表示“直读拿不到（权限/不存在）”，由调用方决定是否回退 shell。 */
    private fun listDirect(
        path: String,
        limited: Boolean,
    ): FileBrowserListingResult? {
        val dir = File(path)
        if (!dir.isDirectory) {
            return if (dir.exists()) {
                FileBrowserListingResult.Failure(FileBrowserError.NotDirectory)
            } else {
                null
            }
        }
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return null
        val truncated = files.size > FileBrowserShell.MAX_ENTRIES
        val selected: List<File> = if (truncated) {
            files.take(FileBrowserShell.MAX_ENTRIES)
        } else {
            files.toList()
        }
        val entries = selected.mapNotNull { file -> directEntry(path, file) }
        return FileBrowserListingResult.Success(
            entries = entries,
            source = FileListingSource.DIRECT,
            limited = limited,
            truncated = truncated,
        )
    }

    private fun directEntry(parentPath: String, file: File): FileBrowserEntry? {
        val name = file.name
        if (name.isEmpty()) return null
        val isSymlink = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)
        val kind = when {
            isSymlink -> FileEntryKind.SYMLINK
            file.isDirectory -> FileEntryKind.DIRECTORY
            file.isFile -> FileEntryKind.FILE
            else -> FileEntryKind.OTHER
        }
        val modifiedSeconds = (file.lastModified() / 1000L).coerceAtLeast(0L)
        return FileBrowserEntry(
            name = name,
            path = FileBrowserPaths.child(parentPath, name, internalRoot),
            kind = kind,
            sizeBytes = if (kind == FileEntryKind.DIRECTORY) 0L else file.length(),
            modifiedEpochSeconds = modifiedSeconds,
            permissions = null,
        )
    }

    private fun listViaShell(
        path: String,
        access: FileBrowserAccessInfo,
    ): FileBrowserListingResult {
        val command = FileBrowserShell.buildListingCommand(path, internalRoot)
        val result = runCatching {
            TaskManagerUtil.runShellCommandLine(
                command = command,
                useRoot = access.mode == AccessMode.ROOT,
                timeoutMs = SHELL_TIMEOUT_MS,
            )
        }.getOrElse { error ->
            return FileBrowserListingResult.Failure(
                FileBrowserError.Unknown(error.message),
            )
        }
        return when (val parsed = FileBrowserShell.parseListing(result.output, path, internalRoot)) {
            is FileShellResult.Success -> FileBrowserListingResult.Success(
                entries = parsed.entries,
                source = FileListingSource.SHELL,
                limited = false,
                truncated = parsed.truncated,
            )
            is FileShellResult.Failure -> FileBrowserListingResult.Failure(
                mapShellError(parsed.error, path, access),
            )
        }
    }

    /**
     * Shizuku 以 shell 身份运行，`/data`、`/root` 必然被拒；把“权限不足”翻译成
     * “需要 Root”，UI 才能给出可执行的提示，而不是让用户反复点重试。
     */
    private fun mapShellError(
        error: FileBrowserError,
        path: String,
        access: FileBrowserAccessInfo,
    ): FileBrowserError = when {
        error == FileBrowserError.PermissionDenied &&
            !access.rootMode &&
            FileBrowserPaths.isRootOnly(path) -> FileBrowserError.NeedsRoot
        else -> error
    }

    private fun noPrivilegeError(path: String): FileBrowserError = when {
        path == "/" ||
            FileBrowserPaths.isRootOnly(path) ||
            FileBrowserPaths.isAndroidDataOrObb(path, internalRoot) ->
            FileBrowserError.PrivilegeUnavailable
        else -> FileBrowserError.PermissionDenied
    }

    private companion object {
        const val SHELL_TIMEOUT_MS = 15_000L
    }
}
