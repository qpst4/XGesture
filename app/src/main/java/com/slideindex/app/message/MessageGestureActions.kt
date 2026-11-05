package com.slideindex.app.message

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationSbnCache
import com.slideindex.app.notification.NotificationShadePolicy
import com.slideindex.app.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 「阅读消息 / 小窗阅读消息 / 清空通知」三个通用动作的落地点。
 *
 * 这三个动作在卡片（消息提醒）之外触发，没有现成的 [NotificationData]，所以需要自己从
 * 通知栏里挑「最新一条」；挑到之后仍然交给 [MessageActionExecutor] 执行 —— 小窗回落全屏、
 * 目标已在前台改全屏、阅读后返回键这些行为与卡片上的「阅读」逐字节一致。
 */
@Singleton
class MessageGestureActions @Inject constructor(
    private val listenerPort: NotificationListenerPort,
    private val launchPort: NotificationIntentLaunchPort,
    private val actionExecutor: MessageActionExecutor,
    private val overlayPort: MessageOverlayPort,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 打开通知栏里最新一条可点开的通知。
     *
     * @param smallWindow true = 小窗阅读（小窗不可用时由启动端口自行回落全屏）。
     * @return 是否已经受理；没受理时会自己弹一句提示（没有通知使用权 / 没有可读通知）。
     */
    fun readLatest(context: Context, settings: AppSettings, smallWindow: Boolean): Boolean {
        val appContext = context.applicationContext
        if (listenerPort.listenerOrNull() == null) {
            toast(appContext, R.string.gesture_action_notification_access_permission)
            return false
        }
        val sbn = latestReadableSbn(appContext) ?: run {
            toast(appContext, R.string.gesture_action_read_message_empty)
            return false
        }
        // NotificationData.fromSbn 会解码头像/大图，放后台；打开动作回主线程做。
        workerScope.launch {
            val data = NotificationData.fromSbn(appContext, sbn)
            mainHandler.post {
                if (data == null) {
                    toast(appContext, R.string.gesture_action_read_message_empty)
                    return@post
                }
                actionExecutor.execute(
                    context = appContext,
                    data = data,
                    action = if (smallWindow) {
                        MessageAction.ReadInSmallWindow
                    } else {
                        MessageAction.Read
                    },
                    settings = settings,
                    launchPort = launchPort,
                )
                // 已经跳过去了：这条通知的提醒浮层不能继续挂着（与卡片上点「阅读」同义）。
                MessageStyle.entries.forEach { style ->
                    overlayPort.dismissEntriesForKey(style, data.key)
                }
            }
        }
        return true
    }

    /**
     * 清空通知栏：收起全部消息提醒浮层，再移除所有可清除通知。
     *
     * 常驻通知（前台服务 / 不可清除）一律跳过 —— 那是「清空通知」，不是「强行停服务」。
     */
    fun clearNotifications(context: Context): Boolean {
        val appContext = context.applicationContext
        val listener = listenerPort.listenerOrNull()
        if (listener == null) {
            toast(appContext, R.string.gesture_action_notification_access_permission)
            return false
        }
        mainHandler.post {
            // 先收提醒再取消通知：否则每条 cancel 都会回调一次「提醒随通知消失」的收尾。
            overlayPort.dismissAllReminders()
            val active = runCatching { listener.activeNotifications }.getOrNull().orEmpty()
            active.filterNot { NotificationShadePolicy.isOngoing(it) }.forEach { sbn ->
                runCatching { listener.cancelNotification(sbn.key) }
                    .onFailure { Log.w(TAG, "cancel notification failed: ${sbn.key}", it) }
            }
        }
        return true
    }

    /**
     * 最新一条「可点开」的通知。
     *
     * 先看通知栏（活动通知），再看 [NotificationSbnCache]：开了「拦截通知」后提醒一到就会被
     * 从通知栏撤掉，此时活动通知里没有它，只有缓存里还留着可重放的 SBN。
     */
    private fun latestReadableSbn(context: Context): StatusBarNotification? {
        val ownPackage = context.packageName
        val live = runCatching { listenerPort.listenerOrNull()?.activeNotifications }
            .getOrNull()
            .orEmpty()
            .filter { isReadable(it, ownPackage) }
            .maxByOrNull { it.postTime }
        if (live != null) return live
        return NotificationSbnCache.latestActive()?.takeIf { isReadable(it, ownPackage) }
    }

    private fun isReadable(sbn: StatusBarNotification, ownPackage: String): Boolean {
        if (sbn.packageName == ownPackage) return false
        val notification = sbn.notification ?: return false
        // 常驻 / 前台服务 / 不可清除：不是「消息」，点了也打不开。
        if (NotificationShadePolicy.isOngoing(sbn)) return false
        // 分组摘要：点它只会展开分组，不会进会话。
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // 没有点击意图的通知（进度条、纯展示类）打不开。
        if (notification.contentIntent == null) return false
        val category = notification.category
        if (category == Notification.CATEGORY_PROGRESS || category == Notification.CATEGORY_SERVICE) return false
        return true
    }

    private fun toast(context: Context, messageRes: Int) {
        // 调用点既可能是主线程（手势派发），也可能是后台协程收尾，统一回主线程弹。
        mainHandler.post {
            runCatching { Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show() }
        }
    }

    private companion object {
        const val TAG = "MessageGestureActions"
    }
}
