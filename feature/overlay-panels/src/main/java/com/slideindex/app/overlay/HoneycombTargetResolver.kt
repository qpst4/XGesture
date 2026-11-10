package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.isCloneTarget
import com.slideindex.app.shell.ShellCommand

object HoneycombTargetResolver {
    fun resolve(
        context: Context,
        items: List<QuickLauncherItem>,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository? = null,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList()
    ): List<HoneycombRuntimeTarget> =
        items.mapNotNull { item ->
            if (item.type != QuickLauncherItemType.APP &&
                item.type != QuickLauncherItemType.SHORTCUT &&
                item.type != QuickLauncherItemType.ACTION
            ) {
                return@mapNotNull null
            }
            val label = com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherLabel?.invoke(context, item, appsByPackage) ?: item.payload
            val target = item.appTarget
            val icon = when {
                appRepository != null && item.type == QuickLauncherItemType.APP -> {
                    val packageName = target?.packageName ?: item.payload
                    // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）。
                    // 这里必须查：target.icon 会被渲染方当成 resolvedIcon 直接用，
                    // 预填成系统原图后再也走不到覆盖分支（圆环 / 蜂窝的真机表现）。
                    com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideDrawable?.invoke(context, packageName, 128)
                        ?: appRepository.peekLaunchIconDrawable(packageName)
                }
                appRepository == null || item.type == QuickLauncherItemType.SHORTCUT ||
                    item.type == QuickLauncherItemType.ACTION ->
                    com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconDrawable?.invoke(
                        item,
                        appsByPackage,
                        context,
                        activityShortcuts,
                        shellCommands
                    )
                else -> null
            }
            HoneycombRuntimeTarget(
                item = item,
                label = label,
                icon = icon,
                shellCommands = shellCommands,
                isClone = item.isCloneTarget(context),
            )
        }
}
