package com.slideindex.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.gesturepicker.ACTION_GRID_COLUMNS
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 内置动作浏览页：按动作类型全量列出，分类分段 + 特殊入口角标 + 筛选。 */
@Composable
fun ActionCenterScreen(
    onBack: () -> Unit,
    onOpenActionDetail: (GestureAction) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(ActionCenterFilter.ALL) }
    val entries = ActionCenterCatalog.entries
    val filtered = remember(entries, filter) {
        entries.filter { ActionCenterCatalog.matches(it, filter) }
    }
    val sections = remember(filtered) {
        ActionCenterSection.entries.mapNotNull { section ->
            val items = filtered.filter { it.section == section }
            if (items.isEmpty()) null else section to items
        }
    }
    // content 是 LazyListScope（非组合作用域），标题必须提前取好。
    val sectionTitles = ActionCenterSection.entries.associateWith { stringResource(it.titleRes) }
    val emptyText = stringResource(R.string.search_no_actions)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.extension_action_center_title),
        pageHint = stringResource(R.string.extension_action_center_subtitle),
        onBack = onBack,
    ) {
        item(key = "action-center-filter") {
            ActionCenterFilterRow(
                selected = filter,
                onSelect = { filter = it },
            )
        }
        sections.forEach { (section, items) ->
            settingsLazySmallTitle(
                key = "action-center-section-${section.name}",
                title = sectionTitles.getValue(section),
            )
            actionCenterRows(
                keyPrefix = "action-center-${section.name}",
                entries = items,
                onOpen = onOpenActionDetail,
            )
        }
        if (sections.isEmpty()) {
            item(key = "action-center-empty") {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionCenterFilterRow(
    selected: ActionCenterFilter,
    onSelect: (ActionCenterFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionCenterFilter.entries.forEach { filter ->
            ActionCenterFilterPill(
                label = stringResource(filter.labelRes),
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun ActionCenterFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainer
            },
            maxLines = 1,
        )
    }
}

private fun LazyListScope.actionCenterRows(
    keyPrefix: String,
    entries: List<BuiltInActionEntry>,
    onOpen: (GestureAction) -> Unit,
) {
    entries.chunked(ACTION_GRID_COLUMNS).forEachIndexed { rowIndex, rowEntries ->
        item(key = "$keyPrefix-row-$rowIndex") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowEntries.forEach { entry ->
                    ActionGridCell(
                        modifier = Modifier.weight(1f),
                        entry = entry,
                        onClick = { onOpen(entry.sample) },
                    )
                }
                repeat(ACTION_GRID_COLUMNS - rowEntries.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionGridCell(
    modifier: Modifier,
    entry: BuiltInActionEntry,
    onClick: () -> Unit,
) {
    val label = gestureActionLabel(entry.sample)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                ActionIconSlot(action = entry.sample, sizeDp = 27)
            }
            entry.special?.let { mark ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(mark.badgeRes),
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                    )
                }
            }
            if (entry.needsParams) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@get:StringRes
private val ActionCenterFilter.labelRes: Int
    get() = when (this) {
        ActionCenterFilter.ALL -> R.string.action_center_filter_all
        ActionCenterFilter.COMMON -> R.string.action_center_filter_common
        ActionCenterFilter.SPECIAL -> R.string.action_center_filter_special
    }
