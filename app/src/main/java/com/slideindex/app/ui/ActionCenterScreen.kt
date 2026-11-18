package com.slideindex.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.gesturepicker.ACTION_GRID_COLUMNS
import com.slideindex.app.ui.gesturepicker.gestureActionDescriptionText
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.gestureActionSearchAliases
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import com.slideindex.app.ui.miuix.LocalMiuixScreenBackdrop
import com.slideindex.app.ui.miuix.LocalMiuixContentUnderBar
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 内置动作浏览页：按动作类型全量列出，分类分段 + 特殊入口角标 + 筛选。 */
@Composable
fun ActionCenterScreen(
    onBack: () -> Unit,
    onOpenActionDetail: (GestureAction) -> Unit,
) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(ActionCenterFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val entries = ActionCenterCatalog.entries
    val filtered = remember(entries, filter, searchQuery, context) {
        val byFilter = entries.filter { ActionCenterCatalog.matches(it, filter) }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            byFilter
        } else {
            byFilter.filter { entry ->
                val label = gestureActionLabelText(context, entry.sample)
                val aliases = gestureActionSearchAliases(context, entry.sample)
                val desc = gestureActionDescriptionText(context, entry.sample)
                label.lowercase().contains(q) ||
                    PinyinHelper.sortKey(label).contains(q) ||
                    aliases.any { it.lowercase().contains(q) || PinyinHelper.sortKey(it).contains(q) } ||
                    desc?.lowercase()?.contains(q) == true
            }
        }
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

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.extension_action_center_title),
        pageHint = stringResource(R.string.extension_action_center_subtitle),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        extraBottomContent = {
            ActionCenterFilterRow(
                selected = filter,
                onSelect = { filter = it },
            )
        },
    ) {
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
    val tabs = ActionCenterFilter.entries.map { stringResource(it.labelRes) }
    // 与通知滤盒同源：内容滚到栏下才升起玻璃；无背板（浮层窗口）时退回实色 pills。
    val backdrop = LocalMiuixScreenBackdrop.current ?: LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        // 静息态：未选中是灰色胶囊；内容滚到栏下（材质升起）才交给玻璃（miuix neutralColors 的设计意图）。
        val contentUnderBar = LocalMiuixContentUnderBar.current.value
        GlassTabRow(
            tabs = tabs,
            selectedIndex = ActionCenterFilter.entries.indexOf(selected).coerceAtLeast(0),
            onSelect = { index -> ActionCenterFilter.entries.getOrNull(index)?.let(onSelect) },
            backdrop = backdrop,
            surfaceAlpha = if (contentUnderBar) 1f else 0f,
            height = GlassTabRowDefaults.Height,
            // 选中态用主题蓝（同开关），静息态未选中仍是灰色胶囊：取中性套的静息灰 + 换掉选中色。
            colors = GlassTabRowDefaults.neutralColors().copy(
                selectedContainerColor = MiuixTheme.colorScheme.primary,
                selectedContentColor = androidx.compose.ui.graphics.Color.White,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    } else {
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
}

@Composable
private fun ActionCenterFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "action-center-filter-press",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
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
        ActionCenterFilter.PANEL -> R.string.action_center_filter_panel
        ActionCenterFilter.CONFIGURABLE -> R.string.action_center_filter_configurable
    }
