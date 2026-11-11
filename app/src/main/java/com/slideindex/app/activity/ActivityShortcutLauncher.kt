package com.slideindex.app.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandModeResolver
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.ShellResultFormatter
import com.slideindex.app.util.ShortcutLaunchDispatcher
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
        val intent = Intent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setComponent(ComponentName(packageName, activityClassName))
        return ShortcutLaunchDispatcher.launch(
            context = context,
            packageName = packageName,
            intent = intent,
            exportedLaunch = { target ->
                startActivity(context, target, settings, longPressTriggered)
            },
            onFailure = { failure ->
                ShortcutLaunchDispatcher.showFailure(context, failure)
            },
        )
    }

    private fun launchDynamic(
        context: Context,
        shortcut: ActivityShortcut,
        settings: AppSettings,
        longPressTriggered: Boolean,
        showFailureToast: Boolean,
    ): Boolean {
        val resolved = AppShortcutLoader.resolveShortcutForLaunch(
            context = context,
            packageName = shortcut.packageName,
            shortcutId = shortcut.shortcutId,
            label = shortcut.label,
        )
        val intent = resolved.shortcutIntent
        if (intent != null) {
            return ShortcutLaunchDispatcher.launch(
                context = context,
                packageName = shortcut.packageName,
                intent = intent,
                exportedLaunch = { target ->
                    startActivity(context, target, settings, longPressTriggered)
                },
                onFailure = { failure ->
                    if (showFailureToast) {
                        ShortcutLaunchDispatcher.showFailure(context, failure)
                    }
                },
            )
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
                    val mode = ShellCommandModeResolver.forShortcut(
                        shortcutId = shortcut.shortcutId,
                        command = command,
                        savedCommands = settings.shellCommands,
                        defaultMode = ShellCommandModeResolver.currentDefault(),
                    )
                    // 执行可能耗时数秒，不能占着拉起快捷方式的主线程。
                    Thread {
                        val outcome = ShellCommandRunner.execute(
                            context = context,
                            command = ShellCommand(
                                label = shortcut.label,
                                command = command,
                                executionMode = mode,
                            ),
                        )
                        if (outcome.exitCode != 0) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    ShellResultFormatter.toast(context, outcome.exitCode, outcome.output),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }.start()
                    return true
                }
                continue
            }
            val intent = runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.getOrNull() ?: continue
            val launched = ShortcutLaunchDispatcher.launch(
                context = context,
                packageName = shortcut.packageName,
                intent = intent,
                exportedLaunch = { target ->
                    startActivity(context, target, settings, longPressTriggered)
                },
            )
            if (launched) return true
        }
        Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        return false
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
        }.getOrDefault(false)
    }
}
