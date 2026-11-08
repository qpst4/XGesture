package com.slideindex.app.shell

import android.content.Context

/**
 * shell/root 执行链路需要的宿主能力。
 *
 * `:core:common` 里的 `TaskManagerShellExecutor` 原本直接读 app 的资源与权限模式，
 * 下沉后改为在这里注入：`SlideIndexApp` 启动时填上，模块内不感知 `:app`。
 */
object ShellRuntimeHooks {

    /** 按名字取 app 的字符串资源（取不到时返回 [fallback]）。 */
    @Volatile
    var stringResolver: ((Context, String) -> String?)? = null

    /** 当前是否 root 模式。 */
    @Volatile
    var rootModeProvider: (() -> Boolean)? = null

    internal fun stringOf(context: Context?, name: String, fallback: String? = null): String? {
        if (context == null) return fallback
        return stringResolver?.invoke(context, name) ?: fallback
    }

    internal fun isRootMode(): Boolean = rootModeProvider?.invoke() ?: false
}
