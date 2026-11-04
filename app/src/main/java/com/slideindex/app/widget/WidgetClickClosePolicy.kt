package com.slideindex.app.widget

import android.app.PendingIntent

/**
 * 小组件被点击后「要不要收起面板」的策略。
 *
 * 背景：小组件点击由 [SlideIndexAppWidgetHost] 拦截，面板收起必须发生在
 * PendingIntent **发送之后**（先发再收），否则 `RemoteViews` 的 launch options
 * 会算在已脱离窗口的视图上；这里只回答「这次点击该不该收」。
 *
 * 三档语义对齐同类产品的做法：
 * - [MODE_ACTIVITY_ONLY]：只有点击会启动外部 Activity 时才收（默认，旧行为）；
 * - [MODE_ALWAYS]：任何点击都收（含计算器/音乐这类原地广播）；
 * - [MODE_NEVER]：从不自动收，用户自己返回。
 */
object WidgetClickClosePolicy {
  const val MODE_ACTIVITY_ONLY = 0
  const val MODE_ALWAYS = 1
  const val MODE_NEVER = 2

  @Volatile
  var mode: Int = MODE_ACTIVITY_ONLY

  fun shouldClose(pendingIntent: PendingIntent): Boolean = when (mode) {
    MODE_ALWAYS -> true
    MODE_NEVER -> false
    else -> pendingIntent.isActivity
  }

  fun modeFromSetting(value: Int): Int = when (value) {
    MODE_ALWAYS -> MODE_ALWAYS
    MODE_NEVER -> MODE_NEVER
    else -> MODE_ACTIVITY_ONLY
  }
}
