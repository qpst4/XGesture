package com.slideindex.app.overlay.quickmenu

import com.slideindex.app.overlay.panels.R
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 定时提醒：输入一句话 + 选几分钟后 → 到点发一条通知。
 *
 * 形状照 [QuickDash 的 QuickRemindersScreen]：**一个标题框 + 一组分钟预设 + 一个按钮**，
 * 不做提醒列表（它自己也没有）。
 *
 * ## 精确闹钟
 *
 * 本仓库清单里**已经声明了 `USE_EXACT_ALARM`**（API 33+ 安装即生效、无需用户授权），所以 33+ 上
 * 直接 `setExactAndAllowWhileIdle` 就能准点。API 31/32 没有这个权限，只有需要用户授权的
 * `SCHEDULE_EXACT_ALARM` —— 那种机器上退化排程并**在界面上给一个"去授权"的入口**，
 * 而不是静默地晚几分钟（这正是用户问的"怎么让它精准"）。
 */

/** 可选的分钟预设（对齐"1-tap"的用法：点一下就走，不用输数字）。 */
private val REMINDER_MINUTES = listOf(5, 10, 30, 60)

@Composable
fun QuickMenuReminderScreen() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableStateOf(REMINDER_MINUTES.first()) }
    var message by remember { mutableStateOf<String?>(null) }
    val needExactPermission = remember(context) { !QuickMenuReminderAlarm.canScheduleExact(context) }
    // 资源读取留在**组合期**（stringResource 是 configuration-aware 的）：
    // 在 onClick 回调里用 LocalContext.current 的 getString 会拿到过期值，lint 的
    // LocalContextGetResourceValueCall 会直接判错、中断构建。
    val notifyTitle = stringResource(R.string.quick_menu_reminder_notify_title)
    val setDoneTemplate = stringResource(R.string.quick_menu_reminder_set_done)
    val setFailed = stringResource(R.string.quick_menu_reminder_set_failed)

    ToolScaffold {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.quick_menu_reminder_input)) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            REMINDER_MINUTES.forEach { minutes ->
                // 用 FilterChip（与倒计时页的分钟预设同款）：它自带**选中态**（填充色 + 边框）。
                // 之前用 OutlinedButton 只改文字颜色 ✗ —— 用户反馈"看不出哪个选中了"，那不是错觉 ✗
                FilterChip(
                    selected = minutes == selectedMinutes,
                    onClick = { selectedMinutes = minutes },
                    label = { Text(stringResource(R.string.quick_menu_reminder_minutes, minutes)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val text = title.trim().ifEmpty { notifyTitle }
                val ok = QuickMenuReminderAlarm.schedule(context, text, selectedMinutes * 60_000L)
                message = if (ok) {
                    setDoneTemplate.format("$selectedMinutes")
                } else {
                    setFailed
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank(),
        ) {
            Text(stringResource(R.string.quick_menu_reminder_set))
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (needExactPermission) {
            Spacer(modifier = Modifier.height(8.dp))
            // 不静默退化：明确告诉用户"可能晚几分钟"，并给一键去授权的入口。
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.quick_menu_reminder_need_exact))
            }
        }
    }
}

/**
 * 定时提醒的排程：**能精确就精确，不能才退化**，并把"能不能精确"暴露给界面。
 *
 * 不复用 [com.slideindex.app.stash.StashReminderScheduler.setAlarmDegradingToInexact]：那个实现是
 * **永远退化**（它服务的是"几分钟后提醒一下"这种可容忍漂移的场景）。提醒要准点，所以这里先试精确。
 */
object QuickMenuReminderAlarm {

    private const val TAG = "QuickMenuReminder"

    /** 与倒计时（7301）分开的 request code，互不干扰。 */
    private const val REQUEST_CODE = 7302

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return false
        // API 33+ 声明了 USE_EXACT_ALARM 就是 true；31/32 看用户有没有在系统设置里授权。
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, title: String, afterMs: Long): Boolean {
        if (afterMs <= 0L) return false
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return false
        val pending = pendingIntent(appContext, title, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        val triggerAt = System.currentTimeMillis() + afterMs
        return runCatching {
            if (canScheduleExact(appContext)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // 没有精确权限时退化（可能晚几分钟），但界面上已经明确告知并提供授权入口。
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        }.onFailure { Log.w(TAG, "schedule failed, triggerAt=$triggerAt", it) }.getOrDefault(false)
    }

    private fun pendingIntent(context: Context, title: String, flag: Int): PendingIntent? = runCatching {
        val intent = Intent(context, QuickMenuReminderAlarmReceiver::class.java).apply {
            action = QuickMenuReminderAlarmReceiver.ACTION_REMINDER
            putExtra(QuickMenuReminderAlarmReceiver.EXTRA_TITLE, title)
        }
        PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }.getOrNull()
}

/**
 * 定时提醒到点：发一条通知（标题就是用户输入的提醒内容）。
 *
 * 通知写法对齐 [QuickMenuTimerAlarmReceiver]：先查 `POST_NOTIFICATIONS`（未授权就留日志、不假装发过）、
 * 建渠道、`notify` 失败要留日志**不吞**。
 */
class QuickMenuReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val appContext = context.applicationContext
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { appContext.getString(R.string.quick_menu_reminder_notify_title) }
        Log.i(TAG, "onReceive: 定时提醒到点")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS 未授权 → 不发通知")
            return
        }
        ensureChannel(appContext)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(appContext.getString(R.string.quick_menu_reminder_notify_title))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFY_ID, notification)
        } catch (error: Throwable) {
            Log.e(TAG, "notify 失败（渠道/权限/ROM 拦截？）", error)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.quick_menu_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }

    companion object {
        private const val TAG = "QuickMenuReminder"

        const val ACTION_REMINDER = "com.slideindex.app.action.QUICK_MENU_REMINDER"
        const val EXTRA_TITLE = "quick_menu_reminder_title"

        private const val CHANNEL_ID = "quick_menu_reminder"
        private const val NOTIFY_ID = 7320
    }
}
