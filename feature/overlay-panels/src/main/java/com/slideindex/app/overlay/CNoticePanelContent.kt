package com.slideindex.app.overlay

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.NotificationMessage
import com.slideindex.app.message.NotificationMessagingHistory
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons

data class CNoticePanelEntryUi(
    val conversationSourceKey: String,
    val data: NotificationData,
    val unreadCount: Int,
    val messages: List<NotificationMessage>,
    val headerColor: Color,
    val headerTextColor: Color,
)

private val ReplyInputShape = RoundedCornerShape(20.dp)

@Composable
fun CNoticePanelContent(
    entries: List<CNoticePanelEntryUi>,
    selectedKey: String,
    horizontalEdge: SideBubbleHorizontalEdge,
    historyLoading: Boolean,
    historyExhausted: Boolean,
    onSelectConversation: (String) -> Unit,
    onClosePanel: () -> Unit,
    onOpenApp: (String) -> Unit,
    onMarkRead: (String) -> Unit,
    onSendReply: (String, String) -> Boolean,
    onLoadOlderHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = entries.firstOrNull { it.conversationSourceKey == selectedKey } ?: entries.firstOrNull()
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val hostContext = context.applicationContext
    val density = LocalDensity.current
    val (screenWidthPx, screenHeightPx) = OverlayScreenMetrics.sizePx(hostContext)
    val displayWidthDp = with(density) { screenWidthPx.toDp() }
    val displayHeightDp = with(density) { screenHeightPx.toDp() }
    val isLandscape = displayWidthDp > displayHeightDp
    val maxPanelHeight = if (isLandscape) {
        minOf(displayWidthDp, displayHeightDp) * 0.9f
    } else {
        displayHeightDp * 0.82f
    }
    val maxPanelWidth = if (isLandscape) {
        with(density) {
            MessageOverlayLayout.cNoticeLandscapeSheetWidthPx(hostContext).toDp()
        }
    } else {
        360.dp
    }
    val panelAlignment = if (isLandscape) {
        when (horizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Alignment.TopStart
            SideBubbleHorizontalEdge.Right -> Alignment.TopEnd
        }
    } else {
        Alignment.TopCenter
    }
    val panelPadding = if (isLandscape) {
        Modifier.padding(
            top = 20.dp,
            bottom = 12.dp,
            start = if (horizontalEdge == SideBubbleHorizontalEdge.Left) 6.dp else 12.dp,
            end = if (horizontalEdge == SideBubbleHorizontalEdge.Right) 6.dp else 12.dp,
        )
    } else {
        Modifier.padding(top = 44.dp, bottom = 12.dp)
    }
    val rootModifier = if (isLandscape) {
        modifier.width(maxPanelWidth)
    } else {
        modifier.fillMaxSize()
    }
    OverlayAwareModuleTheme {
        Box(
            modifier = rootModifier,
        ) {
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            indication = null,
                            interactionSource = scrimInteractionSource,
                            onClick = onClosePanel,
                        ),
                )
            }
            val panelCard = @Composable {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    selected?.let { entry ->
                        CNoticePanelDetailContent(
                            entry = entry,
                            maxPanelHeight = maxPanelHeight,
                            historyLoading = historyLoading,
                            historyExhausted = historyExhausted,
                            onBack = onClosePanel,
                            onClose = onClosePanel,
                            onOpenApp = { onOpenApp(entry.conversationSourceKey) },
                            onMarkRead = { onMarkRead(entry.conversationSourceKey) },
                            onSendReply = { text -> onSendReply(entry.conversationSourceKey, text) },
                            onLoadOlderHistory = { onLoadOlderHistory(entry.conversationSourceKey) },
                        )
                    }
                }
            }
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .align(panelAlignment)
                        .width(maxPanelWidth)
                        .then(panelPadding)
                        .imePadding(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (horizontalEdge == SideBubbleHorizontalEdge.Left && entries.isNotEmpty()) {
                        CNoticePanelDockVertical(
                            entries = entries,
                            selectedKey = selectedKey,
                            onSelect = onSelectConversation,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        panelCard()
                    }
                    if (horizontalEdge == SideBubbleHorizontalEdge.Right && entries.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CNoticePanelDockVertical(
                            entries = entries,
                            selectedKey = selectedKey,
                            onSelect = onSelectConversation,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(panelAlignment)
                        .width(maxPanelWidth)
                        .then(panelPadding)
                        .imePadding(),
                ) {
                    panelCard()
                    if (entries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CNoticePanelDock(
                            entries = entries,
                            selectedKey = selectedKey,
                            onSelect = onSelectConversation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CNoticePanelDock(
    entries: List<CNoticePanelEntryUi>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(entries, key = { it.conversationSourceKey }) { entry ->
            CNoticePanelDockItem(
                entry = entry,
                selected = entry.conversationSourceKey == selectedKey,
                onSelect = { onSelect(entry.conversationSourceKey) },
            )
        }
    }
}

@Composable
private fun CNoticePanelDockVertical(
    entries: List<CNoticePanelEntryUi>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(entries, key = { it.conversationSourceKey }) { entry ->
            CNoticePanelDockItem(
                entry = entry,
                selected = entry.conversationSourceKey == selectedKey,
                onSelect = { onSelect(entry.conversationSourceKey) },
            )
        }
    }
}

@Composable
private fun CNoticePanelDockItem(
    entry: CNoticePanelEntryUi,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val title = NotificationData.overlayHeaderTitle(entry.data)
    Box(
        modifier = Modifier
            .clickable(onClick = onSelect)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (selected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MessageNotificationIcon(
                iconBitmap = entry.data.conversationIcon,
                appIconBitmap = entry.data.appIcon,
                sizeDp = 42.dp,
                allowAppIconFallback = false,
                fallbackLabel = NotificationData.normalizeConversationTitle(title),
                iconShape = CircleShape,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
        }
        if (entry.unreadCount > 0) {
            Text(
                text = entry.unreadCount.coerceAtMost(99).toString(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
            )
        }
    }
}

@Composable
private fun CNoticePanelDetailContent(
    entry: CNoticePanelEntryUi,
    maxPanelHeight: androidx.compose.ui.unit.Dp,
    historyLoading: Boolean,
    historyExhausted: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onMarkRead: () -> Unit,
    onSendReply: (String) -> Boolean,
    onLoadOlderHistory: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val quickReplyFailedMessage = stringResource(R.string.message_action_quick_reply_failed)
    val data = entry.data
    val headerTitle = NotificationData.overlayHeaderTitle(data)
    val displayMessages = remember(
        entry.messages,
        entry.messages.size,
        data.content,
        data.postTime,
        data.largeIcon,
        data.conversationIcon,
    ) {
        val source = if (entry.messages.isNotEmpty()) {
            entry.messages
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
    val listState = rememberLazyListState()

    LaunchedEffect(displayMessages.size, displayMessages.lastOrNull()?.text) {
        if (displayMessages.isEmpty()) return@LaunchedEffect
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LaunchedEffect(entry.conversationSourceKey) {
        if (!historyLoading && !historyExhausted) {
            onLoadOlderHistory()
        }
    }

    LaunchedEffect(listState, historyLoading, historyExhausted) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index == 0 && !historyLoading && !historyExhausted) {
                    onLoadOlderHistory()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxPanelHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(entry.headerColor)
                .padding(vertical = 6.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.message_c_notice_panel_back),
                    tint = entry.headerTextColor,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                MessageNotificationIcon(
                    iconBitmap = data.conversationIcon,
                    appIconBitmap = data.appIcon,
                    sizeDp = 32.dp,
                    allowAppIconFallback = false,
                    fallbackLabel = NotificationData.normalizeConversationTitle(headerTitle),
                )
                if (headerTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = entry.headerTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (data.hasMarkAsRead) {
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Ok,
                            contentDescription = stringResource(R.string.message_c_notice_mark_read),
                            tint = entry.headerTextColor,
                        )
                    }
                }
                IconButton(
                    onClick = onOpenApp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.message_c_notice_open_app),
                        tint = entry.headerTextColor,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Close,
                        contentDescription = stringResource(R.string.message_overlay_menu_close),
                        tint = entry.headerTextColor,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            state = listState,
        ) {
            if (historyLoading) {
                item(key = "history_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            items(displayMessages.size, key = { index ->
                "msg_${index}_${displayMessages[index].text.hashCode()}"
            }) { index ->
                CNoticePanelMessageRow(
                    message = displayMessages[index],
                    data = data,
                    appIcon = data.appIcon,
                    topPadding = if (index == 0) 8.dp else 0.dp,
                )
            }
        }
        if (data.hasDirectReply) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            CNoticeReplyInputBar(
                replyText = replyText,
                onReplyTextChange = { replyText = it },
                onSend = {
                    val text = replyText.text.trim()
                    if (text.isEmpty()) return@CNoticeReplyInputBar
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
            )
        }
    }
}

@Composable
private fun CNoticePanelMessageRow(
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .padding(top = topPadding),
        verticalAlignment = Alignment.Top,
    ) {
        if (CNoticeListUiState.shouldShowMessageRowAvatar(message, data)) {
            MessageNotificationIcon(
                iconBitmap = avatarBitmap,
                appIconBitmap = appIcon,
                sizeDp = 36.dp,
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

@Composable
private fun CNoticeReplyInputBar(
    replyText: TextFieldValue,
    onReplyTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = replyText,
            onValueChange = onReplyTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.message_action_quick_reply_hint)) },
            singleLine = false,
            maxLines = 2,
            shape = ReplyInputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = replyText.text.isNotBlank(),
            modifier = Modifier
                .padding(start = 6.dp)
                .size(38.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.message_action_quick_reply_send),
            )
        }
    }
}

private fun hideIme(view: View) {
    val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}
