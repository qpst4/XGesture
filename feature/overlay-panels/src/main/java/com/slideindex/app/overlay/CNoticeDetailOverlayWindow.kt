package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.NotificationMessage
import com.slideindex.app.message.NotificationMessagingHistory
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.MiuixIcons

object CNoticeDetailOverlayWindow {
    private const val TAG = "CNoticeDetail"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeViewRef = java.lang.ref.WeakReference<ComposeView>(null)
    private var composeView: ComposeView?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var backHandlerRef = java.lang.ref.WeakReference<OverlayViewBackHandler>(null)
    private var backHandler: OverlayViewBackHandler?
        get() = backHandlerRef.get()
        set(value) {
            backHandlerRef = java.lang.ref.WeakReference(value)
        }
    private var activeConversationKey: String? = null
    private val messageHistoryState = mutableStateListOf<NotificationMessage>()
    private var latestDataState = mutableStateOf<NotificationData?>(null)
    private var headerColorState = mutableStateOf(Color.Gray)
    private var headerTextColorState = mutableStateOf(Color.White)
    private var onOpenAppHandler: (() -> Unit)? = null
    private var onMarkReadHandler: (() -> Unit)? = null
    private var onSendReplyHandler: ((String) -> Boolean)? = null
    private var onDismissHandler: (() -> Unit)? = null

    fun show(
        context: Context,
        conversationSourceKey: String,
        latestData: NotificationData,
        messages: SnapshotStateList<NotificationMessage>,
        headerColor: Color,
        headerTextColor: Color = Color.White,
        onOpenApp: () -> Unit,
        onMarkRead: () -> Unit,
        onSendReply: (String) -> Boolean,
        onDismiss: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                show(
                    context,
                    conversationSourceKey,
                    latestData,
                    messages,
                    headerColor,
                    headerTextColor,
                    onOpenApp,
                    onMarkRead,
                    onSendReply,
                    onDismiss,
                )
            }
            return
        }

        activeConversationKey = conversationSourceKey
        latestDataState.value = latestData
        headerColorState.value = headerColor
        headerTextColorState.value = headerTextColor
        onOpenAppHandler = onOpenApp
        onMarkReadHandler = onMarkRead
        onSendReplyHandler = onSendReply
        onDismissHandler = onDismiss
        messageHistoryState.clear()
        messageHistoryState.addAll(messages)

        if (composeView != null) {
            if (activeConversationKey == conversationSourceKey) {
                refreshIfShowing(conversationSourceKey, latestData, messages)
                return
            }
            dismiss()
        }

        val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext
            ?.invoke()
            ?: run {
                Log.w(TAG, "overlay permission not granted")
                return
            }
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dialogOwner = OverlayComposeOwner()
        val completeDismiss: () -> Unit = {
            val handler = onDismissHandler
            dismiss()
            handler?.invoke()
        }
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val data = latestDataState.value ?: latestData
                CNoticeDetailContent(
                    data = data,
                    messages = messageHistoryState,
                    headerColor = headerColorState.value,
                    headerTextColor = headerTextColorState.value,
                    onClose = completeDismiss,
                    onOpenApp = {
                        val handler = onOpenAppHandler
                        dismiss()
                        handler?.invoke()
                    },
                    onMarkRead = {
                        val handler = onMarkReadHandler
                        dismiss()
                        handler?.invoke()
                    },
                    onSendReply = { text ->
                        val handler = onSendReplyHandler
                        handler?.invoke(text) ?: false
                    },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        backHandler = OverlayViewBackHandler(view, completeDismiss).also { it.attach() }
        view.requestFocus()
    }

    fun refreshIfShowing(
        conversationKey: String,
        latestData: NotificationData,
        messages: List<NotificationMessage>,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshIfShowing(conversationKey, latestData, messages) }
            return
        }
        if (activeConversationKey != conversationKey || composeView == null) return
        latestDataState.value = latestData
        messageHistoryState.clear()
        messageHistoryState.addAll(messages)
    }

    fun dismiss() {
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.closeCNoticePanel?.invoke()
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissLegacyWindow() }
            return
        }
        dismissLegacyWindow()
    }

    private fun dismissLegacyWindow() {
        hideIme(composeView)
        backHandler?.detach()
        backHandler = null
        val wm = windowManager
        val view = composeView
        val dialogOwner = owner
        view?.let { v -> wm?.let { runCatching { it.removeView(v) } } }
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        owner = null
        composeView = null
        windowManager = null
        activeConversationKey = null
        messageHistoryState.clear()
        latestDataState.value = null
        onOpenAppHandler = null
        onMarkReadHandler = null
        onSendReplyHandler = null
        onDismissHandler = null
    }

    fun isShowing(): Boolean = composeView != null

    private fun hideIme(view: View?) {
        view ?: return
        val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

private const val DETAIL_AVATAR_SIZE_DP = 36
private const val DETAIL_HORIZONTAL_PADDING_DP = 14

@Composable
private fun CNoticeDetailContent(
    data: NotificationData,
    messages: List<NotificationMessage>,
    headerColor: Color,
    headerTextColor: Color,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onMarkRead: () -> Unit,
    onSendReply: (String) -> Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val quickReplyFailedMessage = stringResource(R.string.message_action_quick_reply_failed)
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val maxPanelHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.72f
    }
    val avatarSize = DETAIL_AVATAR_SIZE_DP.dp
    val headerTitle = NotificationData.overlayHeaderTitle(data)
    val displayMessages = remember(
        messages,
        messages.size,
        data.content,
        data.postTime,
        data.largeIcon,
        data.conversationIcon,
    ) {
        val source = if (messages.isNotEmpty()) {
            messages
        } else if (data.content.isNotBlank()) {
            listOf(
                NotificationMessage(
                    text = data.content,
                    timestamp = data.postTime.takeIf { it > 0L },
                    senderIcon = NotificationData.storedMessageSenderIcon(data),
                ),
            )
        } else {
            emptyList()
        }
        CNoticeListUiState.enrichMessageRowsForDisplay(
            NotificationMessagingHistory.messagesOldestFirst(source),
            data,
        )
    }
    var replyText by remember { mutableStateOf(TextFieldValue()) }

    LaunchedEffect(displayMessages.size, displayMessages.lastOrNull()?.text) {
        if (displayMessages.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(32)
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    OverlayAwareModuleTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = scrimInteractionSource,
                        onClick = onClose,
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 360.dp)
                    .heightIn(max = maxPanelHeight)
                    .imePadding()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerColor)
                        .padding(vertical = 10.dp),
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .size(32.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Close,
                            contentDescription = stringResource(R.string.message_overlay_menu_close),
                            tint = headerTextColor,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        MessageNotificationIcon(
                            iconBitmap = data.conversationIcon,
                            appIconBitmap = data.appIcon,
                            sizeDp = avatarSize,
                            allowAppIconFallback = false,
                            fallbackLabel = NotificationData.normalizeConversationTitle(headerTitle),
                        )
                        if (headerTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = headerTextColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight - 220.dp)
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = DETAIL_HORIZONTAL_PADDING_DP.dp,
                            vertical = 12.dp,
                        ),
                ) {
                    displayMessages.forEachIndexed { index, message ->
                        CNoticeDetailMessageRow(
                            message = message,
                            data = data,
                            appIcon = data.appIcon,
                            topPadding = if (index == 0) 0.dp else 12.dp,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (data.hasDirectReply) {
                    Text(
                        text = stringResource(
                            R.string.message_action_quick_reply_to,
                            headerTitle.ifBlank { data.packageName },
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(stringResource(R.string.message_action_quick_reply_hint))
                            },
                            singleLine = false,
                            maxLines = 4,
                        )
                        IconButton(
                            onClick = {
                                val text = replyText.text.trim()
                                if (text.isEmpty()) return@IconButton
                                if (onSendReply(text)) {
                                    replyText = TextFieldValue()
                                    hideIme(view)
                                } else {
                                    Toast.makeText(
                                        context,
                                        quickReplyFailedMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            enabled = replyText.text.isNotBlank(),
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.message_action_quick_reply_send),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (data.hasMarkAsRead) {
                        TextButton(onClick = onMarkRead) {
                            Text(stringResource(R.string.message_c_notice_mark_read))
                        }
                    }
                    Button(onClick = onOpenApp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.message_c_notice_open_app),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CNoticeDetailMessageRow(
    message: NotificationMessage,
    data: NotificationData,
    appIcon: android.graphics.Bitmap?,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    val timeLabel = message.timestamp?.let { timestamp ->
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    }.orEmpty()
    val avatarBitmap = CNoticeListUiState.resolveMessageRowAvatarBitmap(message, data)
    val avatarLabel = CNoticeListUiState.resolveMessageRowAvatarLabel(message, data)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        verticalAlignment = Alignment.Top,
    ) {
        if (CNoticeListUiState.shouldShowMessageRowAvatar(message, data)) {
            MessageNotificationIcon(
                iconBitmap = avatarBitmap,
                appIconBitmap = appIcon,
                sizeDp = DETAIL_AVATAR_SIZE_DP.dp,
                endPaddingDp = 10.dp,
                allowAppIconFallback = false,
                fallbackLabel = avatarLabel,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val sender = message.sender
            if (!sender.isNullOrBlank()) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = CNoticeListUiState.formatMessageRowBody(message, data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (sender.isNullOrBlank()) 0.dp else 2.dp),
            )
            if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun hideIme(view: View) {
    val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}
