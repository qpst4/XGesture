package com.slideindex.app.message

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.slideindex.app.R
import com.slideindex.app.overlay.CNoticeOverlayWindow
import com.slideindex.app.overlay.DanmakuOverlayWindow
import com.slideindex.app.overlay.EdgeCardOverlayWindow
import com.slideindex.app.overlay.FloatIconOverlayWindow
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.SideBubbleOverlayWindow
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppMessageOverlayPort @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val cNoticeHistoryLoader: CNoticeHistoryLoader,
) : MessageOverlayPort {
    private val settingsWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 主线程 Handler。
     *
     * ⚠️ 必须声明在 `init` **之前**：`init` 里启动的协程会立刻 collect 设置流，
     * 并在 IO 线程上调用 `mainHandler.post`。Kotlin 按声明顺序初始化属性，
     * 一旦本行排在 `init` 之后，构造函数执行到 `init` 时它还是 null ——
     * 冷启动（IO 线程空闲 + 设置流已有值 + 构造函数被抢占）三者同时成立时会 NPE
     * 直接崩进程（真机 2026-11-09 14:41:54 已发生一次，栈在 AppMessageOverlayPort.kt:75）。
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        CNoticeOverlayWindow.historyLoader = cNoticeHistoryLoader
        CNoticeOverlayWindow.placementPersistHandler = { edge, yFraction ->
            settingsWriteScope.launch {
                settingsRepository.setMessageCNoticeHorizontalEdge(edge.id)
                settingsRepository.setMessageCNoticeYFraction(yFraction)
            }
        }
        // 设置页改了贴边卡参数（行数/不透明度/边距）后，让已在屏上的卡片立刻跟着变，
        // 不必等下一条通知。
        settingsWriteScope.launch {
            settingsRepository.messageReminderSettings.collect { settings ->
                mainHandler.post {
                    EdgeCardOverlayWindow.updateWindowPlacement(appContext, settings)
                }
            }
        }
    }
    private var unlockConfirmationHost: OverlayComposeDialogHost? = null
    private var unlockBringToFrontRunnable: Runnable? = null
    private var unlockAutoDismissRunnable: Runnable? = null

    private fun cancelUnlockConfirmationCallbacks() {
        unlockBringToFrontRunnable?.let { mainHandler.removeCallbacks(it) }
        unlockAutoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        unlockBringToFrontRunnable = null
        unlockAutoDismissRunnable = null
    }

    override fun containsNotification(style: MessageStyle, data: NotificationData): Boolean =
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.containsNotification(data)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.containsNotification(data)
            MessageStyle.CNotice -> CNoticeOverlayWindow.containsNotification(data)
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.containsNotification(data)
            else -> false
        }

    override fun containsCNoticeConversation(conversationSourceKey: String): Boolean =
        CNoticeOverlayWindow.containsConversation(conversationSourceKey)

    override fun refreshCNoticeConversationIcon(conversationSourceKey: String, icon: android.graphics.Bitmap) {
        CNoticeOverlayWindow.refreshConversationIcon(conversationSourceKey, icon)
    }

    override fun dismissEntry(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.CNotice -> CNoticeOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.dismissEntry(key, postTime)
            else -> Unit
        }
    }

    override fun dismissEntriesForKey(style: MessageStyle, key: String) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntriesForKey(key)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntriesForKey(key)
            MessageStyle.CNotice -> CNoticeOverlayWindow.dismissEntriesForKey(key)
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.dismissEntriesForKey(key)
            else -> Unit
        }
    }

    override fun reconcileCNoticeAfterRemoval(
        removedNotificationKey: String,
        removedConversationKey: String?,
        activeConversationKeys: Set<String>,
    ) {
        CNoticeOverlayWindow.reconcileAfterRemoval(
            removedNotificationKey = removedNotificationKey,
            removedConversationKey = removedConversationKey,
            activeConversationKeys = activeConversationKeys,
        )
    }

    override fun resumeAutoDismiss(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.resumeAutoDismiss(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.resumeAutoDismiss(key, postTime)
            MessageStyle.CNotice -> CNoticeOverlayWindow.resumeAutoDismiss(key, postTime)
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.resumeAutoDismiss(key, postTime)
            else -> Unit
        }
    }

    override fun pauseAutoDismiss(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.pauseAutoDismiss(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.pauseAutoDismiss(key, postTime)
            MessageStyle.CNotice -> CNoticeOverlayWindow.pauseAutoDismiss(key, postTime)
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.pauseAutoDismiss(key, postTime)
            else -> Unit
        }
    }

    override fun dismissImmediate(style: MessageStyle?) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissImmediate()
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissImmediate()
            MessageStyle.CNotice -> CNoticeOverlayWindow.dismissImmediate()
            MessageStyle.EdgeCard -> EdgeCardOverlayWindow.dismissImmediate()
            null -> {
                SideBubbleOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
                CNoticeOverlayWindow.dismissImmediate()
                EdgeCardOverlayWindow.dismissImmediate()
            }
            else -> Unit
        }
    }

    override fun snapshotDisplayedKeys(): Set<String> =
        buildSet {
            addAll(SideBubbleOverlayWindow.snapshotDisplayedKeys())
            addAll(FloatIconOverlayWindow.snapshotDisplayedKeys())
            addAll(CNoticeOverlayWindow.snapshotDisplayedKeys())
            addAll(EdgeCardOverlayWindow.snapshotDisplayedKeys())
        }

    override fun dismissAllReminders() {
        SideBubbleOverlayWindow.dismiss()
        FloatIconOverlayWindow.dismiss()
        CNoticeOverlayWindow.dismiss()
        EdgeCardOverlayWindow.dismiss()
        DanmakuOverlayWindow.detach()
    }

    override fun replayFloatIconAfterUnlock() {
        FloatIconOverlayWindow.replayAfterUnlock()
    }

    override fun dismissSameSourceReminders(sourceKey: String) {
        SideBubbleOverlayWindow.dismissSameSource(sourceKey)
        FloatIconOverlayWindow.dismissSameSource(sourceKey)
        CNoticeOverlayWindow.dismissSameSource(sourceKey)
        EdgeCardOverlayWindow.dismissSameSource(sourceKey)
    }

    override fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> =
        buildSet {
            addAll(SideBubbleOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
            addAll(FloatIconOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
            addAll(CNoticeOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
            addAll(EdgeCardOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
        }

    override fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
        showDanmaku: Boolean,
    ) {
        val danmakuTheme = plan.danmakuTheme
        if (showDanmaku && plan.showDanmaku && danmakuTheme != null) {
            DanmakuOverlayWindow.show(
                context = context,
                data = plan.data.latestMessageForDanmaku(),
                theme = danmakuTheme,
                opacity = plan.settings.danmakuOpacity,
                maxLines = plan.settings.danmakuMaxLines,
                speedLevel = plan.settings.danmakuSpeedLevel,
                fontSizeLevel = plan.settings.sideBubbleFontSizeLevel
            )
        }
        if (plan.showFloatIcon) {
            FloatIconOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss
            )
        }
        if (plan.showSideBubble && plan.sideTheme != null) {
            SideBubbleOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss
            )
        }
        if (plan.showCNotice) {
            CNoticeOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss
            )
        }
        if (plan.showEdgeCard) {
            EdgeCardOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss,
            )
        }
        if (plan.showDanmaku) {
            DanmakuOverlayWindow.bringToFront()
        }
    }

    override fun showUnlockConfirmation(
        context: Context,
        data: NotificationData,
        autoDismissSeconds: Int,
        onConfirm: (alwaysAllow: Boolean) -> Unit,
        onDismiss: () -> Unit
    ) {
        cancelUnlockConfirmationCallbacks()
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(data.packageName, 0)
            ).toString()
        }.getOrDefault(data.packageName)
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: context
        val host = unlockConfirmationHost ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = false
        ).also {
            unlockConfirmationHost = it
        }
        host.show(
            onDismiss = {
                cancelUnlockConfirmationCallbacks()
                onDismiss()
            }
        ) {
            var alwaysAllow by remember { mutableStateOf(false) }
            val badgeBackground = MaterialTheme.colorScheme.surfaceContainer
            val notificationTitle = data.title
                .takeIf { it.isNotBlank() && !it.equals(appLabel, ignoreCase = true) }
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .width(300.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        data.largeIcon?.let { icon ->
                            Box {
                                Image(
                                    bitmap = icon.asImageBitmap(),
                                    contentDescription = appLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                                val appIcon = data.appIcon
                                appIcon?.let {
                                    Image(
                                        bitmap = appIcon.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .align(Alignment.BottomEnd)
                                            .drawBehind {
                                                drawCircle(
                                                    color = badgeBackground,
                                                    radius = size.minDimension / 2f + 2.dp.toPx()
                                                )
                                            }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                        }
                        data.appIcon?.let { appIcon ->
                            if (data.largeIcon != null) return@let
                            Image(
                                bitmap = appIcon.asImageBitmap(),
                                contentDescription = appLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                        }
                        Column {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.message_reminder_unlock_confirm_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Text(
                        text = listOfNotNull(
                            notificationTitle,
                            data.content.takeIf { it.isNotBlank() }
                        ).joinToString("\n"),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { alwaysAllow = !alwaysAllow }
                    ) {
                        Switch(
                            checked = alwaysAllow,
                            onCheckedChange = { alwaysAllow = it }
                        )
                        Text(
                            text = stringResource(R.string.message_reminder_unlock_confirm_always_allow),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { host.dismiss() }) {
                            Text(stringResource(R.string.message_reminder_unlock_confirm_cancel))
                        }
                        Button(onClick = {
                            host.dismiss()
                            onConfirm(alwaysAllow)
                        }) {
                            Text(stringResource(R.string.message_reminder_unlock_confirm_open))
                        }
                    }
                }
            }
        }
        val bringToFrontRunnable = Runnable { host.bringToFront() }
        unlockBringToFrontRunnable = bringToFrontRunnable
        mainHandler.postDelayed(bringToFrontRunnable, 250L)
        if (autoDismissSeconds > 0) {
            val autoDismissRunnable = Runnable {
                if (host.isShowing) host.dismiss()
            }
            unlockAutoDismissRunnable = autoDismissRunnable
            mainHandler.postDelayed(
                autoDismissRunnable,
                autoDismissSeconds.coerceIn(1, 30) * 1000L
            )
        }
    }

    override fun detachDanmaku() {
        DanmakuOverlayWindow.detach()
    }
}
