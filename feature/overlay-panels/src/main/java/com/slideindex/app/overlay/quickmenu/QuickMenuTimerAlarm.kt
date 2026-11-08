package com.slideindex.app.overlay.quickmenu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 倒计时的"到点"闹钟。
 *
 * ## 为什么要独立一套（这是修 bug 换来的）
 *
 * 早先为了不新建通知，这里借用了既有的「N 分钟后提醒」（`RemindAlarmScheduler`）。那条路的
 * 排程键是**分钟数**，而且只在开始时排、从不撤销 —— 于是**暂停/复位之后到点照样响**
 * （用户反馈的现象），而且"暂停在 2:30 再继续"时闹钟仍在最初那个时刻响。
 *
 * 现在：专属 request code + 自己的接收器，暂停/复位/结束都能精确撤销，继续时按**剩余时长**重排。
 *
 * ## 时钟口径
 *
 * 复用 [StashReminderScheduler.setAlarmDegradingToInexact]（RTC 口径，"精确优先、被系统拒绝就退化
 * 成不精确"），因此这里把剩余时长换算成 `System.currentTimeMillis() + remainingMs`。
 * 对分钟级的短计时来说与 `elapsedRealtime` 等价，换来的是**复用仓库里已经修对的那份排程实现**
 * （它记录了"精确那条一抛异常会把退化分支整段跳过"这个坑）。
 *
 * ⚠️ 本仓库没有声明 `SCHEDULE_EXACT_ALARM`（与既有提醒一致），所以没有该权限时闹钟会被系统
 * 推迟到合适时机（可能晚一点响），而不是不响。
 */
internal object QuickMenuTimerAlarm {

    private const val TAG = "QuickMenuTimerAlarm"

    /** 专属 request code：与提醒（REQUEST_CODE_BASE=4000 起）及收纳提醒互不干扰。 */
    private const val REQUEST_CODE = 7301

    /** 按剩余时长排一条到点闹钟；[totalMs] 随闹钟带过去，供接收器写历史。 */
    fun schedule(context: Context, remainingMs: Long, totalMs: Long) {
        if (remainingMs <= 0L || totalMs <= 0L) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(appContext, totalMs, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        val triggerAt = System.currentTimeMillis() + remainingMs
        runCatching {
            com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host?.setReminderAlarm(alarmManager, triggerAt, pending)
        }.onFailure { Log.w(TAG, "schedule failed, triggerAt=$triggerAt", it) }
    }

    /** 撤销到点闹钟（暂停 / 复位 / 提前结束 / 已到点）。没有已排的闹钟时什么也不做。 */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        // FLAG_NO_CREATE：不存在就返回 null，避免"为了取消而顺手建出一个"。
        val pending = pendingIntent(appContext, 0L, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            alarmManager.cancel(pending)
            pending.cancel()
        }.onFailure { Log.w(TAG, "cancel failed", it) }
    }

    /**
     * @param totalMs 只作为 extra；[PendingIntent] 的匹配走 `Intent.filterEquals`，**不看 extras**，
     *   所以取消时传 0 也能匹配到同一条。
     */
    private fun pendingIntent(context: Context, totalMs: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, QuickMenuTimerAlarmReceiver::class.java).apply {
            action = QuickMenuTimerAlarmReceiver.ACTION_TIMER_DONE
            putExtra(QuickMenuTimerAlarmReceiver.EXTRA_TOTAL_MS, totalMs)
        }
        return runCatching {
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                flag or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
    }
}
