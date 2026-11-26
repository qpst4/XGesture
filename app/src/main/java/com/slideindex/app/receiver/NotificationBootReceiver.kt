package com.slideindex.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.service.OverlayServiceLifecycle
import com.slideindex.app.util.MediaSessionHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On boot, user unlock, or package replace:
 * - rebind notification listener for persisted filter rules;
 * - start overlay service and nudge accessibility binding so edge gestures work without opening the app.
 */
class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    MediaSessionHelper.ensureNotificationListenerConnected(appContext)
                    Log.d(TAG, "Requested notification listener rebind after $action")
                }.onFailure { error ->
                    Log.w(TAG, "Failed to request notification listener rebind after $action", error)
                }
                val deps = EntryPointAccessors.fromApplication(
                    appContext,
                    AppGraphEntryPoint::class.java,
                ).dependencies()
                OverlayServiceLifecycle.syncFromSettings(appContext, deps.settingsRepository)
                com.slideindex.app.service.OverlayWatchdogJobService.schedule(appContext)
                if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    val settings = deps.settingsRepository.readSnapshot()
                    // 覆盖安装后无障碍大概率被系统解绑，这里要重试；但**不能**在广播里
                    // 长等（超过广播时限 = ANR，实测每次 install -r 都会弹）。
                    // 给整段重试一个 8s 上限：正常设备上 0.8+2+5s 的重试刚好能跑完，
                    // 跑不完就结束广播，交给看门狗 / 下次启动继续恢复。
                    val recovered = withTimeoutOrNull(REBIND_BUDGET_MS) {
                        OverlayServiceLifecycle.recoverAccessibilityBindingWithRetries(
                            appContext,
                            settings,
                        )
                    }
                    if (recovered == null) {
                        Log.w(TAG, "Accessibility rebind exceeded ${'$'}REBIND_BUDGET_MS ms, deferred")
                    }
                }
                Log.i(TAG, "Gesture boot recovery completed after $action")
            } catch (error: Exception) {
                Log.w(TAG, "Gesture boot recovery failed after $action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotificationBootReceiver"
        const val REBIND_BUDGET_MS = 8_000L
    }
}
