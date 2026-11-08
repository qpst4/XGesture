package com.slideindex.app.overlay.filebrowser

import android.content.Context
import com.slideindex.app.shizuku.TaskManagerShellExecutor

/**
 * 文件浏览器的宿主能力端口。
 *
 * 特权（root/shizuku）、OCR、暂存夹落盘、设置快照都在 `:app`，模块侧只消费这些入口。
 */
interface FileBrowserHost {

    /** 是否有 root 或已授权的 shizuku。 */
    fun hasPrivilegedAccess(): Boolean

    /** shizuku 是否已授权。 */
    fun hasShizukuPermission(): Boolean

    /** 申请 shizuku 权限（内部会引导去 Shizuku App）。 */
    fun requestShizukuPermission(context: Context)

    /** 特权 shell 执行。 */
    fun runShellCommandLine(command: String, useRoot: Boolean, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult

    /** 把图片钉到屏幕上（暂存夹能力）。 */
    fun pinImageToScreen(context: Context, bitmap: android.graphics.Bitmap)

    /** 启动文件浏览器中转 Activity（用于从外部打开指定路径）。 */
    fun launchTrampoline(context: Context, path: String?)
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object FileBrowserHostAccess {

    @Volatile
    var host: FileBrowserHost? = null
}
