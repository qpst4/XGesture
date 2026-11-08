package com.slideindex.app.overlay.filebrowser

import android.content.Context
import com.slideindex.app.shizuku.TaskManagerShellExecutor

/**
 * 文件浏览器调用宿主能力的薄封装：未注入时统一降级，避免每个调用点写 `?.` 判空。
 */
object FileBrowserHostCalls {

    fun isRootMode(): Boolean =
        FileBrowserHostAccess.host?.isRootMode() == true

    fun hasPrivilegedAccess(): Boolean =
        FileBrowserHostAccess.host?.hasPrivilegedAccess() == true

    fun hasShizukuPermission(): Boolean =
        FileBrowserHostAccess.host?.hasShizukuPermission() == true

    fun requestPermission(context: Context) {
        FileBrowserHostAccess.host?.requestShizukuPermission(context)
    }

    fun runShellCommandLine(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long = 35_000L,
    ): TaskManagerShellExecutor.ShellExecResult =
        FileBrowserHostAccess.host?.runShellCommandLine(command, useRoot, timeoutMs)
            ?: TaskManagerShellExecutor.ShellExecResult(-1, "no-privilege")

    fun pinImageToScreen(context: Context, bitmap: android.graphics.Bitmap) {
        FileBrowserHostAccess.host?.pinImageToScreen(context, bitmap)
    }

    fun showPinFailedToast(context: Context) {
        FileBrowserHostAccess.host?.showPinFailedToast(context)
    }

    fun requestPinShortcut(context: Context): Boolean =
        FileBrowserHostAccess.host?.requestPinShortcut(context) == true

    fun launchImageEditor(context: Context, bitmap: android.graphics.Bitmap): Boolean =
        FileBrowserHostAccess.host?.launchImageEditor(context, bitmap) == true

    fun showSearchPanel(context: Context): Boolean =
        FileBrowserHostAccess.host?.showSearchPanel(context) == true

    fun launchTrampoline(context: Context, path: String?) {
        FileBrowserHostAccess.host?.launchTrampoline(context, path)
    }
}
