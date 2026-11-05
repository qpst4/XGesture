package com.slideindex.app.gesture.executor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.slideindex.app.activity.ActivityShortcutLauncher
import com.slideindex.app.activity.ActivityShortcutShellSupport
import com.slideindex.app.data.AppRepository
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.CloneAppLauncher
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.ForegroundHostPackageResolver
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ActionExecutorLaunch(
    private val context: Context,
    private val appRepository: AppRepository,
    private val mainHandler: Handler,
) {
    fun launchQuickItem(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawY: Float? = null,
        execute: (GestureAction, AppSettings, Boolean, Float?) -> Boolean,
    ): Boolean {
        return when (item.type) {
            QuickLauncherItemType.APP -> {
                val target = item.appTarget ?: com.slideindex.app.launcher.AppTarget(item.payload)
                launchApp(target.packageName, settings, longPressArmed, cloneUserId = target.cloneUserId)
                false
            }
            QuickLauncherItemType.SHORTCUT ->
                launchQuickShortcut(item, settings, longPressArmed)
            QuickLauncherItemType.ACTION -> {
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                    execute(action, settings, longPressArmed, anchorRawY)
                }
                false
            }
            QuickLauncherItemType.WIDGET,
            QuickLauncherItemType.FOLDER -> false
        }
    }

    fun launchGestureShortcut(
        action: GestureAction.LaunchShortcut,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        when (val decoded = GestureShortcutPayload.decode(action.payloadKey)) {
            is GestureShortcutPayload.Decoded.Dynamic -> {
                val item = QuickLauncherItem.dynamicShortcut(
                    packageName = decoded.packageName,
                    shortcutId = decoded.shortcutId,
                    label = decoded.label.ifBlank { action.label },
                )
                launchQuickShortcut(item, settings, longPressArmed)
            }
            is GestureShortcutPayload.Decoded.Component -> {
                launchActivityComponent(decoded.componentFlat, settings, longPressArmed)
            }
            is GestureShortcutPayload.Decoded.IntentShortcut -> {
                launchIntentShortcut(decoded.intentUri, settings, longPressArmed, decoded.label)
            }
            is GestureShortcutPayload.Decoded.IntentsShortcut -> {
                launchIntentShortcuts(decoded.intentUris, settings, longPressArmed, decoded.label)
            }
            null -> Unit
        }
    }

    fun launchApp(
        packageName: String,
        settings: AppSettings,
        longPressArmed: Boolean,
        windowMode: LaunchWindowMode = LaunchWindowMode.FOLLOW_GLOBAL,
        cloneUserId: Int = 0,
    ): Boolean {
        val targetKey = com.slideindex.app.launcher.AppTarget.encode(packageName, cloneUserId)
        val baseApp = appRepository.getCachedApps().firstOrNull { it.packageName == packageName }
            ?: appRepository.lookupApp(packageName)
            ?: return false
        // 分身与主实例同包名：这里带上 cloneUserId 的 AppInfo 才能让冰箱按 `(包名, user)` 判断状态。
        val app = if (cloneUserId > 0) {
            baseApp.copy(
                label = com.slideindex.app.overlay.CloneAppsIndex.labelForUser(
                    context,
                    cloneUserId,
                    baseApp.label,
                ),
                cloneUserId = cloneUserId,
            )
        } else {
            baseApp
        }
        val fullscreen = settings.shouldLaunchFullscreen(windowMode, longPressArmed)
        if (targetKey in settings.freezerAppPackages) {
            // 冰箱成员（主实例或分身）：先按记录的档位解冻/恢复，再启动。直接 startActivity 对 disabled 包必失败。
            CoroutineScope(Dispatchers.IO).launch {
                FreezerOperations.launchAndRestore(
                    context = context,
                    appRepository = appRepository,
                    settings = settings,
                    app = app,
                    fullscreen = fullscreen,
                )
            }
            return true
        }
        // 分身/工作资料：主实例的冻结状态与 launchIntent 都不适用，直接跨 user 启动。
        if (cloneUserId > 0) {
            launchAppInUser(packageName, cloneUserId, settings)
            return true
        }
        return appRepository.launchApp(app, settings, fullscreen)
    }

    /**
     * 在指定 user 下启动应用（应用分身 / 工作资料）。
     *
     * 具体走 shell 还是 `LauncherApps` 由 [CloneAppLauncher] 决定：有特权时 shell 更稳
     * （绕过后台启动限制），没有 Shizuku 时退回公开 API。
     */
    fun launchAppInUser(packageName: String, userId: Int, settings: AppSettings): Boolean {
        if (userId <= 0) return launchApp(packageName, settings, longPressArmed = false)
        Thread {
            CloneAppLauncher.launch(context, packageName, userId)
        }.start()
        return true
    }

    fun switchToRecentTask(
        taskId: Int,
        rawIdentifier: String,
        topComponent: String,
        packageName: String,
        settings: AppSettings,
    ) {
        Thread {
            val switched = runCatching {
                TaskManagerUtil.switchToTask(
                    taskId = taskId,
                    identifier = rawIdentifier,
                    topComponent = topComponent,
                )
            }.getOrElse { error ->
                Log.e(
                    ActionExecutor.TAG,
                    "switchToRecentTask failed taskId=$taskId raw=$rawIdentifier component=$topComponent",
                    error,
                )
                false
            }
            if (switched) {
                RecentTasksLoader.requestRefreshAfterSwitch(appRepository)
            } else {
                mainHandler.post {
                    launchRecentTaskFallback(topComponent, rawIdentifier, packageName, settings)
                }
            }
        }.start()
    }

    fun closeCurrentApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.removeCurrentFrontAppTask() }.start()
    }

    /** 强行停止当前前台应用（真杀进程），与 [closeCurrentApp] 的「移除最近任务卡片」语义不同。 */
    fun forceStopCurrentApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.forceStopCurrentFrontApp() }.start()
    }

    /**
     * 小窗化当前应用。
     *
     * 魅族模式：先试"把已有任务搬进小窗"（Shizuku/root 原地搬移 + 读回校验），搬不动就直接走下面的
     * 一次 ActivityOptions 启动 —— 不另开小窗。
     * 其余模式照搬 SideGesture「应用小窗(7.0+)」：取前台包 → 解析它的 launcher Activity →
     * 一次 `startActivity` + ActivityOptions；不搬移已有任务、不校验、不重试，也不叠加 MULTIPLE_TASK。
     */
    fun freeWindowForegroundApp(settings: AppSettings) {
        val effectiveSettings = settings.copy(freeWindow = settings.freeWindow.copy(freeWindowEnabled = true))
        if (effectiveSettings.resolvedFreeWindowMode() == FreeWindowMode.FLYME) {
            freeWindowForegroundAppViaTaskMove(effectiveSettings)
            return
        }
        launchCurrentAppInFreeWindow(effectiveSettings)
    }

    /** 魅族模式：先试原地搬移，搬不动退回同一个"一次启动"路径。 */
    private fun freeWindowForegroundAppViaTaskMove(settings: AppSettings) {
        val runMove = Runnable {
            val targetPackage = ForegroundHostPackageResolver.resolveForFreeWindow(context)
            if (targetPackage == null) {
                launchCurrentAppInFreeWindow(settings)
                return@Runnable
            }
            if (!TaskManagerUtil.hasPermission()) {
                mainHandler.post { launchCurrentAppInFreeWindow(settings) }
                return@Runnable
            }
            Thread {
                val moved = try {
                    TaskManagerUtil.ensureServiceBound()
                    var inPlace = TaskManagerUtil.movePackageToFreeWindow(targetPackage, settings)
                    if (!inPlace) {
                        inPlace = TaskManagerUtil.moveFrontTaskToFreeWindow(settings)
                    }
                    inPlace
                } catch (error: Exception) {
                    Log.e(ActionExecutor.TAG, "freeWindowForegroundApp failed", error)
                    false
                }
                if (!moved) {
                    mainHandler.post { launchCurrentAppInFreeWindow(settings) }
                }
            }.start()
        }
        mainHandler.postDelayed(runMove, 120L)
    }

    /** 取前台包，一次 ActivityOptions 启动成小窗（与其余模式共用）。 */
    private fun launchCurrentAppInFreeWindow(settings: AppSettings) {
        val targetPackage = ForegroundHostPackageResolver.resolveForFreeWindow(context) ?: return
        FreeWindowLauncher.launchPackageInFreeWindow(context, targetPackage, settings)
    }

    private fun launchRecentTaskFallback(
        topComponent: String,
        rawIdentifier: String,
        packageName: String,
        settings: AppSettings,
    ) {
        if (topComponent.isNotBlank() && launchComponent(topComponent, settings)) return
        if (launchRawIdentifier(rawIdentifier, packageName, settings)) return
        if (shouldLaunchPackageFallback(rawIdentifier, packageName) &&
            launchApp(packageName, settings, longPressArmed = false)
        ) {
            return
        }
        Log.w(
            ActionExecutor.TAG,
            "recents switch failed raw=$rawIdentifier package=$packageName component=$topComponent",
        )
    }

    private fun launchRawIdentifier(
        rawIdentifier: String,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        val raw = rawIdentifier.trim()
        if (raw.isBlank()) return false
        if (raw.contains('/') && launchComponent(raw, settings)) return true
        if (raw != packageName && raw.startsWith("$packageName.")) {
            return launchComponent(ComponentName(packageName, raw), settings)
        }
        return false
    }

    private fun shouldLaunchPackageFallback(rawIdentifier: String, packageName: String): Boolean {
        val raw = rawIdentifier.trim()
        return raw.isBlank() || raw == packageName
    }

    private fun launchComponent(componentRaw: String, settings: AppSettings): Boolean {
        val component = componentFromRawIdentifier(componentRaw) ?: return false
        return launchComponent(component, settings)
    }

    private fun launchComponent(component: ComponentName, settings: AppSettings): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setComponent(component)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            if (settings.freeWindowEnabled) {
                FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrElse { error ->
            Log.w(ActionExecutor.TAG, "launchComponent($component) failed", error)
            false
        }
    }

    private fun componentFromRawIdentifier(rawIdentifier: String): ComponentName? {
        val trimmed = rawIdentifier.trim()
        if (!trimmed.contains('/')) return null
        val pkg = trimmed.substringBefore('/').trim()
        var cls = trimmed.substringAfter('/').trim()
        if (cls.startsWith('.')) cls = pkg + cls
        if (pkg.isEmpty() || cls.isEmpty()) return null
        return ComponentName(pkg, cls)
    }

    private fun launchQuickShortcut(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean,
    ): Boolean {
        QuickLauncherItemCodec.parseIntentPayload(item.payload)?.let { intentUri ->
            launchIntentShortcut(intentUri, settings, longPressArmed, item.label)
            return false
        }
        QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.let { intentUris ->
            launchIntentShortcuts(intentUris, settings, longPressArmed, item.label)
            return false
        }
        val dynamic = QuickLauncherItemCodec.parseShortcutPayload(item.payload)
        if (dynamic != null) {
            val (packageName, shortcutId) = dynamic
            val launchResolved: (TaskSwitcherMenuItem) -> Unit = { menuItem ->
                val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
                if (!fullscreen && settings.freeWindowEnabled) {
                    launchShortcutInFreeWindow(packageName, menuItem, settings)
                } else {
                    AppShortcutLoader.launchShortcut(context, packageName, menuItem)
                }
            }
            val cached = AppShortcutLoader.peekResolvedShortcut(packageName, shortcutId)
                ?.takeUnless { it.isServiceShortcut }
            if (cached != null) {
                launchResolved(cached)
                return false
            }
            Thread {
                val resolved = AppShortcutLoader.resolveShortcutForLaunch(
                    context = context,
                    packageName = packageName,
                    shortcutId = shortcutId,
                    label = item.label,
                )
                mainHandler.post { launchResolved(resolved) }
            }.start()
            return true
        }
        launchActivityComponent(item.payload, settings, longPressArmed)
        return false
    }

    private fun launchActivityComponent(
        componentFlat: String,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        val component = componentFromRawIdentifier(componentFlat) ?: return
        ActivityShortcutLauncher.launch(
            context = context,
            packageName = component.packageName,
            activityClassName = component.className,
            settings = settings,
            longPressTriggered = longPressArmed,
        )
    }

    private fun launchShortcutInFreeWindow(
        packageName: String,
        item: TaskSwitcherMenuItem,
        settings: AppSettings,
    ) {
        val intent = item.shortcutIntent
        if (intent != null) {
            FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
            return
        }
        val shortcutId = item.shortcutId
        if (!shortcutId.isNullOrBlank()) {
            val options = FreeWindowLauncher.launchOptionsBundle(context, settings)
            val trampoline = com.slideindex.app.service.LaunchTrampolineActivity.createShortcutIntent(
                context,
                packageName,
                shortcutId,
                options,
            )
            val started = runCatching {
                context.startActivity(trampoline)
            }.isSuccess
            if (started) return
        }
        AppShortcutLoader.launchShortcut(context, packageName, item)
    }

    private fun launchIntentShortcut(
        intentUri: String,
        settings: AppSettings,
        longPressArmed: Boolean,
        label: String = "",
    ) {
        launchIntentShortcuts(listOf(intentUri), settings, longPressArmed, label)
    }

    private fun launchIntentShortcuts(
        intentUris: List<String>,
        settings: AppSettings,
        longPressArmed: Boolean,
        label: String = "",
    ) {
        val intents = mutableListOf<Intent>()
        for (uri in intentUris) {
            if (ActivityShortcutShellSupport.isShellUri(uri)) {
                val command = ActivityShortcutShellSupport.decodeCommand(uri)
                if (command.isNotBlank()) {
                    Thread {
                        ShellCommandRunner.execute(
                            context = context,
                            command = ShellCommand(
                                label = label.ifBlank { "Shortcut" },
                                command = command,
                            ),
                        )
                    }.start()
                }
                return
            }
            runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            }.getOrNull()?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { intents.add(it) }
        }
        if (intents.isEmpty()) return
        val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
        if (intents.size > 1) {
            runCatching { context.startActivities(intents.toTypedArray()) }
            return
        }
        val intent = intents[0]
        if (fullscreen || !settings.freeWindowEnabled) {
            runCatching {
                context.startActivity(
                    com.slideindex.app.service.LaunchTrampolineActivity.createIntent(context, intent),
                )
            }.onFailure {
                context.startActivity(intent)
            }
        } else {
            FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
        }
    }
}
