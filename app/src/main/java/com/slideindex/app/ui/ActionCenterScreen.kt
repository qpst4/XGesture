package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.ui.gesturepicker.ACTION_GRID_COLUMNS
import com.slideindex.app.ui.gesturepicker.actionHasDetailParams
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「内置动作」浏览页：第一段是面板/启动器入口（含应用索引动作），
 * 后面按「可配置动作 → 普通动作」排；第一段出现的动作类型不再在下面重复。
 */
@Composable
fun ActionCenterScreen(
    onBack: () -> Unit,
    onOpenActionDetail: (GestureAction) -> Unit,
) {
    val actions = remember {
        GestureActionCatalog.build(
            scope = GestureActionCatalogScope.GesturePicker,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
        ).filter { it.type != GestureActionType.NONE }
    }
    val launchTypes = remember { ActionCenterLaunchEntry.entries.map { it.actionType }.toSet() }
    val configurable = remember(actions, launchTypes) {
        actions.filter { actionHasDetailParams(it.type) && it.type !in launchTypes }
    }
    val plain = remember(actions, launchTypes) {
        actions.filter { !actionHasDetailParams(it.type) && it.type !in launchTypes }
    }
    // SettingsLazyScreenScaffold 的 content 是 LazyListScope（非 @Composable），标题必须提前取好。
    val launchPanelTitle = stringResource(R.string.extension_section_launch_panel)
    val configurableTitle = stringResource(R.string.action_center_configurable_section)
    val plainTitle = stringResource(R.string.action_center_plain_section)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.extension_action_center_title),
        pageHint = stringResource(R.string.extension_action_center_subtitle),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "action-center-launch-panel",
            title = launchPanelTitle,
        )
        actionCenterRows(
            keyPrefix = "action-center-launch-grid",
            entries = ActionCenterLaunchEntry.entries,
        ) { entry, cellModifier ->
            val targetAction = actions.firstOrNull { it.type == entry.actionType }
            ActionGridCell(
                modifier = cellModifier,
                icon = entry.icon,
                label = stringResource(entry.titleRes),
                hasParams = false,
                onClick = { targetAction?.let(onOpenActionDetail) },
            )
        }
        if (configurable.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "action-center-configurable",
                title = configurableTitle,
            )
            actionCenterRows(
                keyPrefix = "action-center-configurable",
                entries = configurable,
            ) { action, cellModifier ->
                ActionGridCell(
                    modifier = cellModifier,
                    icon = gestureActionTypeOutlinedIcon(action.type),
                    label = gestureActionLabel(action),
                    hasParams = true,
                    onClick = { onOpenActionDetail(action) },
                )
            }
        }
        if (plain.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "action-center-plain",
                title = plainTitle,
            )
            actionCenterRows(
                keyPrefix = "action-center-plain",
                entries = plain,
            ) { action, cellModifier ->
                ActionGridCell(
                    modifier = cellModifier,
                    icon = gestureActionTypeOutlinedIcon(action.type),
                    label = gestureActionLabel(action),
                    hasParams = false,
                    onClick = { onOpenActionDetail(action) },
                )
            }
        }
    }
}

/** 网格第一段：面板/启动器/应用索引入口，全部走动作详情页。 */
enum class ActionCenterLaunchEntry(
    val titleRes: Int,
    val icon: ImageVector,
    val actionType: GestureActionType,
) {
    OpenIndex(R.string.gesture_action_open_index, HubLeadingIcons.layoutSettings(true), GestureActionType.OPEN_INDEX),
    QuickLauncher(
        R.string.quick_launcher_editor_title,
        HubLeadingIcons.quickLauncher(true),
        GestureActionType.QUICK_LAUNCHER,
    ),
    HoneycombLauncher(
        R.string.honeycomb_launcher_editor_title,
        HubLeadingIcons.honeycombLauncher(true),
        GestureActionType.HONEYCOMB_LAUNCHER,
    ),
    RingLauncher(
        R.string.fv_ring_launcher_entry_title,
        HubLeadingIcons.ringLauncher(true),
        GestureActionType.APP_RING_LAUNCHER,
    ),
    HolographicLauncher(
        R.string.holographic_launcher_entry_title,
        HubLeadingIcons.holographicLauncher(true),
        GestureActionType.HOLOGRAPHIC_LAUNCHER,
    ),
    WidgetPanel(
        R.string.widget_panel_settings_title,
        HubLeadingIcons.widgetPanel(true),
        GestureActionType.WIDGET_POPUP_OVERLAY,
    ),
    QuickWheel(
        R.string.extension_quick_wheel_entry_title,
        gestureActionTypeOutlinedIcon(GestureActionType.QUICK_WHEEL),
        GestureActionType.QUICK_WHEEL,
    ),
    ShellPanel(
        R.string.shell_panel_entry_title,
        HubLeadingIcons.shellCommand(true),
        GestureActionType.SHELL_COMMAND_PANEL,
    ),
}

/**
 * 网格行：和「选择操作类型」页同一套做法 —— 格子直接吃 `weight(1f)`（不是包在 Box 里再让
 * 内容自己定宽），所以每列宽度严格相等，图标和文字都居中于列宽，同列上下一定在一条竖线上。
 */
private fun <T> LazyListScope.actionCenterRows(
    keyPrefix: String,
    entries: List<T>,
    cell: @Composable (T, Modifier) -> Unit,
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
                    cell(entry, Modifier.weight(1f))
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
    icon: ImageVector,
    label: String,
    hasParams: Boolean,
    onClick: () -> Unit,
) {
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
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(27.dp),
                )
            }
            if (hasParams) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
