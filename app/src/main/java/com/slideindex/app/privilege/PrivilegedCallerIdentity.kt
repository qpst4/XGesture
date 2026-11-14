package com.slideindex.app.privilege

import android.os.Process

/**
 * 特权进程（root 守护进程 / Shizuku 用户服务）向系统服务传「调用方包名」时的身份。
 *
 * 为什么必须有这个东西：像 `tethering` 的 `startTethering` 会校验「传入的包名必须属于调用方 uid」，
 * 不匹配就整条请求丢弃；而且这类接口是 `oneway` 的，客户端拿不到异常，只会在系统日志里出现
 * `Package name X does not match UID Y` —— 失败是完全静默的。
 *
 * 所以身份只在这里算一次：所有需要「调用方包名」的特权 binder 调用都引用本对象，
 * 不再各处手写 applicationId。
 *
 * 注意：这里算的是**本地进程 uid**。如果某次调用是通过 `ShizukuBinderWrapper` 代打的，
 * 系统看到的 uid 是 Shizuku 服务的 2000，那种场景必须显式用 [SHELL]。
 */
object PrivilegedCallerIdentity {

    /** uid 0 的身份包名；与 KeyMapper 的 SystemBridge 保持一致。 */
    const val ROOT = "root"

    /** uid 2000 的身份包名。 */
    const val SHELL = "com.android.shell"

    @Volatile
    private var cached: String? = null

    /** 当前进程是否为 root / shell 特权进程。 */
    val isPrivilegedProcess: Boolean
        get() = Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SHELL_UID

    /**
     * 当前进程 uid 对应的身份包名。
     *
     * @throws IllegalStateException 当前进程既不是 root 也不是 shell —— 说明调用点放错了进程。
     * 宁可在入口就炸掉（调用方通常包在 `runCatching` 里，会转成失败分支），也不要让它静默失败。
     */
    fun require(): String {
        cached?.let { return it }
        val identity = when (Process.myUid()) {
            Process.ROOT_UID -> ROOT
            Process.SHELL_UID -> SHELL
            else -> throw IllegalStateException(
                "必须在 root(0) / shell(2000) 进程里调用，当前 uid=${Process.myUid()}",
            )
        }
        cached = identity
        return identity
    }

    /** 不抛异常版本：非特权进程返回 null。 */
    fun packageNameOrNull(): String? = runCatching { require() }.getOrNull()

    /** 诊断用：`uid=... identity=...`。 */
    fun describe(): String = "uid=${Process.myUid()} identity=${packageNameOrNull() ?: "-"}"
}
