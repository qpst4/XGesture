@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.ui.messagestyle.messageStyleLabel
import com.slideindex.app.ui.messagestyle.messageStyleSummary
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun MessageReminderSettingsScreen(
    settings: MessageSettings,
    notificationListenerEnabled: Boolean,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInterceptNotificationsChange: (Boolean) -> Unit,
    onOpenLastMessageOnUnlockChange: (Boolean) -> Unit,
    onOpenLastMessageOnUnlockRules: () -> Unit,
    onReturnChipEnabledChange: (Boolean) -> Unit,
    onReturnChipAutoDismissSecondsChange: (Int) -> Unit,
    onFloatIconEnabledChange: (Boolean) -> Unit,
    onSideBubbleEnabledChange: (Boolean) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onCNoticeEnabledChange: (Boolean) -> Unit,
    onEdgeCardEnabledChange: (Boolean) -> Unit = {},
    onOpenFloatIconSettings: () -> Unit,
    onOpenSideBubbleSettings: () -> Unit,
    onOpenDanmakuSettings: () -> Unit,
    onOpenCNoticeSettings: () -> Unit,
    onOpenEdgeCardSettings: () -> Unit = {},
    onHideInLandscapeChange: (Boolean) -> Unit,
    onPortraitDanmakuChange: (Boolean) -> Unit,
    onLandscapeDanmakuChange: (Boolean) -> Unit,
    onGestureActionChange: (String, MessageAction) -> Unit,
    onOpenGestureActionPick: (String) -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenDndApps: () -> Unit,
    onSuppressWhenSystemDndChange: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationListenerPermission: () -> Unit,
) {
    val context = LocalContext.current
    // 资源必须走 LocalResources：`LocalContext.current.getString()` 不会随 Configuration
    // （语言 / 深色 / 字体缩放）失效，Compose lint 直接判 ERROR
    // （LocalContextGetResourceValueCall）。context 仍保留给权限与能力查询。
    val resources = LocalResources.current
    val overlayPermissionGranted = PermissionHelper.canDrawOverlays(context)
    val overlayReady = MessageOverlayHost.canShow(context)
    val controlsEnabled = settings.enabled

    val listenerPermHint = stringResource(R.string.message_reminder_permission_listener_desc)
    val overlayPermHint = stringResource(R.string.message_reminder_permission_overlay_desc)
    val enableHint = stringResource(R.string.message_reminder_enable_hint)
    val returnChipNeverLabel = stringResource(R.string.message_reminder_auto_dismiss_off)
    val generalSectionTitle = stringResource(R.string.message_reminder_section_general)
    val styleSectionTitle = stringResource(R.string.message_style_title)
    val filterSectionTitle = stringResource(R.string.message_reminder_section_filter)
    val landscapeSectionTitle = stringResource(R.string.message_reminder_section_landscape)
    val gesturesSectionTitle = stringResource(R.string.message_reminder_section_gestures)

    SettingsScreenScaffold(
        title = stringResource(R.string.message_reminder_title),
        subtitle = stringResource(R.string.message_reminder_subtitle),
        onBack = onBack,
    ) {
        if (!notificationListenerEnabled || !overlayPermissionGranted) {
            if (!notificationListenerEnabled) {
                settingsLazyHint(
                    key = "message-perm-listener-hint",
                    text = listenerPermHint,
                )
            }
            if (!overlayPermissionGranted) {
                settingsLazyHint(
                    key = "message-perm-overlay-hint",
                    text = overlayPermHint,
                )
            }
            groupedCardItems(
                keyPrefix = "message-reminder-permissions",
                items = buildList {
                    if (!notificationListenerEnabled) {
                        add(
                            settingsCardScopeItem("listener-permission") {
                                SettingLinkRow(
                                    title = stringResource(R.string.message_reminder_permission_listener_title),
                                    subtitle = stringResource(R.string.grant_permission),
                                    onClick = onOpenNotificationListenerPermission,
                                )
                            },
                        )
                    }
                    if (!overlayPermissionGranted) {
                        add(
                            settingsCardScopeItem("overlay-permission") {
                                SettingLinkRow(
                                    title = stringResource(R.string.permission_overlay_title),
                                    subtitle = stringResource(R.string.grant_permission),
                                    onClick = onOpenOverlayPermission,
                                )
                            },
                        )
                    }
                },
            )
        } else if (!overlayReady) {
            settingsLazyHint(
                key = "message-overlay-not-ready",
                text = overlayPermHint,
            )
        }

        if (notificationListenerEnabled && overlayPermissionGranted && !settings.enabled) {
            settingsLazyHint(
                key = "message-enable-hint",
                text = enableHint,
            )
        }

        settingsLazySmallTitle(
            key = "message-general-section",
            title = generalSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "message-general",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_enabled),
                            subtitle = stringResource(R.string.message_reminder_enabled_desc),
                            icon = { label -> SymbolsIcon("Notifications", contentDescription = label) },
                            checked = settings.enabled,
                            enabled = notificationListenerEnabled && overlayPermissionGranted,
                            onCheckedChange = onEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("intercept-notifications") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_intercept_notifications),
                            subtitle = stringResource(R.string.message_reminder_intercept_notifications_desc),
                            icon = { label -> SymbolsIcon("DoNotDisturbOn", contentDescription = label) },
                            checked = settings.interceptNotifications,
                            enabled = notificationListenerEnabled,
                            onCheckedChange = onInterceptNotificationsChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("open-last-on-unlock") {
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.message_reminder_open_last_on_unlock),
                            subtitle = stringResource(R.string.message_reminder_open_last_on_unlock_desc),
                            icon = { label -> SymbolsIcon("LockOpen", contentDescription = label) },
                            checked = settings.openLastMessageOnUnlock,
                            enabled = controlsEnabled,
                            onCheckedChange = onOpenLastMessageOnUnlockChange,
                            onNavigate = onOpenLastMessageOnUnlockRules,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("return-chip") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_return_chip),
                            subtitle = stringResource(R.string.message_reminder_return_chip_desc),
                            icon = { label ->
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = label,
                                )
                            },
                            checked = settings.returnChipEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onReturnChipEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("return-chip-dismiss") {
                        val returnChipSeconds = settings.returnChipAutoDismissSeconds
                        SettingsSliderRow(
                            title = stringResource(R.string.message_reminder_return_chip_dismiss),
                            value = returnChipSeconds.toFloat(),
                            valueRange = 0f..30f,
                            steps = 29,
                            enabled = controlsEnabled && settings.returnChipEnabled,
                            label = if (returnChipSeconds <= 0) {
                                returnChipNeverLabel
                            } else {
                                stringResource(
                                    R.string.message_reminder_auto_dismiss_seconds,
                                    returnChipSeconds,
                                )
                            },
                            formatLabel = { value ->
                                val seconds = value.toInt()
                                if (seconds <= 0) {
                                    returnChipNeverLabel
                                } else {
                                    resources.getString(
                                        R.string.message_reminder_auto_dismiss_seconds,
                                        seconds,
                                    )
                                }
                            },
                            onValueChange = { onReturnChipAutoDismissSecondsChange(it.toInt()) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-style-section",
            title = styleSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "message-style",
            items = buildList {
                add(
                    settingsCardScopeItem("float-icon") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.FloatIcon),
                            subtitle = stringResource(R.string.message_style_float_icon_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Notifications,
                                    background = Color(0xFF42A5F5),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.floatIconEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onFloatIconEnabledChange,
                            onNavigate = onOpenFloatIconSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("side-bubble") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.SideBubble),
                            subtitle = stringResource(R.string.message_style_side_bubble_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.CropSquare,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.sideBubbleEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onSideBubbleEnabledChange,
                            onNavigate = onOpenSideBubbleSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("danmaku") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.Danmaku),
                            subtitle = stringResource(R.string.message_style_danmaku_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.SwipeLeft,
                                    background = Color(0xFF26A69A),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.danmakuEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onDanmakuEnabledChange,
                            onNavigate = onOpenDanmakuSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("c-notice") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.CNotice),
                            subtitle = stringResource(R.string.message_style_c_notice_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Notifications,
                                    background = Color(0xFFFF7043),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.cNoticeEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onCNoticeEnabledChange,
                            onNavigate = onOpenCNoticeSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("edge-card") {
                        SettingSwitchNavigationRow(
                            title = messageStyleLabel(MessageStyle.EdgeCard),
                            subtitle = stringResource(R.string.message_style_edge_card_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.SwipeRight,
                                    background = Color(0xFF7E57C2),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.edgeCardEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onEdgeCardEnabledChange,
                            onNavigate = onOpenEdgeCardSettings,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-filter-section",
            title = filterSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "message-filter",
            items = buildList {
                add(
                    settingsCardScopeItem("allowed-apps") {
                        SettingNavigationRow(
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Checklist,
                                    background = Color(0xFF4CAF50),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.message_reminder_allowed_apps),
                            subtitle = stringResource(R.string.message_reminder_allowed_apps_subtitle),
                            enabled = settings.enabled,
                            onClick = onOpenAllowedApps,
                            trailingContent = {
                                MessageReminderNavigationTrailing(
                                    count = settings.enabledPackages.size,
                                    showChevron = true,
                                )
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("dnd-apps") {
                        SettingNavigationRow(
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.DoNotDisturbOn,
                                    background = Color(0xFFF44336),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.message_reminder_dnd_apps),
                            subtitle = stringResource(R.string.message_reminder_dnd_apps_subtitle),
                            enabled = settings.enabled,
                            onClick = onOpenDndApps,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("suppress-system-dnd") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_suppress_system_dnd),
                            subtitle = stringResource(R.string.message_reminder_suppress_system_dnd_desc),
                            icon = { label ->
                                MessageReminderColoredIcon(
                                    icon = Icons.Outlined.Bedtime,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.suppressWhenSystemDnd,
                            enabled = settings.enabled,
                            onCheckedChange = onSuppressWhenSystemDndChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-landscape-section",
            title = landscapeSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "message-landscape",
            items = buildList {
                add(
                    settingsCardScopeItem("hide-in-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_hide_in_landscape),
                            subtitle = stringResource(R.string.message_reminder_hide_in_landscape_desc),
                            checked = settings.hideInLandscape,
                            enabled = settings.enabled,
                            onCheckedChange = onHideInLandscapeChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("portrait-danmaku") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_portrait_danmaku),
                            subtitle = stringResource(R.string.message_reminder_portrait_danmaku_desc),
                            checked = settings.portraitDanmaku,
                            enabled = settings.enabled,
                            onCheckedChange = onPortraitDanmakuChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("landscape-danmaku") {
                        SettingSwitchRow(
                            title = stringResource(R.string.message_reminder_landscape_danmaku),
                            subtitle = stringResource(R.string.message_reminder_landscape_danmaku_desc),
                            checked = settings.landscapeDanmaku,
                            enabled = settings.enabled,
                            onCheckedChange = onLandscapeDanmakuChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "message-gestures-section",
            title = gesturesSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "message-gestures",
            items = buildList {
                add(
                    settingsCardScopeItem("gesture-tap") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_tap),
                            icon = Icons.Outlined.TouchApp,
                            action = settings.singleTapAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_TAP) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-up") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_up),
                            icon = Icons.Outlined.SwipeUp,
                            action = settings.swipeUpAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_UP) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-down") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_down),
                            icon = Icons.Outlined.SwipeDown,
                            action = settings.swipeDownAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_DOWN) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-left") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_left),
                            icon = Icons.Outlined.SwipeLeft,
                            action = settings.swipeLeftAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_LEFT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-swipe-right") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_swipe_right),
                            icon = Icons.Outlined.SwipeRight,
                            action = settings.swipeRightAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_RIGHT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("gesture-long-press") {
                        MessageGestureActionRow(
                            title = stringResource(R.string.message_reminder_gesture_long_press),
                            icon = Icons.Outlined.BackHand,
                            action = settings.longPressAction,
                            enabled = settings.enabled,
                            onClick = { onOpenGestureActionPick(MessageSettingsCodec.SLOT_LONG_PRESS) },
                        )
                    },
                )
            },
        )

        item(key = "message-reminder-bottom-inset") {
            Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
        }
    }
}

@Composable
private fun MessageReminderNavigationTrailing(
    count: Int,
    showChevron: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_navigate_forward), modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageReminderColoredIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsCardScope.MessageGestureActionRow(
    title: String,
    icon: ImageVector,
    action: MessageAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary) },
        title = title,
        subtitle = messageActionLabel(action),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.MessageReminderEntryCard(
    enabled: Boolean,
    settings: MessageSettings? = null,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.notifications(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.message_reminder_title),
        subtitle = if (settings != null) {
            messageStyleSummary(settings)
        } else if (enabled) {
            stringResource(R.string.message_reminder_entry_enabled)
        } else {
            stringResource(R.string.message_reminder_entry_disabled)
        },
        onClick = onClick,
    )
}
