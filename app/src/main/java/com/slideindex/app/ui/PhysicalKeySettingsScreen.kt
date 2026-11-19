package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.physicalkey.PhysicalKeyGesture
import com.slideindex.app.physicalkey.PhysicalKeySettings
import com.slideindex.app.physicalkey.PhysicalKeyStatus
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PhysicalKeySettingsScreen(
    settings: PhysicalKeySettings,
    status: PhysicalKeyStatus,
    diagnostics: String,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInterceptNativeEventsChange: (Boolean) -> Unit,
    onScreenOffTriggerChange: (Boolean) -> Unit = {},
    onMediaPlaybackPrioritizedChange: (Boolean) -> Unit,
    onLongPressTimeoutChange: (Long) -> Unit,
    onDoubleClickTimeoutChange: (Long) -> Unit,
    onOpenActionPick: (PhysicalKeyGesture) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRetry: () -> Unit,
) {
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(showDiagnostics) {
        if (showDiagnostics) {
            onRefreshDiagnostics()
        }
    }

    val screenTitle = stringResource(R.string.physical_key_title)
    val tipText = stringResource(R.string.physical_key_tip)
    val bottomHint = stringResource(R.string.physical_key_hint)

    val enableTitle = stringResource(R.string.physical_key_enable_title)
    val enableSummary = stringResource(R.string.physical_key_enable_summary)
    val statusTitle = stringResource(R.string.physical_key_status_title)
    val statusDisabled = stringResource(R.string.physical_key_status_disabled)
    val statusRunning = stringResource(R.string.physical_key_status_running)
    val statusStopped = stringResource(R.string.physical_key_status_stopped)

    val sectionPowerTitle = stringResource(R.string.physical_key_section_power)
    val powerSingleTitle = stringResource(R.string.physical_key_gesture_power_single)
    val powerLongTitle = stringResource(R.string.physical_key_gesture_power_long)
    val powerDoubleTitle = stringResource(R.string.physical_key_gesture_power_double)

    val sectionVolumeTitle = stringResource(R.string.physical_key_section_volume)
    val volUpSingleTitle = stringResource(R.string.physical_key_gesture_vol_up_single)
    val volUpLongTitle = stringResource(R.string.physical_key_gesture_vol_up_long)
    val volUpDoubleTitle = stringResource(R.string.physical_key_gesture_vol_up_double)
    val volDownSingleTitle = stringResource(R.string.physical_key_gesture_vol_down_single)
    val volDownLongTitle = stringResource(R.string.physical_key_gesture_vol_down_long)
    val volDownDoubleTitle = stringResource(R.string.physical_key_gesture_vol_down_double)
    val volBothTitle = stringResource(R.string.physical_key_gesture_vol_both_press)

    val sectionAdvancedTitle = stringResource(R.string.physical_key_section_advanced)
    val interceptNativeTitle = stringResource(R.string.physical_key_intercept_native_title)
    val interceptNativeSummary = stringResource(R.string.physical_key_intercept_native_summary)
    val screenOffTriggerTitle = stringResource(R.string.physical_key_screen_off_trigger_title)
    val screenOffTriggerSummary = stringResource(R.string.physical_key_screen_off_trigger_summary)
    val mediaPriorityTitle = stringResource(R.string.physical_key_media_priority_title)
    val mediaPrioritySummary = stringResource(R.string.physical_key_media_priority_summary)
    val longPressDurationTitle = stringResource(R.string.physical_key_long_press_duration_title)
    val doubleTapDurationTitle = stringResource(R.string.physical_key_double_tap_duration_title)
    val diagnosticsTitle = stringResource(R.string.physical_key_diagnostics_title)
    val diagnosticsDialogTitle = stringResource(R.string.physical_key_diagnostics_dialog_title)

    val channelFallback = stringResource(R.string.physical_key_status_channel_fallback)
    val channelDetail = stringResource(
        R.string.physical_key_status_channel_detail,
        status.channel.ifBlank { channelFallback },
    )
    val statusNotRunning = stringResource(R.string.physical_key_status_not_running, status.detail)
    val diagnosticsSummary = stringResource(R.string.physical_key_diagnostics_summary)
    val diagnosticsDriverInfo = stringResource(R.string.physical_key_diagnostics_driver_info)
    val diagnosticsLoading = stringResource(R.string.physical_key_diagnostics_loading)
    val closeDescription = stringResource(R.string.physical_key_close)
    val refreshDescription = stringResource(R.string.physical_key_refresh)

    SettingsScreenScaffold(title = screenTitle, onBack = onBack) {
        settingsLazyTipCard(
            key = "physicalkey-tip",
            text = tipText,
        )

        // 开关与状态
        groupedCardItems(
            keyPrefix = "physicalkey-status",
            items = buildList {
                add(
                    settingsCardScopeItem("physicalkey-enabled") {
                        SettingSwitchRow(
                            title = enableTitle,
                            subtitle = enableSummary,
                            checked = settings.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("physicalkey-status-row") {
                        val enabled = settings.enabled
                        val tone = when {
                            !enabled -> StatusTone.Neutral
                            status.running -> StatusTone.Good
                            else -> StatusTone.Bad
                        }
                        val pill = when {
                            !enabled -> statusDisabled
                            status.running -> statusRunning
                            else -> statusStopped
                        }
                        val detail = when {
                            !enabled -> statusDisabled
                            status.running -> channelDetail
                            else -> statusNotRunning
                        }
                        StatusRow(
                            title = statusTitle,
                            pill = pill,
                            tone = tone,
                            detail = detail,
                            segmentKey = "physicalkey-status-row",
                            onClick = {
                                if (!status.running && enabled) {
                                    onRetry()
                                }
                                showDiagnostics = true
                                onRefreshDiagnostics()
                            },
                        )
                    }
                )
            }
        )

        // 电源键手势
        settingsLazySmallTitle(
            key = "section-power",
            title = sectionPowerTitle,
        )
        groupedCardItems(
            keyPrefix = "physicalkey-power",
            items = listOf(
                settingsCardScopeItem("power-single") {
                    val action = settings.entries[PhysicalKeyGesture.POWER_SINGLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = powerSingleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.POWER_SINGLE_PRESS) },
                    )
                },
                settingsCardScopeItem("power-double") {
                    val action = settings.entries[PhysicalKeyGesture.POWER_DOUBLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = powerDoubleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.POWER_DOUBLE_PRESS) },
                    )
                },
                settingsCardScopeItem("power-long") {
                    val action = settings.entries[PhysicalKeyGesture.POWER_LONG_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = powerLongTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.POWER_LONG_PRESS) },
                    )
                },
            )
        )

        // 音量键手势
        settingsLazySmallTitle(
            key = "section-volume",
            title = sectionVolumeTitle,
        )
        groupedCardItems(
            keyPrefix = "physicalkey-volume",
            items = listOf(
                settingsCardScopeItem("vol-up-single") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volUpSingleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-up-double") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volUpDoubleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-up-long") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_UP_LONG_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volUpLongTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_UP_LONG_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-down-single") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_DOWN_SINGLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volDownSingleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_DOWN_SINGLE_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-down-double") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volDownDoubleTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-down-long") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volDownLongTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS) },
                    )
                },
                settingsCardScopeItem("vol-both-press") {
                    val action = settings.entries[PhysicalKeyGesture.VOLUME_BOTH_PRESS]?.action ?: GestureAction.None
                    MiuixNavigationRow(
                        title = volBothTitle,
                        summary = gestureActionLabel(action),
                        enabled = settings.enabled,
                        onClick = { onOpenActionPick(PhysicalKeyGesture.VOLUME_BOTH_PRESS) },
                    )
                },
            )
        )

        // 高级与灵敏度
        settingsLazySmallTitle(
            key = "section-advanced",
            title = sectionAdvancedTitle,
        )
        groupedCardItems(
            keyPrefix = "physicalkey-advanced",
            items = listOf(
                settingsCardScopeItem("intercept-native") {
                    SettingSwitchRow(
                        title = interceptNativeTitle,
                        subtitle = interceptNativeSummary,
                        checked = settings.interceptNativeEvents,
                        enabled = settings.enabled,
                        onCheckedChange = onInterceptNativeEventsChange,
                    )
                },
                settingsCardScopeItem("screen-off-trigger") {
                    SettingSwitchRow(
                        title = screenOffTriggerTitle,
                        subtitle = screenOffTriggerSummary,
                        checked = settings.screenOffTriggerEnabled,
                        enabled = settings.enabled,
                        onCheckedChange = onScreenOffTriggerChange,
                    )
                },
                settingsCardScopeItem("media-priority") {
                    SettingSwitchRow(
                        title = mediaPriorityTitle,
                        subtitle = mediaPrioritySummary,
                        checked = settings.mediaPlaybackPrioritized,
                        enabled = settings.enabled,
                        onCheckedChange = onMediaPlaybackPrioritizedChange,
                    )
                },
                settingsCardScopeItem("long-press-timeout") {
                    SettingsSliderRow(
                        title = longPressDurationTitle,
                        value = settings.longPressTimeoutMs.toFloat(),
                        valueRange = 300f..1000f,
                        steps = 14,
                        enabled = settings.enabled,
                        label = "${settings.longPressTimeoutMs} ms",
                        formatLabel = { "${it.toInt()} ms" },
                        onValueChange = { onLongPressTimeoutChange(it.toLong()) },
                    )
                },
                settingsCardScopeItem("double-tap-timeout") {
                    SettingsSliderRow(
                        title = doubleTapDurationTitle,
                        value = settings.doubleClickTimeoutMs.toFloat(),
                        valueRange = 200f..600f,
                        steps = 8,
                        enabled = settings.enabled,
                        label = "${settings.doubleClickTimeoutMs} ms",
                        formatLabel = { "${it.toInt()} ms" },
                        onValueChange = { onDoubleClickTimeoutChange(it.toLong()) },
                    )
                },
            )
        )

        settingsLazyHint(key = "bottom-hint", text = bottomHint)
    }

    // 诊断弹窗
    MiuixBottomSheet(
        show = showDiagnostics,
        title = diagnosticsDialogTitle,
        startAction = {
            IconButton(onClick = { showDiagnostics = false }) {
                MiuixIcon(
                    imageVector = MiuixIcons.Medium.Close,
                    contentDescription = closeDescription,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    onRetry()
                    onRefreshDiagnostics()
                },
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = refreshDescription,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        onDismissRequest = { showDiagnostics = false },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SmallTitle(
                    text = diagnosticsDriverInfo,
                    insideMargin = PaddingValues(16.dp, 8.dp),
                )
                Card(
                    modifier = Modifier.padding(bottom = 16.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    SelectionContainer {
                        MiuixText(
                            text = diagnostics.ifBlank { diagnosticsLoading },
                            style = MiuixTheme.textStyles.body2,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
