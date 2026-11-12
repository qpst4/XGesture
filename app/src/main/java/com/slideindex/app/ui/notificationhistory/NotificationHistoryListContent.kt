package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.ActiveNotificationEntry
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryItemMeta
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
internal fun rememberResolvableAppInfo(
    viewModel: NotificationHistoryViewModel,
    packageName: String,
): AppInfo? {
    var appInfo by remember(packageName) {
        mutableStateOf(viewModel.getCachedAppInfo(packageName))
    }
    LaunchedEffect(packageName) {
        if (appInfo == null) {
            appInfo = viewModel.resolveAppInfo(packageName)
        }
    }
    return appInfo
}

internal fun LazyListScope.activeNotificationsItems(
    listenerEnabled: Boolean,
    activeNotifications: List<ActiveNotificationEntry>,
    groupByApp: Boolean,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    timeFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
) {
    if (!listenerEnabled) {
        item(key = "active_permission_hint") {
            Text(
                text = stringResource(R.string.notification_history_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else if (activeNotifications.isEmpty()) {
        item(key = "active_empty") {
            Text(
                text = stringResource(R.string.notification_filter_active_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else {
        emitGroupedOrFlatNotificationItems(
            items = activeNotifications,
            groupByApp = groupByApp,
            searchActive = false,
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
            keyOf = { it.key },
            packageNameOf = { it.packageName },
            postedAtMsOf = { it.postedAtMs },
            groupHeaderContent = { packageName, count, entry, expanded ->
                val displayItem = entry.historyItem ?: entry.toHistoryItem()
                val appInfo = rememberResolvableAppInfo(viewModel, packageName)
                NotificationHistoryAppGroupHeader(
                    appInfo = appInfo,
                    packageName = packageName,
                    count = count,
                    latestItem = displayItem,
                    timeLabel = timeFormat.format(Date(entry.postedAtMs)),
                    expanded = expanded,
                    onToggle = { onToggleGroup(packageName) },
                )
            },
            rowContent = { entry, showTopLine, showBottomLine ->
                val historyItem = entry.historyItem
                val displayItem = historyItem ?: entry.toHistoryItem()
                val meta = historyItem?.let(itemMeta) ?: itemMeta(displayItem)
                val appInfo = rememberResolvableAppInfo(viewModel, entry.packageName)
                NotificationHistoryRow(
                    item = displayItem,
                    appInfo = appInfo,
                    timeLabel = timeFormat.format(Date(entry.postedAtMs)),
                    isActiveInShade = false,
                    matchingRule = meta.matchingHideRule,
                    showHiddenBadge = meta.isHidden,
                    showRestoreAction = meta.isHidden,
                    showDeleteAction = historyItem != null,
                    showTopLine = showTopLine,
                    showBottomLine = showBottomLine,
                    onOpen = { viewModel.replayActive(entry) },
                    onHide = {
                        onHideItem(displayItem, historyItem?.id)
                    },
                    onUnhide = {
                        viewModel.restoreSnoozed(historyItem ?: displayItem)
                    },
                    onDelete = {
                        historyItem?.let { item -> viewModel.deleteItem(item.id) }
                    },
                )
            },
        )
    }
}

internal fun LazyListScope.historyNotificationsItems(
    items: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    groupByApp: Boolean,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    timeFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    if (items.isEmpty()) {
        item(key = "history_empty") {
            Text(
                text = stringResource(R.string.notification_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else if (filteredItems.isEmpty()) {
        item(key = "search_empty") {
            Text(
                text = stringResource(R.string.notification_history_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else {
        emitGroupedOrFlatNotificationItems(
            items = filteredItems,
            groupByApp = groupByApp,
            searchActive = searchQuery.isNotBlank(),
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
            keyOf = { it.id },
            packageNameOf = { it.packageName },
            postedAtMsOf = { it.postedAtMs },
            groupHeaderContent = { packageName, count, item, expanded ->
                val appInfo = rememberResolvableAppInfo(viewModel, packageName)
                NotificationHistoryAppGroupHeader(
                    appInfo = appInfo,
                    packageName = packageName,
                    count = count,
                    latestItem = item,
                    timeLabel = timeFormat.format(Date(item.postedAtMs)),
                    expanded = expanded,
                    onToggle = { onToggleGroup(packageName) },
                )
            },
            rowContent = { item, showTopLine, showBottomLine ->
                val meta = itemMeta(item)
                val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                NotificationHistoryRow(
                    item = item,
                    appInfo = appInfo,
                    timeLabel = timeFormat.format(Date(item.postedAtMs)),
                    isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                    matchingRule = meta.matchingHideRule,
                    showHiddenBadge = meta.isHidden,
                    showRestoreAction = false,
                    showTopLine = showTopLine,
                    showBottomLine = showBottomLine,
                    onOpen = { viewModel.replay(item) },
                    onHide = { onHideItem(item, item.id) },
                    onUnhide = {},
                    onDelete = { onDelete(item) },
                )
            },
        )
    }
}

internal fun LazyListScope.hiddenNotificationsItems(
    hiddenItems: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    groupByApp: Boolean,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    timeFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    if (hiddenItems.isEmpty()) {
        item(key = "hidden_empty") {
            Text(
                text = stringResource(R.string.notification_filter_hidden_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else if (filteredItems.isEmpty()) {
        item(key = "hidden_search_empty") {
            Text(
                text = stringResource(R.string.notification_history_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
    } else {
        emitGroupedOrFlatNotificationItems(
            items = filteredItems,
            groupByApp = groupByApp,
            searchActive = searchQuery.isNotBlank(),
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
            keyOf = { it.id },
            packageNameOf = { it.packageName },
            postedAtMsOf = { it.postedAtMs },
            groupHeaderContent = { packageName, count, item, expanded ->
                val appInfo = rememberResolvableAppInfo(viewModel, packageName)
                NotificationHistoryAppGroupHeader(
                    appInfo = appInfo,
                    packageName = packageName,
                    count = count,
                    latestItem = item,
                    timeLabel = timeFormat.format(Date(item.postedAtMs)),
                    expanded = expanded,
                    onToggle = { onToggleGroup(packageName) },
                )
            },
            rowContent = { item, showTopLine, showBottomLine ->
                val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                NotificationHistoryRow(
                    item = item,
                    appInfo = appInfo,
                    timeLabel = timeFormat.format(Date(item.postedAtMs)),
                    isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                    matchingRule = itemMeta(item).matchingHideRule,
                    showHiddenBadge = true,
                    showRestoreAction = true,
                    showTopLine = showTopLine,
                    showBottomLine = showBottomLine,
                    onOpen = { viewModel.replay(item) },
                    onHide = {},
                    onUnhide = { viewModel.restoreSnoozed(item) },
                    onDelete = { onDelete(item) },
                )
            },
        )
    }
}

internal fun ActiveNotificationEntry.toHistoryItem(): NotificationHistoryItem {
    return NotificationHistoryItem(
        packageName = packageName,
        title = title,
        text = text,
        postedAtMs = postedAtMs,
        intentUri = null,
        notificationKey = key,
        channelId = channelId,
    )
}
