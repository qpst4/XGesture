package com.slideindex.app.util

import android.app.ActivityManager
import android.content.Context

/**
 * 主进程（与包名同名、承载 MainActivity 的进程）此刻是否在前台。
 *
 * 无障碍服务与大部分浮层跑在 `:overlay` 进程，进程内静态状态代表不了主界面；
 * 这里统一用 ActivityManager 的进程 importance 判断，避免把“本应用浮层盖在别的应用上”
 * 误判成“本应用页面在前台”。
 */
object SelfAppForeground {

    fun isForeground(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return@runCatching false
        val selfPackage = context.packageName
        manager.runningAppProcesses?.any { info ->
            info.processName == selfPackage &&
                info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false
    }.getOrDefault(false)
}
