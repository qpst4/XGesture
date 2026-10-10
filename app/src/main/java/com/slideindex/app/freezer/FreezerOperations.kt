package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FreezerAppIntent
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerOperations {
    fun hasShellAccess(): Boolean = TaskManagerUtil.hasPrivilegedAccess()

    /**
     * 冰箱提示统一出口：**优先用覆盖层窗口显示**，拿不到悬浮窗权限时由它自己回退系统 Toast。
     *
     * 为什么不用系统 Toast：冰箱面板是全屏浮窗（`TYPE_APPLICATION_OVERLAY` + `MATCH_PARENT`），
     * 面板打开时弹出的 Toast 会被面板自己盖在底下（用户反馈「提示看不见」）。覆盖层窗口是同类
     * 窗口、后 `addView`，稳定叠在面板之上。
     */
    private suspend fun showFreezerMessage(context: Context, message: String) {
        Log.i(TAG, "message: $message")
        withContext(Dispatchers.Main) {
            OverlayToastWindow.show(context, message)
        }
    }

    private suspend fun showFreezerMessage(context: Context, messageRes: Int) {
        showFreezerMessage(context, context.getString(messageRes))
    }

    /**
     * 记下「用户要这个成员处于哪种状态」。
     *
     * 系统只有 enabled / suspended 两个当前状态，没有「上次用的是冻结还是暂停」；
     * 而我们又必须知道它，才能在用户点开应用（`launchAndRestore` 会把状态清成启用）之后
     * 还能把它按原样收回去。列表外的包不记录，避免留下永远不会被用到的档位。
     */
    private suspend fun setFreezerIntent(context: Context, packageName: String, intent: FreezerAppIntent) {
        val repository = freezerSettingsRepository(context)
        if (repository == null) {
            Log.w(TAG, "intent not recorded (no settings repository): $packageName -> ${intent.storageValue}")
            return
        }
        if (packageName !in repository.readSnapshot().freezerAppPackages) {
            Log.i(TAG, "intent not recorded (not a freezer member): $packageName")
            return
        }
        Log.i(TAG, "intent recorded: $packageName -> ${intent.storageValue}")
        runCatching { repository.setFreezerAppIntent(packageName, intent) }
            .onFailure { Log.w(TAG, "intent write failed: $packageName", it) }
    }

    private fun freezerSettingsRepository(context: Context): SettingsRepository? = runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppGraphEntryPoint::class.java
        ).dependencies().settingsRepository
    }.getOrNull()

    /** 一次查询出三态；Compose 组合期逐项调用，不要拆成多次包管理查询。 */
    fun stateOf(context: Context, packageName: String): FreezerAppState =
        FreezerPrivilegedOps.appState(context, packageName)

    fun isFrozen(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isAppDisabled(context, packageName)

    fun isPaused(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isAppSuspended(context, packageName)

    /** 本应用自身、system、SystemUI、当前桌面等「动了就回不来」的包。 */
    fun isProtectedPackage(context: Context, packageName: String): Boolean =
        FreezerPrivilegedOps.isProtectedPackage(context, packageName)

    suspend fun setFrozen(context: Context, packageName: String, frozen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) {
                showFreezerMessage(context, R.string.freezer_permission_required)
                return@withContext false
            }
            if (frozen && FreezerPrivilegedOps.isProtectedPackage(context, packageName)) {
                showFreezerMessage(context, R.string.freezer_protected_package)
                return@withContext false
            }
            if (frozen && isPaused(context, packageName)) {
                // 停用会让桌面图标消失，挂起态在它面前「看不见」：先取消暂停再停用，
                // 否则之后解冻出来仍是挂起态，用户会以为解冻失败。
                FreezerPrivilegedOps.setAppSuspended(context, packageName, suspended = false, dialogMessage = null)
            }
            val (success, detail) = FreezerPrivilegedOps.setAppDisabled(context, packageName, frozen)
            if (success) {
                Log.i(TAG, "setFrozen($packageName, $frozen) -> ok")
                if (frozen) setFreezerIntent(context, packageName, FreezerAppIntent.FROZEN)
                return@withContext true
            }
            Log.w(TAG, "setFrozen($packageName, $frozen) -> failed: ${detail.take(160)}")
            val message = when (detail) {
                FreezerPrivilegedOps.NEED_ROOT_FOR_SYSTEM_DISABLE ->
                    context.getString(R.string.freezer_unfreeze_need_root)
                else -> {
                    val messageRes = if (frozen) {
                        R.string.freezer_freeze_failed
                    } else {
                        R.string.freezer_unfreeze_failed
                    }
                    detail.take(160).ifBlank { null }?.let {
                        context.getString(messageRes, it)
                    } ?: context.getString(R.string.freezer_permission_required)
                }
            }
            showFreezerMessage(context, message)
            false
        }

    /**
     * 暂停（应用挂起）：应用仍安装，桌面图标保留但灰化、点击弹系统对话框。
     * 与冻结的差别是「图标不消失」，代价是挂起只拦启动、不拦后台。
     */
    suspend fun setPaused(context: Context, packageName: String, paused: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) {
                showFreezerMessage(context, R.string.freezer_permission_required)
                return@withContext false
            }
            if (paused) {
                if (FreezerPrivilegedOps.isProtectedPackage(context, packageName)) {
                    showFreezerMessage(context, R.string.freezer_protected_package)
                    return@withContext false
                }
                if (isFrozen(context, packageName)) {
                    // 挂起本身不要求 enabled，但停用的应用图标已经消失，暂停没有意义：先解冻。
                    if (!setFrozen(context, packageName, frozen = false)) return@withContext false
                }
            }
            val dialogMessage = if (paused) {
                context.getString(
                    R.string.freezer_pause_dialog_message,
                    context.getString(R.string.app_name),
                )
            } else {
                null
            }
            val (success, detail) =
                FreezerPrivilegedOps.setAppSuspended(context, packageName, paused, dialogMessage)
            if (success) {
                Log.i(TAG, "setPaused($packageName, $paused) -> ok")
                // 取消暂停不改意图：用户要的还是「暂停」，只是这次为了使用而临时放出来。
                if (paused) setFreezerIntent(context, packageName, FreezerAppIntent.PAUSE)
                return@withContext true
            }
            Log.w(TAG, "setPaused($packageName, $paused) -> failed: ${detail.take(160)}")
            val messageRes = if (paused) {
                R.string.freezer_pause_failed
            } else {
                R.string.freezer_unpause_failed
            }
            val message = detail.take(160).ifBlank { null }?.let {
                context.getString(messageRes, it)
            } ?: context.getString(R.string.freezer_permission_required)
            showFreezerMessage(context, message)
            false
        }

    /**
     * 点击冰箱里的应用：按当前状态恢复（解冻 / 取消暂停）后再启动。
     *
     * 恢复前先把「它原本是冻结还是暂停」补进意图表：状态一被清成启用，系统那边就再也看不出
     * 它原来是哪一种了。这样用户点开应用、用完再用「重冻应用」手势收回时，它会回到原来的档位。
     */
    suspend fun launchAndRestore(
        context: Context,
        appRepository: AppRepository,
        settings: AppSettings,
        app: AppInfo,
        fullscreen: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        when (val state = stateOf(context, app.packageName)) {
            FreezerAppState.FROZEN -> {
                setFreezerIntent(context, app.packageName, FreezerAppIntent.FROZEN)
                if (!setFrozen(context, app.packageName, frozen = false)) {
                    return@withContext false
                }
            }
            FreezerAppState.PAUSED -> {
                setFreezerIntent(context, app.packageName, FreezerAppIntent.PAUSE)
                if (!setPaused(context, app.packageName, paused = false)) {
                    return@withContext false
                }
            }
            FreezerAppState.ACTIVE ->
                // 已经是启用的：没有原状态可记（首次加入列表的成员就落在这里，由批量动作
                // 按全局工作模式兜底），也不需要在启动前做任何恢复。
                Unit
        }
        withContext(Dispatchers.Main) {
            launchApp(context, app, settings, appRepository, fullscreen)
        }
    }

    private fun launchApp(
        context: Context,
        app: AppInfo,
        settings: AppSettings,
        appRepository: AppRepository,
        fullscreen: Boolean
    ): Boolean {
        val effectiveSettings = if (!fullscreen) {
            settings.copy(freeWindow = settings.freeWindow.copy(freeWindowEnabled = true))
        } else {
            settings
        }
        if (appRepository.launchApp(app, effectiveSettings, fullscreen = fullscreen)) return true
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(app.packageName)
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, flags)
        }.firstOrNull() ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
            setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * 批量动作的统一入口：**按每个成员自己记录的档位收回**。冰箱面板底部按钮、「重冻应用」
     * 手势与后续的自动触发都走这里。
     *
     * 有记录的按记录来（上次是暂停的就还它暂停，不会变成冻结）；没有记录的（刚加入列表、
     * 或从别的工具导入的）按 [fallbackPause] 兜底 —— 取全局工作模式。
     *
     * 之所以不是「一律冻结」：用户点开一个应用时状态会被清成启用，只有记录还记得它原来
     * 该是冻结还是暂停。一律冻结会把用户手动设成暂停的成员一起变成图标消失。
     */
    suspend fun restoreIntents(
        context: Context,
        packages: Set<String>,
        fallbackPause: Boolean,
        report: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            Log.w(TAG, "restoreIntents: no privileged access, members=${packages.size}")
            if (report) showFreezerMessage(context, R.string.freezer_permission_required)
            return@withContext 0
        }
        val intents = freezerSettingsRepository(context)?.readSnapshot()?.freezerAppIntents.orEmpty()
        var frozen = 0
        var paused = 0
        for (pkg in packages) {
            val state = stateOf(context, pkg)
            val decision = FreezerIntentResolution.decide(
                state = state,
                intent = intents[pkg],
                fallbackPause = fallbackPause,
            )
            Log.d(
                TAG,
                "restoreIntents: $pkg state=$state intent=${intents[pkg]?.storageValue ?: "none"} " +
                    "fallbackPause=$fallbackPause -> $decision"
            )
            when (decision) {
                FreezerIntentResolution.Decision.Skip -> Unit
                FreezerIntentResolution.Decision.Freeze -> if (setFrozen(context, pkg, frozen = true)) frozen++
                FreezerIntentResolution.Decision.Pause -> if (setPaused(context, pkg, paused = true)) paused++
            }
        }
        val count = frozen + paused
        Log.i(TAG, "restoreIntents done: members=${packages.size} changed=$count frozen=$frozen paused=$paused")
        if (report) {
            when {
                count > 0 -> showFreezerMessage(
                    context,
                    restoreSummary(context, frozen = frozen, paused = paused),
                )
                // 全都在目标态：给一句反馈，否则用户会以为手势没生效（曾经真的被这么报过）。
                packages.isNotEmpty() -> showFreezerMessage(context, R.string.freezer_restore_intents_noop)
            }
        }
        count
    }

    /**
     * 「按档位收回」的结果文案。
     *
     * 面板内用它渲染提示条（`report = false` 时不弹提示）——面板是全屏浮窗，系统 Toast 会被
     * 它自己盖住，所以在面板里必须由面板自己显示。
     */
    fun restoreSummary(context: Context, frozen: Int, paused: Int): String = when {
        paused == 0 -> context.resources.getQuantityString(
            R.plurals.freezer_refreeze_done, frozen, frozen
        )
        frozen == 0 -> context.resources.getQuantityString(
            R.plurals.freezer_pause_all_done, paused, paused
        )
        else -> context.getString(R.string.freezer_restore_intents_done, frozen, paused)
    }

    suspend fun pauseAll(
        context: Context,
        packages: Set<String>,
        report: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            if (report) {
                showFreezerMessage(context, R.string.freezer_permission_required)
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isActive && setPaused(context, pkg, paused = true)) count++
        }
        if (report) {
            showFreezerMessage(
                context,
                context.resources.getQuantityString(R.plurals.freezer_pause_all_done, count, count),
            )
        }
        count
    }

    suspend fun unpauseAll(
        context: Context,
        packages: Set<String>,
        report: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            if (report) {
                showFreezerMessage(context, R.string.freezer_permission_required)
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            // 只取消挂起，**不动意图表**：「取消暂停」是让它们现在能跑，用户给它们记的档位
            // 仍然是「暂停」，下次按档位收回时还要把暂停还回去。删记录会让它们退回兜底档位
            // （工作模式为冻结时就是图标消失），那正是用户报过的「说好暂停的又被冻结了」。
            if (stateOf(context, pkg).isPaused && setPaused(context, pkg, paused = false)) count++
        }
        if (report) {
            showFreezerMessage(
                context,
                context.resources.getQuantityString(R.plurals.freezer_unpause_all_done, count, count),
            )
        }
        count
    }

    suspend fun unfreezeAll(
        context: Context,
        packages: Set<String>,
        report: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        if (!hasShellAccess()) {
            if (report) {
                showFreezerMessage(context, R.string.freezer_permission_required)
            }
            return@withContext 0
        }
        var count = 0
        for (pkg in packages) {
            if (stateOf(context, pkg).isFrozen && setFrozen(context, pkg, frozen = false)) count++
        }
        if (report) {
            showFreezerMessage(
                context,
                context.resources.getQuantityString(R.plurals.freezer_unfreeze_all_done, count, count),
            )
        }
        count
    }

    private const val TAG = "FreezerOperations"
}
