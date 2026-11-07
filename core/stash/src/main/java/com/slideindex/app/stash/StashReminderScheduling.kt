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

    /** 取消一条提醒。 */
    @Volatile
    var cancel: ((Context, String) -> Unit)? = null
}