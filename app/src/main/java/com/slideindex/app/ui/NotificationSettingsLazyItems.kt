package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

data class NotificationSettingsLazyGroups(
    val restoreSnoozed: List<CardItem>,
    val historyMaxCount: List<CardItem>,
)

@Composable
fun rememberNotificationSettingsLazyGroups(
    filterSettings: NotificationFilterSettings,
    listenerEnabled: Boolean,
    onRequestListenerAccess: () -> Unit,
    onSetNotificationHistoryEnabled: (Boolean) -> Unit,
    onSetNotificationHistoryMaxCount: (Int) -> Unit,
    onSetGroupHistoryByApp: (Boolean) -> Unit,
    onRestoreAllSnoozed: (Boolean) -> Int,
): NotificationSettingsLazyGroups {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val restoreEmptyMessage = stringResource(R.string.notification_restore_snoozed_empty)
    val restoreDesc = stringResource(R.string.notification_restore_snoozed_desc)
    val restoreAction = stringResource(R.string.notification_restore_snoozed_action)

    val restoreSnoozed = remember(
        listenerEnabled,
        restoreDesc,
        restoreAction,
        restoreEmptyMessage,
    ) {
        listOf(
            CardItem("desc") {
                Text(
                    text = restoreDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            },
            CardItem("button") {
                Button(
                    onClick = {
                        if (!listenerEnabled) {
                            Toast.makeText(
                                context,
                                R.string.notification_hide_listener_required,
                                Toast.LENGTH_SHORT,
                            ).show()
                            onRequestListenerAccess()
                            return@Button
                        }
                        val restored = onRestoreAllSnoozed(listenerEnabled)
                        if (restored < 0) return@Button
                        Toast.makeText(
                            context,
                            if (restored > 0) {
                                appContext.getString(R.string.notification_restore_snoozed_result, restored)
                            } else {
                                restoreEmptyMessage
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(restoreAction)
                }
            },
        )
    }

    val formatMaxCountLabel = remember(appContext) {
        { value: Float ->
            appContext.getString(
                R.string.notification_history_max_count_value,
                value.roundToInt(),
            )
        }
    }
    val maxCountRange = NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT.toFloat()..
        NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT.toFloat()
    val maxCountSteps = (
        (NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT -
            NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT) /
            NotificationFilterPreferences.NOTIFICATION_HISTORY_MAX_COUNT_STEP
        ) - 1
    val snapMaxCount: (Float) -> Float = { value ->
        val step = NotificationFilterPreferences.NOTIFICATION_HISTORY_MAX_COUNT_STEP
        val snapped = ((value / step).roundToInt() * step)
            .coerceIn(
                NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
                NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
            )
        snapped.toFloat()
    }

    val historyMaxCount = remember(filterSettings, formatMaxCountLabel, maxCountRange, maxCountSteps) {
        listOf(
            settingsCardScopeItem("history-enabled") {
                SettingSwitchRow(
                    title = stringResource(R.string.notification_history_enabled_title),
                    subtitle = stringResource(R.string.notification_history_enabled_desc),
                    checked = filterSettings.notificationHistoryEnabled,
                    enabled = true,
                    onCheckedChange = onSetNotificationHistoryEnabled,
                )
            },
            settingsCardScopeItem("group-by-app") {
                SettingSwitchRow(
                    title = stringResource(R.string.notification_history_group_by_app_title),
                    subtitle = stringResource(R.string.notification_history_group_by_app_desc),
                    checked = filterSettings.groupHistoryByApp,
                    enabled = filterSettings.notificationHistoryEnabled,
                    onCheckedChange = onSetGroupHistoryByApp,
                )
            },
            settingsCardScopeItem("history-max-count") {
                SettingsSliderRow(
                    title = stringResource(R.string.notification_history_max_count_title),
                    value = filterSettings.notificationHistoryMaxCount.toFloat(),
                    valueRange = maxCountRange,
                    steps = maxCountSteps,
                    enabled = filterSettings.notificationHistoryEnabled,
                    label = formatMaxCountLabel(filterSettings.notificationHistoryMaxCount.toFloat()),
                    formatLabel = formatMaxCountLabel,
                    snapValue = snapMaxCount,
                    onValueChange = { value ->
                        val count = snapMaxCount(value).roundToInt()
                        onSetNotificationHistoryMaxCount(count)
                    },
                )
            },
        )
    }

    return remember(restoreSnoozed, historyMaxCount) {
        NotificationSettingsLazyGroups(
            restoreSnoozed = restoreSnoozed,
            historyMaxCount = historyMaxCount,
        )
    }
}

fun LazyListScope.emitNotificationSettingsItems(
    groups: NotificationSettingsLazyGroups,
    historySectionTitle: String,
) {
    groupedCardItems(
        keyPrefix = "notification-restore",
        items = groups.restoreSnoozed,
    )
    settingsLazySmallTitle(key = "history_section", title = historySectionTitle)
    groupedCardItems(
        keyPrefix = "notification-history-max",
        items = groups.historyMaxCount,
    )
}
