package com.slideindex.app.overlay.quickmenu

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.quickmenu.StopwatchLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 倒计时到点：响铃通知 + 写历史。
 *
 * ## 为什么历史写在这里而不是面板里
 *
 * 闹钟**一定会**在到点那一刻响（面板开着、关着、甚至 App 进程已经被杀都一样），所以把它作为
 * 唯一的历史写入口，就不会出现"面板开着记一次、闹钟又记一次"的重复，也不会出现"面板早被销毁
 * 所以根本没记"的漏记。面板里那条滴答只负责把界面归零。
 *
 * 通知的写法对齐仓库既有的 [com.slideindex.app.stash.StashReminderReceiver]：先查
 * `POST_NOTIFICATIONS`（未授权就只记历史、不发通知）、建渠道、`notify` 失败要留日志**不吞**。
 */
class QuickMenuTimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_DONE) return
        val appContext = context.applicationContext
        val totalMs = intent.getLongExtra(EXTRA_TOTAL_MS, 0L)
        Log.i(TAG, "onReceive: 倒计时到点 totalMs=$totalMs")

        notifyFinished(appContext, totalMs)
        recordHistory(appContext, totalMs)
    }

    private fun recordHistory(context: Context, totalMs: Long) {
        if (totalMs <= 0L) return
        // 广播的 onReceive 一返回进程就可能被杀，所以用 goAsync 把这次写入带出 onReceive。
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                QuickMenuHistoryStore.add(
                    context,
                    QuickMenuHistoryEntry(
                        kind = QuickMenuHistoryEntry.Kind.TIMER,
                        totalMs = totalMs,
                        atEpochMs = System.currentTimeMillis(),
                    ),
                )
            } catch (error: Throwable) {
                Log.w(TAG, "写计时历史失败", error)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private fun notifyFinished(context: Context, totalMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 没授权就只留历史，不静默假装发过了。
            Log.w(TAG, "POST_NOTIFICATIONS 未授权 → 不发通知（历史照记）")
            return
        }
        ensureChannel(context)
        val duration = if (totalMs > 0L) StopwatchLogic.format(totalMs) else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.quick_menu_timer_notify_title))
            .setContentText(context.getString(R.string.quick_menu_timer_notify_text, duration))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        } catch (error: Throwable) {
            Log.e(TAG, "notify 失败（渠道/权限/ROM 拦截？）", error)
        }
    }

    /** 点通知回到应用（面板本身靠手势唤出，不能从通知直接拉起覆盖层）。 */
    private fun openAppIntent(context: Context): PendingIntent? = runCatching {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        PendingIntent.getActivity(
            context,
            0,
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }.getOrNull()

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.quick_menu_timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.quick_menu_timer_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 150, 200)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_TIMER_DONE = "com.slideindex.app.action.QUICK_MENU_TIMER_DONE"
        const val EXTRA_TOTAL_MS = "extra_total_ms"

        private const val TAG = "QuickMenuTimerAlarm"
        private const val CHANNEL_ID = "quick_menu_timer_done"
        private const val NOTIFY_ID = 7301
    }
}
