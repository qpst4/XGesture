package com.slideindex.app.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.search.NonExportedActivityLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.TaskManagerUtil

object ActivityShortcutLauncher {
    fun launch(
        context: Context,
        shortcut: ActivityShortcut,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ): Boolean = when (shortcut.kind) {
        ActivityShortcutKind.COMPONENT -> launch(
            context = context,
            packageName = shortcut.packageName,
            activityClassName = shortcut.activityClassName,
            settings = settings,
            longPressTriggered = longPressTriggered
        )
        ActivityShortcutKind.DYNAMIC -> launchDynamic(
            context, shortcut, settings, longPressTriggered, showFailureToast = true,
        )
        ActivityShortcutKind.INTENT -> {
            // 应用快捷方式优先按 ID 解析；失败再退回 intent URI（老数据兜底）。
            val launchedByShortcutId = shortcut.isAppShortcut &&
                launchDynamic(context, shortcut, settings, longPressTriggered, showFailureToast = false)
            if (launchedByShortcutId) {
                true
            } else {
                launchIntents(context, shortcut, settings, longPressTriggered)
            }
        }
    }

    fun launch(
        context: Context,
        packageName: String,
        activityClassName: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ): Boolean {
        if (packageName.isBlank() || activityClassName.isBlank()) return false
        return if (PackageActivityResolver.isActivityExported(context, packageName, activityClassName)) {
            val intent = Intent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(ComponentName(packageName, activityClassName))
            startActivity(context, intent, settings, longPressTriggered)
        } else {
            launchNonExported(context, packageName, activityClassName)
        }
    }

    private fun launchDynamic(
        context: Context,
        shortcut: ActivityShortcut,
        settings: AppSettings,
        longPressTriggered: Boolean,
        showFailureToast: Boolean,
    ): Boolean {
        val item = QuickLauncherItem.dynamicShortcut(
            packageName = shortcut.packageName,
            shortcutId = shortcut.shortcutId,
            label = shortcut.label
        )
        AppShortcutLoader.warmQuickLauncherShortcuts(context, listOf(item))
        val resolved = AppShortcutLoader.peekResolvedShortcut(shortcut.packageName, shortcut.shortcutId)
        val intent = resolved?.shortcutIntent
        if (intent != null) {
            return startActivity(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), settings, longPressTriggered)
        }
        val started = TaskManagerUtil.startPublishedShortcut(shortcut.packageName, shortcut.shortcutId)
        if (!started && showFailureToast) {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
        return started
    }

    private fun launchIntents(
        context: Context,
        shortcut: ActivityShortcut,
        settings: AppSettings,
        longPressTriggered: Boolean
    ): Boolean {
        for (uri in shortcut.intentUris) {
            if (ActivityShortcutShellSupport.isShellUri(uri)) {
                val command = ActivityShortcutShellSupport.decodeCommand(uri)
                if (command.isNotBlank()) {
                    com.slideindex.app.util.ShellCommandRunner.execute(
                        context = context,
                        command = com.slideindex.app.shell.ShellCommand(
                            label = shortcut.label,
                            command = command
                        )
                    )
                    return true
                }
                continue
            }
            val intent = runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.getOrNull() ?: continue
            if (startActivity(context, intent, settings, longPressTriggered)) return true
        }
        Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun launchNonExported(context: Context, packageName: String, activityClassName: String): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, PrivilegeUiStrings.privilegedAccessRequiredRes(), Toast.LENGTH_LONG).show()
            return false
        }
        NonExportedActivityLauncher.launch(
            context = context,
            packageName = packageName,
            activityName = activityClassName
        ) { success ->
            if (!success) {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun startActivity(
        context: Context,
        intent: Intent,
        settings: AppSettings,
        longPressTriggered: Boolean
    ): Boolean {
        return runCatching {
            val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
            FreeWindowLauncher.launch(context, intent, settings, fullscreen)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
