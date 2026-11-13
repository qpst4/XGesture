package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.slideindex.app.service.MediaNotificationListener

/**
 * 媒体会话相关的纯判定工具：监听权限、包过滤、播放状态评分。
 *
 * 「当前媒体是谁」的唯一权威来源是 [MediaSessionTracker]（系统 MediaSession 栈的实时快照）。
 * 这里不提供 `dumpsys` 兜底查询：文本 dump 随 ROM / Android 版本漂移，而且在部分 ROM 上
 * `dumpsys audio` 的 players 段只列 uid/pid 不带包名，最终只能匹配到音频事件日志里
 * 「最近谁开过播放器」，答不出「现在是谁」。那条兜底曾以同样的 500ms 节奏和 tracker
 * 抢写悬浮面板的媒体字段，表现就是音量图标 ↔ App 图标闪烁
 * （见 `OhoQuickToolsPanelState` 的媒体状态写入口）。
 */
object MediaSessionHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, MediaNotificationListener::class.java)
        return enabled.contains(component.flattenToString(), ignoreCase = true)
    }

    fun ensureNotificationListenerConnected(context: Context) {
        if (!isNotificationListenerEnabled(context)) return
        if (MediaNotificationListener.instance != null) return
        val component = ComponentName(context, MediaNotificationListener::class.java)
        runCatching {
            NotificationListenerService.requestRebind(component)
        }
        runCatching {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /**
     * Notification-access settings for this app.
     *
     * Android 11+ (minSdk is 31) exposes a per-listener detail screen, so the user lands on our own
     * switch instead of the all-apps list. Some OEM ROMs do not implement that action, so fall back
     * to the list page whenever it cannot be resolved.
     */
    fun notificationListenerSettingsIntent(context: Context): Intent {
        val component = ComponentName(context, MediaNotificationListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(detail, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return detail
        }
        return listNotificationListenerSettingsIntent()
    }

    /** All-apps notification-access list; kept as the fallback for ROMs without the detail screen. */
    fun listNotificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Opens notification access without ever throwing: a [android.content.ActivityNotFoundException]
     * from a half-implemented OEM Settings activity degrades to the list page. Safe to call from the
     * long-lived `:overlay` process, where an uncaught exception would take down the overlay.
     */
    fun openNotificationListenerSettings(context: Context) {
        val detail = notificationListenerSettingsIntent(context)
        if (runCatching { context.startActivity(detail) }.isSuccess) return
        runCatching { context.startActivity(listNotificationListenerSettingsIntent()) }
    }

    fun isIgnoredMediaPackage(pkg: String, selfPackage: String): Boolean =
        pkg == selfPackage ||
            pkg == "com.android.systemui" ||
            pkg == "android" ||
            pkg.startsWith("com.android.")

    fun playbackScore(state: Int): Int = when (state) {
        PlaybackState.STATE_PLAYING -> 300
        PlaybackState.STATE_BUFFERING -> 280
        PlaybackState.STATE_PAUSED -> 200
        PlaybackState.STATE_CONNECTING -> 150
        PlaybackState.STATE_STOPPED -> 80
        else -> 0
    }

    fun isPlayingState(state: Int): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
}
