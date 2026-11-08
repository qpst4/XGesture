package com.slideindex.app.privilege.root

import android.util.Log
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File

/**
 * Root 命令是否运行在 init/global mount namespace。
 *
 * Android 11+ 给每个 App 一个独立的 mount namespace：`/data/data` 在里面是一个只 bind 了
 * 本 App（及少量依赖）的 tmpfs。root 进程如果从 App 里 `su` 出来，会继承这个 namespace，
 * 于是“有 root”却只看到两个目录。
 *
 * 这里统一走 toybox 的 `nsenter -t 1 -m` 进入 init 的全局 namespace；不可用时退回调用方
 * 原有的命令，保证老 ROM/老 Root 方案还能工作。
 */
object RootNamespace {
    private const val TAG = "RootNamespace"

    const val NSENTER_PATH = "/system/bin/nsenter"

    @Volatile
    private var cachedUsable: Boolean? = null

    @Volatile
    private var cachedMountMaster: Boolean? = null

    fun nsenterBinaryExists(): Boolean = runCatching { File(NSENTER_PATH).exists() }
        .getOrDefault(false)

    /** 权限模式切换/重新授权后清掉探测缓存。 */
    fun invalidate() {
        cachedUsable = null
        cachedMountMaster = null
    }

    /** 真正执行一次 `nsenter ... id -u` 验证；结果缓存，避免每次 root 操作都探测。 */
    fun isUsable(): Boolean {
        cachedUsable?.let { return it }
        if (!nsenterBinaryExists()) {
            cachedUsable = false
            return false
        }
        val result = runCatching {
            TaskManagerShellExecutor.runAsRootUser(
                "$NSENTER_PATH -t 1 -m -- ${TaskManagerShellExecutor.resolveShPath()} -c 'id -u'",
                4_000L,
            )
        }.getOrNull()
        val output = result?.output.orEmpty()
        val usable = result?.exitCode == 0 && output.lineSequence().any { it.trim() == "0" }
        cachedUsable = usable
        Log.i(TAG, "global namespace usable=$usable")
        return usable
    }

    /** 没有 toybox nsenter 时的第二方案：Magisk/KernelSU 的 `su --mount-master`。 */
    fun mountMasterUsable(): Boolean {
        cachedMountMaster?.let { return it }
        val su = TaskManagerShellExecutor.resolveSuInvocation()
        val command = "$su --mount-master -c 'id -u'"
        val result = runCatching {
            TaskManagerShellExecutor.shellCommandWithOutput(
                4_000L,
                "/system/bin/sh",
                "-c",
                command,
            )
        }.getOrNull()
        val output = result?.output.orEmpty()
        val usable = result?.exitCode == 0 && output.lineSequence().any { it.trim() == "0" }
        cachedMountMaster = usable
        Log.i(TAG, "mount-master usable=$usable")
        return usable
    }

    /** 启动常驻 shell 时用：只判断二进制存在，避免探测过程递归打开 shell。 */
    fun shellLauncher(shellPath: String): String =
        if (nsenterBinaryExists()) "$NSENTER_PATH -t 1 -m -- $shellPath" else shellPath

    /** 给一条普通命令加全局 namespace 包装（用于一次性 root 命令）。 */
    fun wrapCommand(command: String): String {
        if (!isUsable()) return command
        val sh = TaskManagerShellExecutor.resolveShPath()
        val quoted = TaskManagerShellExecutor.shellQuote(command)
        return "$NSENTER_PATH -t 1 -m -- $sh -c $quoted"
    }
}
