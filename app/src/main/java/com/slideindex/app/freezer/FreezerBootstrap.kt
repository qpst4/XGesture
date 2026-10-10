package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.slideindex.app.settings.FreezerAppIntent

object FreezerBootstrap {
    /**
     * 扫描「已停用 **或** 已挂起」的桌面应用：两种状态都可能来自别的冻结 / 暂停工具（或本应用的旧数据），
     * 需要导入冰箱列表统一管理。
     */
    fun scanImportableLauncherPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@filter false
                isImportableState(
                    enabled = info.enabled,
                    suspended = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
                )
            }
            .toSet()
    }

    /** 已停用（冻结）或已挂起（暂停）都值得导入；正常使用的应用不需要。 */
    fun isImportableState(enabled: Boolean, suspended: Boolean): Boolean = !enabled || suspended

    /**
     * 导入时的意图补记：导入的包本身就带着状态，直接当成它的意图，避免手势按全局模式猜档位。
     * 活跃状态没有原状态可记，返回 null 表示不写意图（由批量动作兜底）。
     */
    fun intentForState(state: FreezerAppState): FreezerAppIntent? = when (state) {
        FreezerAppState.FROZEN -> FreezerAppIntent.FROZEN
        FreezerAppState.PAUSED -> FreezerAppIntent.PAUSE
        FreezerAppState.ACTIVE -> null
    }

    fun importablePackages(scanned: Set<String>, excluded: Set<String>): Set<String> =
        scanned - excluded
}
