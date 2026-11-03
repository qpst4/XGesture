package com.slideindex.app.message

import android.content.Context
import android.util.Log
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationRemoteReply
import com.slideindex.app.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageActionExecutor @Inject constructor(
    private val listenerPort: NotificationListenerPort,
    private val foregroundPort: MessageForegroundPort,
    private val environmentPort: MessageEnvironmentPort,
    private val replyPort: MessageReplyPort,
    private val returnPort: MessageReturnPort,
) {
    fun execute(
        context: Context,
        data: NotificationData,
        action: MessageAction,
        settings: AppSettings,
        launchPort: NotificationIntentLaunchPort,
        onQuickReplySent: (() -> Unit)? = null,
        onQuickReplyCancelled: (() -> Unit)? = null,
    ) {
        when (action) {
            MessageAction.Read -> openNotification(
                context,
                data,
                settings,
                launchPort,
                smallWindowRead = false,
            )
            MessageAction.ReadInSmallWindow -> {
                val foregroundPackage = foregroundPort.foregroundPackage()
                if (foregroundPackage != null && foregroundPackage == data.packageName) {
                    openNotification(
                        context,
                        data,
                        settings,
                        launchPort,
                        smallWindowRead = false,
                    )
                } else {
                    openNotificationInSmallWindow(context, data, settings, launchPort)
                }
            }
            MessageAction.QuickReply,
            MessageAction.QuickReplyAndIgnore,
            MessageAction.QuickReplyAndRemove,
            -> {
                if (!data.hasDirectReply) {
                    Log.w(TAG, "Quick reply unavailable for ${data.packageName}")
                    replyPort.showQuickReplyUnavailable(context, data)
                    return
                }
                replyPort.showQuickReply(
                    context,
                    data,
                    onSent = { onQuickReplySent?.invoke() },
                    onCancelled = { onQuickReplyCancelled?.invoke() },
                )
            }
            MessageAction.Ignore -> Unit
            MessageAction.IgnoreAndRemove -> cancelNotification(data.key)
            MessageAction.IgnoreAll,
            MessageAction.IgnoreAndRemoveAll,
            MessageAction.IgnoreSameSource,
            MessageAction.IgnoreSameSourceAndRemove,
            -> Unit
            MessageAction.Dnd5Min -> MessageNotificationFilter.applyDnd(data.packageName, DND_DURATION_MS)
        }
    }

    fun sendDirectReply(
        context: Context,
        data: NotificationData,
        text: String,
    ): Boolean = NotificationRemoteReply.sendReply(context, data.key, data.postTime, text)

    fun cancelNotification(key: String): Boolean {
        val listener = listenerPort.listenerOrNull() ?: return false
        return runCatching {
            listener.cancelNotification(key)
            true
        }.getOrDefault(false)
    }

    private fun openNotification(
        context: Context,
        data: NotificationData,
        settings: AppSettings,
        launchPort: NotificationIntentLaunchPort,
        smallWindowRead: Boolean,
    ) {
        // 来源应用必须在打开前取：打开后前台已经是目标应用。这里取实时前台，不用缓存。
        val sourcePackage = foregroundPort.liveForegroundPackage()
        val opened = launchPort.open(context, data)
        if (!opened) {
            Log.w(TAG, "Failed to open notification for ${data.packageName}")
            return
        }
        armReturnChip(context, sourcePackage, data, settings, smallWindowRead)
    }

    private fun openNotificationInSmallWindow(
        context: Context,
        data: NotificationData,
        settings: AppSettings,
        launchPort: NotificationIntentLaunchPort,
    ) {
        val sourcePackage = foregroundPort.liveForegroundPackage()
        val opened = launchPort.openInSmallWindow(context, data, settings)
        if (!opened) {
            Log.w(TAG, "Failed to open notification in small window for ${data.packageName}")
            return
        }
        // 小窗真开出来时来源应用仍可见，胶囊的显示判定会把它挡掉；
        // 小窗失败退化成全屏时来源不可见，照常显示。
        armReturnChip(context, sourcePackage, data, settings, smallWindowRead = true)
    }

    private fun armReturnChip(
        context: Context,
        sourcePackage: String?,
        data: NotificationData,
        settings: AppSettings,
        smallWindowRead: Boolean,
    ) {
        val messageSettings = settings.messageReminderSettings
        if (!messageSettings.returnChipEnabled) return
        // 锁屏/解锁后打开的路径没有「阅读前应用」，不显示返回胶囊。
        if (environmentPort.isScreenLocked(context)) return
        returnPort.arm(
            context = context,
            sourcePackage = sourcePackage,
            targetPackage = data.packageName,
            settings = messageSettings,
            smallWindowRead = smallWindowRead,
        )
    }

    companion object {
        private const val TAG = "MessageActionExecutor"
        private const val DND_DURATION_MS = 5 * 60 * 1000L
    }
}
