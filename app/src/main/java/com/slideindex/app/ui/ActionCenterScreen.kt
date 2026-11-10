package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        actionCenterGrid(
            key = "action-center-launch-grid",
            entries = ActionCenterLaunchEntry.entries,
            entryContent = { entry ->
                val targetAction = actions.firstOrNull { it.type == entry.actionType }
                ActionGridCell(
                    icon = entry.icon,
                    label = stringResource(entry.titleRes),
                    hasParams = false,
                    onClick = { targetAction?.let(onOpenActionDetail) },
                )
            },
        )
        if (configurable.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "action-center-configurable",
                title = configurableTitle,
            )
            actionCenterActionGrid(
                keyPrefix = "action-center-configurable",
                actions = configurable,
                onOpenActionDetail = onOpenActionDetail,
            )
        }
        if (plain.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "action-center-plain",
                title = plainTitle,
            )
            actionCenterActionGrid(
                keyPrefix = "action-center-plain",
                actions = plain,
                onOpenActionDetail = onOpenActionDetail,
            )
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

private fun LazyListScope.actionCenterActionGrid(
    keyPrefix: String,
    actions: List<GestureAction>,
    onOpenActionDetail: (GestureAction) -> Unit,
) {
    actions.chunked(ACTION_GRID_COLUMNS).forEachIndexed { rowIndex, rowActions ->
        item(key = "$keyPrefix-row-$rowIndex") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowActions.forEach { action ->
                    Box(modifier = Modifier.weight(1f)) {
                        ActionGridCell(
                            icon = gestureActionTypeOutlinedIcon(action.type),
                            label = gestureActionLabel(action),
                            hasParams = actionHasDetailParams(action.type),
                            onClick = { onOpenActionDetail(action) },
                        )
                    }
                }
                repeat(ACTION_GRID_COLUMNS - rowActions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun <T> LazyListScope.actionCenterGrid(
    key: String,
    entries: List<T>,
    entryContent: @Composable (T) -> Unit,
) {
    entries.chunked(ACTION_GRID_COLUMNS).forEachIndexed { rowIndex, rowEntries ->
        item(key = "$key-row-$rowIndex") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) {
                        entryContent(entry)
                    }
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
    icon: ImageVector,
    label: String,
    hasParams: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
            if (hasParams) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}
