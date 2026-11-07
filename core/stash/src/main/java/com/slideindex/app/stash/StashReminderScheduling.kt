package com.slideindex.app.stash

import android.content.Context

/**
 * 提醒调度的接线点：`:app` 的 `StashReminderScheduler` 依赖通知与 trampoline，
 * 数据层（`:core:stash`）只负责调用它。
 */
object StashReminderScheduling {

    /** 排定一条提醒。 */
    @Volatile
    var schedule: ((Context, String, Long, String) -> Unit)? = null

    /** 批量重排提醒（`reminders` 为 id → 到点时刻）。 */
    var rescheduleAll: ((Context, Map<String, Long>, (String) -> String) -> Unit)? = null

    /** 取消一条提醒。 */
    @Volatile
    var cancel: ((Context, String) -> Unit)? = null
}

private const val NOTIFY_ID_BASE = 24_100

/** 通知 ID（按条目 id 稳定派生）。 */
fun notifyIdOf(entryId: String): Int = NOTIFY_ID_BASE + entryId.hashCode()
