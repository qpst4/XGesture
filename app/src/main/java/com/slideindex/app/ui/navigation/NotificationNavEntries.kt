package com.slideindex.app.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.otp.OtpAccessibilitySettingsHelper
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.ui.MessageAppFilterEditorScreen
import com.slideindex.app.ui.MessageGestureActionPickerScreen
import com.slideindex.app.ui.MessageReminderAllowedAppsScreen
import com.slideindex.app.ui.MessageReminderUnlockRulesScreen
import com.slideindex.app.ui.MessageReminderDndAppsScreen
import com.slideindex.app.ui.MessageReminderSettingsScreen
import com.slideindex.app.ui.MessageStyleDetailSettingsScreen
import com.slideindex.app.message.MessageReminderPreviewController
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.ui.NotificationFilterSettingsScreen
import com.slideindex.app.ui.NotificationRuleEditorScreen
import com.slideindex.app.ui.NotificationRulesScreen
import com.slideindex.app.ui.messagestyle.SideBubbleCountPickerScreen
import com.slideindex.app.ui.NotificationHistoryScreen
import com.slideindex.app.ui.NotificationHubScreen
import com.slideindex.app.ui.OtpSmsBlacklistScreen
import com.slideindex.app.ui.OtpExtractionScreen
import com.slideindex.app.ui.OtpAutoFillScreen
import com.slideindex.app.ui.OtpRulesScreen
import com.slideindex.app.ui.OtpRecordsPage
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.OtpHubScreen
import com.slideindex.app.xposed.bridge.PhoneProcessRestartRequester
import com.slideindex.app.ui.viewmodel.MessageSettingsViewModel
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import com.slideindex.app.ui.viewmodel.NotificationHubViewModel
import com.slideindex.app.ui.viewmodel.OtpSettingsViewModel
import com.slideindex.app.settings.toMinimalAppSettings
import kotlinx.coroutines.launch

fun NavEntryBuilder.notificationNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.NotificationHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: NotificationHubViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val visibleHistoryCount by viewModel.visibleHistoryCount.collectAsStateWithLifecycle()
        NotificationHubScreen(
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            messageReminderEnabled = messageSettings.enabled &&
                messageSettings.hasAnyStyleEnabled(),
            messageReminderSettings = messageSettings,
            notificationHistoryCount = visibleHistoryCount,
            onOpenNotificationHistory = { ctx.navigate(AppNavKey.NotificationHistory) },
            onOpenOtpHub = { ctx.navigate(AppNavKey.OtpHub) },
            onOpenMessageReminder = { ctx.navigate(AppNavKey.MessageReminder) },
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
        )
    }

    hiltEntry<AppNavKey.NotificationHistory> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val permissions = ctx.collectPermissions()
        NotificationHistoryScreen(
            viewModel = viewModel,
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onOpenRules = { ctx.navigate(AppNavKey.NotificationFilterRules) },
            onOpenSettings = { ctx.navigate(AppNavKey.NotificationFilterSettings) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    hiltEntry<AppNavKey.NotificationFilterRules> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val filterRules by viewModel.rules.collectAsStateWithLifecycle()
        NotificationRulesScreen(
            rules = filterRules.filter { it.userCreated },
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHistory) },
            onRemoveRule = viewModel::removeRule,
            onSetRuleEnabled = viewModel::setRuleEnabled,
            onOpenRuleEditor = { ruleId ->
                ctx.navigate(AppNavKey.NotificationFilterRuleEditor(ruleId.orEmpty()))
            },
        )
    }

    hiltEntry<AppNavKey.NotificationFilterRuleEditor> { key ->
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val filterRules by viewModel.rules.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val initialRule = key.ruleId.takeIf { it.isNotEmpty() }
            ?.let { id -> filterRules.find { it.id == id } }
        NotificationRuleEditorScreen(
            initialRule = initialRule,
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationFilterRules) },
            onSave = { saved ->
                if (saved.actionEntries.isEmpty()) {
                    Toast.makeText(context, R.string.notification_rule_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.upsertRule(saved)
                    Toast.makeText(context, R.string.notification_rule_saved, Toast.LENGTH_SHORT).show()
                    ctx.navigateBackTo(AppNavKey.NotificationFilterRules)
                }
            },
        )
    }

    hiltEntry<AppNavKey.NotificationFilterSettings> {
        val viewModel: NotificationHistoryViewModel = hiltViewModel()
        val permissions = ctx.collectPermissions()
        NotificationFilterSettingsScreen(
            viewModel = viewModel,
            listenerEnabled = permissions.notificationListenerEnabled,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHistory) },
            onRequestListenerAccess = { ctx.openNotificationListenerSettings() },
        )
    }

    hiltEntry<AppNavKey.MessageReminder> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        MessageReminderSettingsScreen(
            settings = messageSettings,
            notificationListenerEnabled = permissions.notificationListenerEnabled,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onEnabledChange = viewModel::setMessageReminderEnabled,
            onInterceptNotificationsChange = viewModel::setMessageInterceptNotifications,
            onOpenLastMessageOnUnlockChange = viewModel::setMessageOpenLastOnUnlock,
            onOpenLastMessageOnUnlockRules = {
                ctx.navigate(AppNavKey.MessageReminderUnlockRules)
            },
            onReturnChipEnabledChange = viewModel::setMessageReturnChipEnabled,
            onReturnChipAutoDismissSecondsChange = viewModel::setMessageReturnChipAutoDismissSeconds,
            onFloatIconEnabledChange = viewModel::setMessageFloatIconEnabled,
            onSideBubbleEnabledChange = viewModel::setMessageSideBubbleEnabled,
            onDanmakuEnabledChange = viewModel::setMessageDanmakuEnabled,
            onCNoticeEnabledChange = viewModel::setMessageCNoticeEnabled,
            onEdgeCardEnabledChange = viewModel::setMessageEdgeCardEnabled,
            onOpenFloatIconSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.FloatIcon.id))
            },
            onOpenSideBubbleSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.SideBubble.id))
            },
            onOpenDanmakuSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.Danmaku.id))
            },
            onOpenCNoticeSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.CNotice.id))
            },
            onOpenEdgeCardSettings = {
                ctx.navigate(AppNavKey.MessageStyleDetail(MessageStyle.EdgeCard.id))
            },
            onHideInLandscapeChange = viewModel::setMessageHideInLandscape,
            onPortraitDanmakuChange = viewModel::setMessagePortraitDanmaku,
            onLandscapeDanmakuChange = viewModel::setMessageLandscapeDanmaku,
            onGestureActionChange = viewModel::setMessageGestureAction,
            onOpenGestureActionPick = { slot ->
                ctx.navigate(AppNavKey.MessageReminderGestureActionPick(slot))
            },
            onOpenAllowedApps = { ctx.navigate(AppNavKey.MessageReminderAllowedApps) },
            onOpenDndApps = { ctx.navigate(AppNavKey.MessageReminderDndApps) },
            onSuppressWhenSystemDndChange = viewModel::setMessageSuppressWhenSystemDnd,
            onOpenOverlayPermission = { ctx.openOverlaySettings() },
            onOpenNotificationListenerPermission = { ctx.openNotificationListenerSettings() },
        )
    }

    hiltEntry<AppNavKey.MessageStyleDetail> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val style = MessageStyle.fromId(key.styleId)
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestSettings by rememberUpdatedState(messageSettings)
        val latestContext by rememberUpdatedState(context)
        DisposableEffect(lifecycleOwner, style) {
            fun startPreviewIfNeeded() {
                if (
                    style == MessageStyle.FloatIcon ||
                    style == MessageStyle.SideBubble ||
                    style == MessageStyle.CNotice ||
                    style == MessageStyle.EdgeCard
                ) {
                    MessageReminderPreviewController.start(latestContext, style, latestSettings)
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> startPreviewIfNeeded()
                    Lifecycle.Event.ON_STOP -> MessageReminderPreviewController.end(restorePlacement = true)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startPreviewIfNeeded()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                MessageReminderPreviewController.end(restorePlacement = true)
            }
        }
        LaunchedEffect(
            style,
            messageSettings.floatIconCorner,
            messageSettings.floatIconYFraction,
            messageSettings.floatIconSizeDp,
            messageSettings.floatIconOpacity,
            messageSettings.sideBubbleHorizontalEdge,
            messageSettings.sideBubbleYFraction,
            messageSettings.sideThemeId,
            messageSettings.sideBubbleOpacity,
            messageSettings.sideBubbleFontSizeLevel,
            messageSettings.cNoticeHorizontalEdge,
            messageSettings.cNoticeYFraction,
            messageSettings.cNoticeIconSizeDp,
            messageSettings.cNoticeOpacity,
            messageSettings.cNoticeDimmedOpacity,
            messageSettings.cNoticeGhostOpacity,
            messageSettings.cNoticeEdgeMarginDp,
            messageSettings.edgeCardHorizontalEdge,
            messageSettings.edgeCardYFraction,
            messageSettings.edgeCardOpacity,
        ) {
            if (!MessageReminderPreviewController.isPreviewActive(style)) return@LaunchedEffect
            MessageReminderPreviewController.updatePreview(context, messageSettings)
        }
        MessageStyleDetailSettingsScreen(
            style = style,
            settings = messageSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onOpenSideCountPick = {
                ctx.navigate(AppNavKey.MessageStyleSideBubbleCount)
            },
            onSideThemeIdChange = viewModel::setMessageSideThemeId,
            onDanmakuThemeIdChange = viewModel::setMessageDanmakuThemeId,
            onFloatIconOpacityChange = viewModel::setMessageFloatIconOpacity,
            onSideBubbleOpacityChange = viewModel::setMessageSideBubbleOpacity,
            onDanmakuOpacityChange = viewModel::setMessageDanmakuOpacity,
            onDanmakuMaxLinesChange = viewModel::setMessageDanmakuMaxLines,
            onSideMaxCountChange = viewModel::setMessageSideMaxCount,
            onSideMaxLinesChange = viewModel::setMessageSideMaxLines,
            onFloatIconSizeDpChange = viewModel::setMessageFloatIconSizeDp,
            onFloatIconAutoDismissSecondsChange = viewModel::setMessageFloatIconAutoDismissSeconds,
            onFloatIconKeepAfterUnlockChange = viewModel::setMessageKeepFloatIconAfterUnlock,
            onSideBubbleAutoDismissSecondsChange = viewModel::setMessageSideBubbleAutoDismissSeconds,
            onSideHorizontalEdgeChange = { edge ->
                viewModel.setMessageSideHorizontalEdge(edge.id)
            },
            onSideBubbleYFractionPreviewChange = { fraction ->
                MessageReminderPreviewController.previewSettings(
                    context,
                    messageSettings.copy(sideBubbleYFraction = fraction),
                )
            },
            onSideBubbleYFractionPreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
            onSideBubbleYFractionChange = viewModel::setMessageSideBubbleYFraction,
            onFloatIconCornerChange = viewModel::setMessageFloatIconCorner,
            onFloatIconYFractionPreviewChange = { fraction ->
                MessageReminderPreviewController.previewSettings(
                    context,
                    messageSettings.copy(floatIconYFraction = fraction),
                )
            },
            onFloatIconYFractionPreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
            onFloatIconYFractionChange = viewModel::setMessageFloatIconYFraction,
            onSideFontSizeLevelChange = viewModel::setMessageSideFontSizeLevel,
            onDanmakuSpeedLevelChange = viewModel::setMessageDanmakuSpeedLevel,
            onCNoticeOpacityChange = viewModel::setMessageCNoticeOpacity,
            onCNoticeDimmedOpacityChange = viewModel::setMessageCNoticeDimmedOpacity,
            onCNoticeGhostOpacityChange = viewModel::setMessageCNoticeGhostOpacity,
            onCNoticeIconSizeDpChange = viewModel::setMessageCNoticeIconSizeDp,
            onCNoticeMaxCountChange = viewModel::setMessageCNoticeMaxCount,
            onCNoticeAutoDismissSecondsChange = viewModel::setMessageCNoticeAutoDismissSeconds,
            onCNoticeDefaultCollapsedChange = viewModel::setMessageCNoticeDefaultCollapsed,
            onCNoticePeekBannerEnabledChange = viewModel::setMessageCNoticePeekBannerEnabled,
            onCNoticeLandscapeEnabledChange = viewModel::setMessageCNoticeLandscapeEnabled,
            onCNoticeHorizontalEdgeChange = { edge ->
                viewModel.setMessageCNoticeHorizontalEdge(edge.id)
            },
            onCNoticeYFractionPreviewChange = { fraction ->
                MessageReminderPreviewController.previewSettings(
                    context,
                    messageSettings.copy(cNoticeYFraction = fraction),
                )
            },
            onCNoticeYFractionPreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
            onCNoticeYFractionChange = viewModel::setMessageCNoticeYFraction,
            onCNoticeEdgeMarginPreviewChange = { marginDp ->
                MessageReminderPreviewController.previewSettings(
                    context,
                    messageSettings.copy(cNoticeEdgeMarginDp = marginDp),
                )
            },
            onCNoticeEdgeMarginPreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
            onCNoticeEdgeMarginChange = viewModel::setMessageCNoticeEdgeMarginDp,
            onEdgeCardOpacityChange = viewModel::setMessageEdgeCardOpacity,
            onEdgeCardAutoCollapseMsChange = viewModel::setMessageEdgeCardAutoCollapseMs,
            onEdgeCardMaxCountChange = viewModel::setMessageEdgeCardMaxCount,
            onEdgeCardMaxLinesChange = viewModel::setMessageEdgeCardMaxLines,
            onEdgeCardKeepAcrossLockChange = viewModel::setMessageEdgeCardKeepAcrossLock,
            onEdgeCardCollapseWhenScrollDoneChange =
                viewModel::setMessageEdgeCardCollapseWhenScrollDone,
            onEdgeCardHorizontalEdgeChange = { edge ->
                viewModel.setMessageEdgeCardHorizontalEdge(edge.id)
            },
            onEdgeCardYFractionPreviewChange = { fraction ->
                MessageReminderPreviewController.previewSettings(
                    context,
                    messageSettings.copy(edgeCardYFraction = fraction),
                )
            },
            onEdgeCardYFractionPreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
            onEdgeCardYFractionChange = viewModel::setMessageEdgeCardYFraction,
            onMessagePreviewChange = { previewSettings ->
                MessageReminderPreviewController.previewSettings(context, previewSettings)
            },
            onMessagePreviewCommit = {
                MessageReminderPreviewController.clearPlacementBaseline()
            },
        )
    }

    hiltEntry<AppNavKey.MessageStyleSideBubbleCount> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.MessageStyleDetail(MessageStyle.SideBubble.id)
        SideBubbleCountPickerScreen(
            selectedCount = messageSettings.sideMaxCount,
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelect = { count ->
                viewModel.setMessageSideMaxCount(count)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.MessageReminderAllowedApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageReminderAllowedAppsScreen(
            settings = messageSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageEnabledPackage,
            onRemovePackage = viewModel::removeMessageEnabledPackage,
            onSaveFilterRule = viewModel::upsertMessageAppFilterRule,
            onOpenFilterEditor = { packageName ->
                ctx.navigate(AppNavKey.MessageReminderAppFilterEdit(packageName))
            },
        )
    }

    hiltEntry<AppNavKey.MessageReminderUnlockRules> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageReminderUnlockRulesScreen(
            settings = messageSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAlwaysAllowChange = viewModel::setMessageOpenLastAlways,
            onUnlockConfirmationAutoDismissSecondsChange =
                viewModel::setMessageUnlockConfirmationAutoDismissSeconds,
            onUnlockOpenDelayMsChange = viewModel::setMessageUnlockOpenDelayMs,
        )
    }

    hiltEntry<AppNavKey.MessageReminderAppFilterEdit> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val packageName = key.packageName
        val appLabel = remember(packageName) {
            runCatching {
                ctx.activity.packageManager.getApplicationLabel(
                    ctx.activity.packageManager.getApplicationInfo(packageName, 0),
                ).toString()
            }.getOrDefault(packageName)
        }
        MessageAppFilterEditorScreen(
            appLabel = appLabel,
            rule = messageSettings.filterRuleFor(packageName),
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminderAllowedApps) },
            onSave = { rule ->
                viewModel.upsertMessageAppFilterRule(rule)
                ctx.navigateBackTo(AppNavKey.MessageReminderAllowedApps)
            },
        )
    }

    hiltEntry<AppNavKey.MessageReminderGestureActionPick> { key ->
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        val current = when (key.slot) {
            MessageSettingsCodec.SLOT_TAP -> messageSettings.singleTapAction
            MessageSettingsCodec.SLOT_UP -> messageSettings.swipeUpAction
            MessageSettingsCodec.SLOT_DOWN -> messageSettings.swipeDownAction
            MessageSettingsCodec.SLOT_LEFT -> messageSettings.swipeLeftAction
            MessageSettingsCodec.SLOT_RIGHT -> messageSettings.swipeRightAction
            MessageSettingsCodec.SLOT_LONG_PRESS -> messageSettings.longPressAction
            else -> MessageAction.Ignore
        }
        MessageGestureActionPickerScreen(
            current = current,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onSelect = { action ->
                viewModel.setMessageGestureAction(key.slot, action)
                ctx.navigateBackTo(AppNavKey.MessageReminder)
            },
        )
    }

    hiltEntry<AppNavKey.MessageReminderDndApps> {
        val viewModel: MessageSettingsViewModel = hiltViewModel()
        val messageSettings by viewModel.messageReminderSettings.collectAsStateWithLifecycle()
        MessageReminderDndAppsScreen(
            dndPackages = messageSettings.dndPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.MessageReminder) },
            onAddPackage = viewModel::addMessageDndPackage,
            onRemovePackage = viewModel::removeMessageDndPackage,
        )
    }

    hiltEntry<AppNavKey.OtpHub> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val settings = otpSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var statusRefreshKey by remember { mutableIntStateOf(0) }
        val requestAccessibility: () -> Unit = {
            scope.launch {
                if (!OtpAccessibilitySettingsHelper.ensureAccessibilityEnabled(context)) {
                    ctx.openAccessibilitySettings()
                }
            }
        }
        OtpHubScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onExit = { ctx.navigateBackTo(AppNavKey.NotificationHub) },
            onRequestAccessibility = requestAccessibility,
            onOpenExtraction = { ctx.navigate(AppNavKey.OtpExtraction) },
            onOpenAutoFill = { ctx.navigate(AppNavKey.OtpAutoFill) },
            onOpenRules = { ctx.navigate(AppNavKey.OtpRules) },
            onOpenRecords = { ctx.navigate(AppNavKey.OtpRecords) },
            statusRefreshKey = statusRefreshKey,
            onRestartPhoneProcess = {
                PhoneProcessRestartRequester.request(context.applicationContext)
                statusRefreshKey++
            },
        )
    }

    hiltEntry<AppNavKey.OtpExtraction> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        OtpExtractionScreen(
            settings = otpSettings.toMinimalAppSettings(),
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            notificationPermissionGranted = permissions.notificationGranted,
            onRequestNotificationPermission = { ctx.requestNotificationPermission() },
            onCodeNotificationChange = viewModel::setOtpCodeNotificationEnabled,
            onCodeNotificationRetentionChange = viewModel::setOtpCodeNotificationRetentionSeconds,
            onShowCodeToastChange = viewModel::setOtpShowCodeToast,
            onCopyToClipboardChange = viewModel::setOtpCopyToClipboard,
            onBlockCodeSmsChange = viewModel::setOtpBlockCodeSmsEnabled,
            onMarkSmsReadChange = viewModel::setOtpMarkSmsReadEnabled,
            onDeleteSmsAfterExtractChange = viewModel::setOtpDeleteSmsAfterExtractEnabled,
            onOpenSmsBlacklist = { ctx.navigate(AppNavKey.OtpSmsBlacklist) },
            onOpenBlockedApps = { ctx.navigate(AppNavKey.OtpBlockedApps) },
        )
    }

    hiltEntry<AppNavKey.OtpAutoFill> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val requestAccessibility: () -> Unit = {
            scope.launch {
                if (!OtpAccessibilitySettingsHelper.ensureAccessibilityEnabled(context)) {
                    ctx.openAccessibilitySettings()
                }
            }
        }
        OtpAutoFillScreen(
            settings = otpSettings.toMinimalAppSettings(),
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            accessibilityGranted = permissions.accessibilityGranted,
            onRequestAccessibility = requestAccessibility,
            onAutoInputChange = viewModel::setOtpAutoInputEnabled,
            onAutoConfirmChange = viewModel::setOtpAutoConfirmEnabled,
            onDelayChange = viewModel::setOtpAutoInputDelayMs,
            onIntervalChange = viewModel::setOtpAutoInputIntervalMs,
            onLsposedSystemInjectChange = viewModel::setOtpLsposedSystemInjectEnabled,
        )
    }

    hiltEntry<AppNavKey.OtpRules> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        val officialRules by viewModel.officialRules.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val exportRulesLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val json = viewModel.exportUserRulesJson()
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray())
                } != null
            }.getOrDefault(false)
            viewModel.notifyRulesExported(written)
        }
        val importRulesLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                viewModel.notifyRulesExported(false)
            } else {
                viewModel.importUserRulesJson(text)
            }
        }
        OtpRulesScreen(
            settings = otpSettings.toMinimalAppSettings(),
            officialRules = officialRules,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            onRefreshOfficialRules = viewModel::refreshOfficialRules,
            onOfficialRuleEnabledChange = viewModel::setOtpOfficialRuleEnabled,
            onUserRulesChange = viewModel::setOtpUserMatchRules,
            onKeywordsRegexChange = viewModel::setOtpKeywordsRegex,
            onExportRules = { exportRulesLauncher.launch("otp-rules.json") },
            onImportRules = { importRulesLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
    }

    hiltEntry<AppNavKey.OtpRecords> {
        OtpRecordsPage(
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            onOpenTestFlow = { ctx.navigate(AppNavKey.OtpRules) },
        )
    }

    hiltEntry<AppNavKey.OtpBlockedApps> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = otpSettings.otpBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            onOpenAddApp = { ctx.navigate(AppNavKey.OtpBlockedAppsPick) },
            onRemoveBlacklistedApp = viewModel::removeOtpBlockedApp,
            titleRes = R.string.otp_blocked_apps_title,
            descriptionRes = R.string.otp_blocked_apps_desc,
            blockedSectionTitleRes = R.string.otp_blocked_apps_section_blocked,
            emptyRes = R.string.otp_blocked_apps_empty,
            removeActionDescriptionRes = R.string.otp_blocked_apps_remove,
            addSectionTitleRes = R.string.otp_blocked_apps_section_add,
            showPageHint = false,
        )
    }

    hiltEntry<AppNavKey.OtpBlockedAppsPick> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.otp_blocked_apps_section_add,
            excludePackageNames = otpSettings.otpBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.OtpBlockedApps) },
            onSelectApp = { app ->
                viewModel.addOtpBlockedApp(app.packageName)
                ctx.navigateBackTo(AppNavKey.OtpBlockedApps)
            },
        )
    }

    hiltEntry<AppNavKey.OtpSmsBlacklist> {
        val viewModel: OtpSettingsViewModel = hiltViewModel()
        val otpSettings by viewModel.otpUiSettings.collectAsStateWithLifecycle()
        OtpSmsBlacklistScreen(
            settings = otpSettings.toMinimalAppSettings(),
            onBack = { ctx.navigateBackTo(AppNavKey.OtpHub) },
            onBlacklistChange = viewModel::setOtpSmsBlacklist,
        )
    }
}
