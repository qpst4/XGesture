package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.notification.NotificationHistoryAppGroup
import com.slideindex.app.notification.NotificationHistoryListEntry
import com.slideindex.app.notification.groupNotificationHistoryByApp

private sealed interface NotificationHistoryLazyRow<T> {
    data class DateHeader<T>(val dayStartMs: Long) : NotificationHistoryLazyRow<T>

    data class Single<T>(val item: T) : NotificationHistoryLazyRow<T>

    data class GroupHeader<T>(
        val group: NotificationHistoryAppGroup<T>,
    ) : NotificationHistoryLazyRow<T>

    data class GroupChild<T>(
        val packageName: String,
        val item: T,
    ) : NotificationHistoryLazyRow<T>
}

private fun <T> buildNotificationHistoryLazyRows(
    items: List<T>,
    groupByApp: Boolean,
    searchActive: Boolean,
    expandedGroups: Set<String>,
    keyOf: (T) -> String,
    packageNameOf: (T) -> String,
    postedAtMsOf: (T) -> Long,
): List<NotificationHistoryLazyRow<T>> {
    val rows: List<NotificationHistoryLazyRow<T>> = if (!groupByApp || searchActive) {
        items.map { NotificationHistoryLazyRow.Single(it) }
    } else {
        buildList {
            val entries = groupNotificationHistoryByApp(
                items = items,
                packageNameOf = packageNameOf,
                postedAtMsOf = postedAtMsOf,
            )
            entries.forEach { entry ->
                when (entry) {
                    is NotificationHistoryListEntry.Single -> {
                        add(NotificationHistoryLazyRow.Single(entry.item))
                    }
                    is NotificationHistoryListEntry.CollapsedGroup -> {
                        val packageName = entry.group.packageName
                        add(NotificationHistoryLazyRow.GroupHeader(entry.group))
                        if (packageName in expandedGroups) {
                            entry.group.items.forEach { item ->
                                add(NotificationHistoryLazyRow.GroupChild(packageName, item))
                            }
                        }
                    }
                }
            }
        }
    }
    return rows.withDateHeaders(postedAtMsOf)
}

private fun <T> List<NotificationHistoryLazyRow<T>>.withDateHeaders(
    postedAtMsOf: (T) -> Long,
): List<NotificationHistoryLazyRow<T>> {
    if (isEmpty()) return this
    val result = ArrayList<NotificationHistoryLazyRow<T>>(size + 2)
    var currentDay: Long? = null
    for (row in this) {
        when (row) {
            is NotificationHistoryLazyRow.DateHeader -> Unit
            is NotificationHistoryLazyRow.GroupChild -> result.add(row)
            is NotificationHistoryLazyRow.Single -> {
                currentDay = result.addDateHeaderIfNeeded(currentDay, postedAtMsOf(row.item))
                result.add(row)
            }
            is NotificationHistoryLazyRow.GroupHeader -> {
                currentDay = result.addDateHeaderIfNeeded(currentDay, row.group.latestPostedAtMs)
                result.add(row)
            }
        }
    }
    return result
}

private fun <T> MutableList<NotificationHistoryLazyRow<T>>.addDateHeaderIfNeeded(
    currentDay: Long?,
    postedAtMs: Long,
): Long {
    val day = startOfLocalDay(postedAtMs)
    if (day != currentDay) {
        add(NotificationHistoryLazyRow.DateHeader(day))
    }
    return day
}

private fun NotificationHistoryLazyRow<*>.isTimelineRow(): Boolean {
    return this is NotificationHistoryLazyRow.Single<*> ||
        this is NotificationHistoryLazyRow.GroupChild<*>
}

private fun <T> NotificationHistoryLazyRow<T>.lazyRowKey(keyOf: (T) -> String): String {
    return when (this) {
        is NotificationHistoryLazyRow.DateHeader -> "date-$dayStartMs"
        is NotificationHistoryLazyRow.Single -> "single-${keyOf(item)}"
        is NotificationHistoryLazyRow.GroupHeader -> "group-header-${group.packageName}"
        is NotificationHistoryLazyRow.GroupChild -> "group-$packageName-${keyOf(item)}"
    }
}

internal fun <T> LazyListScope.emitGroupedOrFlatNotificationItems(
    items: List<T>,
    groupByApp: Boolean,
    searchActive: Boolean,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    keyOf: (T) -> String,
    packageNameOf: (T) -> String,
    postedAtMsOf: (T) -> Long,
    groupHeaderContent: @Composable (packageName: String, count: Int, latestItem: T, expanded: Boolean) -> Unit,
    rowContent: @Composable (item: T, showTopLine: Boolean, showBottomLine: Boolean) -> Unit,
) {
    val lazyRows = buildNotificationHistoryLazyRows(
        items = items,
        groupByApp = groupByApp,
        searchActive = searchActive,
        expandedGroups = expandedGroups,
        keyOf = keyOf,
        packageNameOf = packageNameOf,
        postedAtMsOf = postedAtMsOf,
    )
    itemsIndexed(
        items = lazyRows,
        key = { _, row -> row.lazyRowKey(keyOf) },
    ) { index, row ->
        when (row) {
            is NotificationHistoryLazyRow.DateHeader -> {
                NotificationHistoryDateHeader(row.dayStartMs)
            }
            is NotificationHistoryLazyRow.Single -> {
                Box {
                    rowContent(
                        row.item,
                        lazyRows.getOrNull(index - 1)?.isTimelineRow() == true,
                        lazyRows.getOrNull(index + 1)?.isTimelineRow() == true,
                    )
                }
            }
            is NotificationHistoryLazyRow.GroupHeader -> {
                val packageName = row.group.packageName
                val expanded = packageName in expandedGroups
                Box(Modifier.padding(bottom = if (expanded) 8.dp else 12.dp)) {
                    groupHeaderContent(
                        packageName,
                        row.group.items.size,
                        row.group.items.first(),
                        expanded,
                    )
                }
            }
            is NotificationHistoryLazyRow.GroupChild -> {
                Box(Modifier.padding(start = 12.dp)) {
                    rowContent(
                        row.item,
                        lazyRows.getOrNull(index - 1)?.isTimelineRow() == true,
                        lazyRows.getOrNull(index + 1)?.isTimelineRow() == true,
                    )
                }
            }
        }
    }
}
