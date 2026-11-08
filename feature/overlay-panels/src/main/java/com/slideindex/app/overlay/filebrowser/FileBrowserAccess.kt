package com.slideindex.app.overlay.filebrowser

import android.os.Environment

enum class AccessMode {
    /** Root 模式且已实际拿到 root。 */
    ROOT,

    /** Shizuku 模式且已授权（shell uid 2000）。 */
    SHIZUKU,

    /** 没有特权，但已授予“所有文件访问”。 */
    ALL_FILES,

    /** 没有特权，也没有“所有文件访问”。 */
    LIMITED,
}

data class FileBrowserAccessInfo(
    val rootMode: Boolean,
    val privileged: Boolean,
    val allFiles: Boolean,
) {
    val mode: AccessMode
        get() = when {
            rootMode && privileged -> AccessMode.ROOT
            privileged -> AccessMode.SHIZUKU
            allFiles -> AccessMode.ALL_FILES
            else -> AccessMode.LIMITED
        }
}

/**
 * 访问能力探测。
 *
 * **必须在 IO 线程调用**：ROOT 模式下 `hasPrivilegedAccess()` 可能触发一次 `su` 探测。
 */
object FileBrowserAccess {
    fun resolve(): FileBrowserAccessInfo {
        val rootMode = FileBrowserHostCalls.isRootMode()
        val privileged = if (rootMode) {
            FileBrowserHostCalls.hasPrivilegedAccess()
        } else {
            FileBrowserHostCalls.hasShizukuPermission()
        }
        return FileBrowserAccessInfo(
            rootMode = rootMode,
            privileged = privileged,
            allFiles = Environment.isExternalStorageManager(),
        )
    }
}
