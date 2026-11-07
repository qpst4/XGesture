package com.slideindex.app.overlay

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.shell.ShellCommand
import java.util.concurrent.Executors

/**
 * Loads honeycomb launcher icons off the UI thread so [HoneycombAppPickerOverlayWindow.show]
 * can attach the overlay before PackageManager work finishes.
 */
object HoneycombIconLoader {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HoneycombIcons").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun warmAppIcons(
        appRepository: AppRepository,
        items: List<QuickLauncherItem>,
        sizePx: Int
    ) {
        val packages = items.asSequence()
            .filter { it.type == QuickLauncherItemType.APP }
            .map { it.appTarget?.packageName ?: it.payload }
            .filter { it.isNotBlank() }
            .toSet()
        if (packages.isEmpty()) return
        appRepository.warmLaunchIconBitmapsAsync(packages, sizePx.coerceAtLeast(48))
    }

    fun loadMissingIconsAsync(
        context: Context,
        targets: List<HoneycombRuntimeTarget>,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
        onIconsReady: () -> Unit
    ) {
        if (targets.none { it.icon == null }) return
        val appContext = context.applicationContext ?: context
        worker.execute {
            var updated = false
            for (target in targets) {
                if (target.icon != null) continue
                val drawable = resolveIcon(
                    appContext,
                    target,
                    appsByPackage,
                    appRepository,
                    activityShortcuts,
                    shellCommands
                )
                if (drawable != null) {
                    target.icon = com.slideindex.app.util.CloneBadge.wrap(
                        drawable,
                        target.item.appTarget?.isClone == true,
                    ) ?: drawable
                    updated = true
                }
            }
            if (updated) {
                mainHandler.post(onIconsReady)
            }
        }
    }

    private fun resolveIcon(
        context: Context,
        target: HoneycombRuntimeTarget,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>
    ): Drawable? {
        val item = target.item
        return when (item.type) {
            QuickLauncherItemType.APP -> {
                val packageName = item.appTarget?.packageName ?: item.payload
                // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideDrawable?.invoke(context, packageName, 128)
                    ?: appRepository.launchIconDrawable(packageName)
            }
            QuickLauncherItemType.SHORTCUT, QuickLauncherItemType.ACTION ->
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconDrawable?.invoke(
                    item,
                    appsByPackage,
                    context,
                    activityShortcuts,
                    shellCommands
                )
            else -> null
        }
    }
}
