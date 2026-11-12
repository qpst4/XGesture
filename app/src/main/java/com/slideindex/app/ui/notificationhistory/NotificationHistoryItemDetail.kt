package com.slideindex.app.ui.notificationhistory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.NotificationChannelSupport
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.ui.Md3PickerAppLeading
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val RowHorizontalPadding = 14.dp
private val RowVerticalPadding = 14.dp
private val TimeColumnWidth = 40.dp
private val RailWidth = 36.dp
private val RailGap = 6.dp
private const val RailIconSizeDp = 34

/**
 * 一条通知在时间线里的行：
 *
 * ```
 * 00:02  ●   X手势运行中
 *        │   边缘手势服务运行中，点击打开设置
 *        │   X手势 · 通知栏中          ⋮
 * ```
 *
 * 左侧时间列 + 图标节点 + 贯穿的竖线由这里负责；右侧只放标题/正文/来源。
 * 渠道 ID 不在列表里出现，长按或点 ⋮ 仍可复制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationHistoryRow(
    item: NotificationHistoryItem,
    appInfo: AppInfo?,
    timeLabel: String,
    isActiveInShade: Boolean,
    matchingRule: NotificationFilterRule?,
    showHiddenBadge: Boolean = item.hidden,
    showDeleteAction: Boolean = true,
    showRestoreAction: Boolean = false,
    showTopLine: Boolean = true,
    showBottomLine: Boolean = true,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val displayTitle = item.title.ifBlank { appInfo?.label ?: item.packageName }
    val channelId = remember(item.id, item.channelId, item.notificationKey, item.postedAtMs) {
        NotificationChannelSupport.resolveChannelId(item)
    }
    val reopenLabel = stringResource(R.string.notification_history_menu_reopen)
    val hideLabel = stringResource(R.string.notification_filter_hide)
    val restoreLabel = stringResource(R.string.notification_history_menu_restore)
    val deleteLabel = stringResource(R.string.notification_history_delete)
    val copyChannelIdLabel = stringResource(R.string.notification_history_menu_copy_channel_id)
    val openChannelSettingsLabel = stringResource(R.string.notification_history_menu_open_channel_settings)
    val menuEntry = DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = reopenLabel,
                    onClick = {
                        showMenu = false
                        onOpen()
                    },
                ),
            )
            if (!channelId.isNullOrBlank()) {
                add(
                    DropdownItem(
                        text = copyChannelIdLabel,
                        onClick = {
                            showMenu = false
                            copyChannelIdToClipboard(context, channelId)
                        },
                    ),
                )
            }
            add(
                DropdownItem(
                    text = openChannelSettingsLabel,
                    onClick = {
                        showMenu = false
                        NotificationChannelSupport.openSettings(
                            context = context,
                            packageName = item.packageName,
                            channelId = channelId,
                        )
                    },
                ),
            )
            if (!item.hidden && matchingRule == null) {
                add(
                    DropdownItem(
                        text = hideLabel,
                        onClick = {
                            showMenu = false
                            onHide()
                        },
                    ),
                )
            }
            if (item.hidden || matchingRule != null) {
                add(
                    DropdownItem(
                        text = restoreLabel,
                        onClick = {
                            showMenu = false
                            onUnhide()
                        },
                    ),
                )
            }
            if (showDeleteAction) {
                add(
                    DropdownItem(
                        text = deleteLabel,
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    ),
                )
            }
        },
    )

    val lineColor = MiuixTheme.colorScheme.dividerLine
    val iconBackground = MiuixTheme.colorScheme.background
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val paddingPx = RowHorizontalPadding.toPx()
                    val timePx = TimeColumnWidth.toPx()
                    val railPx = RailWidth.toPx()
                    val lineX = if (layoutDirection == LayoutDirection.Rtl) {
                        size.width - paddingPx - timePx - railPx / 2f
                    } else {
                        paddingPx + timePx + railPx / 2f
                    }
                    val iconCenterY = RowVerticalPadding.toPx() + railPx / 2f
                    val top = if (showTopLine) 0f else iconCenterY
                    val bottom = if (showBottomLine) size.height else iconCenterY
                    if (bottom > top) {
                        drawLine(
                            color = lineColor,
                            start = Offset(lineX, top),
                            end = Offset(lineX, bottom),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = timeLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                modifier = Modifier
                    .width(TimeColumnWidth)
                    .padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .size(RailWidth)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (appInfo != null) {
                    Md3PickerAppLeading(app = appInfo, sizeDp = RailIconSizeDp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.cd_notification_icon),
                        modifier = Modifier.size(22.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(RailGap))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayTitle,
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (showHiddenBadge) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.notification_filter_hidden_badge),
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!item.extractedCode.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.otp_extracted_code_label, item.extractedCode!!),
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = {
                                OtpClipboardHelper.copyCode(context, item.extractedCode!!)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Medium.Copy,
                                contentDescription = stringResource(R.string.otp_copy_action),
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
                Text(
                    text = buildString {
                        append(appInfo?.label ?: item.packageName)
                        if (isActiveInShade) {
                            append(" · ")
                            append(stringResource(R.string.notification_filter_active_in_shade))
                        }
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showRestoreAction) {
                TextButton(
                    text = stringResource(R.string.notification_restore_action),
                    onClick = onUnhide,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            } else {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Medium.More,
                        contentDescription = stringResource(R.string.notification_filter_more_menu),
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        OverlayDropdownPopup(
            entries = listOf(menuEntry),
            show = showMenu,
            onDismiss = { showMenu = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
        )
    }
}

private fun copyChannelIdToClipboard(context: Context, channelId: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("channel_id", channelId))
    Toast.makeText(
        context,
        context.getString(R.string.notification_history_channel_id_copied, channelId),
        Toast.LENGTH_SHORT,
    ).show()
}
