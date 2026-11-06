package com.slideindex.app.di

import android.content.Context
import android.util.Log
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.privilege.ShizukuRequirement
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Singleton
class ShizukuInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clipboardHistoryRepository: ClipboardHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var listenerRegistered = false

    private val binderListener = Shizuku.OnBinderReceivedListener {
        if (TaskManagerUtil.hasShizukuPermission()) {
            TaskManagerUtil.warmUp()
            // binder 刚到手：顺手把最近任务查询的探测跑掉，别让用户第一次打开切换器时等。
            TaskManagerUtil.warmRecentTasksAsync()
            scope.launch(Dispatchers.IO) {
                com.slideindex.app.service.OverlayServiceLifecycle.syncFromSettings(
                    context,
                    settingsRepository,
                    accessibilityRecoverRetries = true
                )
                // Shizuku 可能在开机后很久才起来：此时剪贴板监听不会自己恢复，
                // 会一直空窗到用户手动打开应用。binder 到达时按设置补一次启动。
                // binder 可能刚回来就又掉线（pingBinder 与权限检查之间），不能让它把进程带崩。
                runCatching {
                    clipboardHistoryRepository.syncClipboardMonitoringFromSettings()
                }.onFailure {
                    Log.w(TAG, "sync clipboard monitoring after binder received failed: ${it.message}")
                }
            }
        }
    }

    fun start() {
        // 注意：这里不要放进 init —— 设置流的首个值会让 apply() 立刻去取 Shizuku binder，
        // 而"取 binder"会顺带拉起主进程并让本进程依赖它（覆盖安装后主进程启动慢被杀时，
        // :overlay 会被系统连带杀掉）。收集器改为随 start() 一起开始，由调用方决定时机。
        startCollecting()
        TaskManagerUtil.initialize(context)
        apply(ShizukuRequirement.needsShizuku(settingsRepository.readSnapshot()))
    }

    @Volatile
    private var collecting = false

    private fun startCollecting() {
        if (collecting) return
        collecting = true
        scope.launch {
            settingsRepository.settings
                .map(ShizukuRequirement::needsShizuku)
                .distinctUntilChanged()
                .collect { needed -> apply(needed) }
        }
    }

    private fun apply(needed: Boolean) {
        if (needed) {
            if (!listenerRegistered) {
                Shizuku.addBinderReceivedListenerSticky(binderListener)
                listenerRegistered = true
            }
            if (TaskManagerUtil.hasShizukuPermission()) {
                TaskManagerUtil.warmUp()
                TaskManagerUtil.warmRecentTasksAsync()
            }
        } else {
            if (listenerRegistered) {
                Shizuku.removeBinderReceivedListener(binderListener)
                listenerRegistered = false
            }
            ShizukuUserServiceHost.drop(context)
        }
    }

    private companion object {
        private const val TAG = "ShizukuInitializer"
    }
}
