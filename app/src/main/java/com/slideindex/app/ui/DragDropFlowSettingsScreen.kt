package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.DragDropFlowPosition
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard

/** 下拉框按枚举顺序展示，文案走资源，别再在这里写死中文。 */
private val DragDropFlowPosition.labelRes: Int
    get() = when (this) {
        DragDropFlowPosition.BOTTOM -> R.string.drag_drop_flow_position_bottom
        DragDropFlowPosition.LEFT -> R.string.drag_drop_flow_position_left
        DragDropFlowPosition.RIGHT -> R.string.drag_drop_flow_position_right
        DragDropFlowPosition.RING -> R.string.drag_drop_flow_position_ring
    }

@Composable
fun DragDropFlowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPositionChange: (DragDropFlowPosition) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onActionsInlineChange: (Boolean) -> Unit,
    onOpenTextSearchEngines: (() -> Unit)? = null,
    onOpenImageSearchEngines: (() -> Unit)? = null,
    onOpenFileShareEngines: (() -> Unit)? = null,
) {
    val currentPosition = settings.dragDropFlowPosition
    val positionLabels = DragDropFlowPosition.entries.map { stringResource(it.labelRes) }
    val positionIndex = currentPosition.id.coerceIn(0, DragDropFlowPosition.entries.lastIndex)

    // LazyListScope 顶层（settingsLazySmallTitle / settingsLazyTipCard）不是 @Composable 上下文，
    // 文案必须在这里先取出来。
    val sectionBasic = stringResource(R.string.drag_drop_flow_section_basic)
    val sectionLayout = stringResource(R.string.drag_drop_flow_section_layout)
    val sectionTargets = stringResource(R.string.drag_drop_flow_section_targets)
    val guideText = stringResource(R.string.drag_drop_flow_guide)

    SettingsScreenScaffold(
        title = stringResource(R.string.drag_drop_flow_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "drag_drop_flow_basic_section",
            title = sectionBasic,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_basic",
            items = buildList {
                add(
                    settingsCardScopeItem("switch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_enable_title),
                            subtitle = stringResource(R.string.drag_drop_flow_enable_summary),
                            icon = { label: String -> Icon(Icons.Outlined.PowerSettingsNew, contentDescription = label) },
                            checked = settings.dragDropFlowEnabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "drag_drop_flow_layout_section",
            title = sectionLayout,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_layout",
            items = buildList {
                add(
                    settingsCardScopeItem("position") {
                        SettingDropdownRow(
                            title = stringResource(R.string.drag_drop_flow_position_title),
                            subtitle = stringResource(R.string.drag_drop_flow_position_summary),
                            items = positionLabels,
                            selectedIndex = positionIndex,
                            enabled = settings.dragDropFlowEnabled,
                            icon = { label: String -> Icon(Icons.Outlined.OpenWith, contentDescription = label) },
                            onSelectedIndexChange = { index ->
                                val target = DragDropFlowPosition.fromId(index)
                                onPositionChange(target)
                            },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("haptic") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_haptic_title),
                            subtitle = stringResource(R.string.drag_drop_flow_haptic_summary),
                            icon = { label: String -> Icon(Icons.Outlined.Vibration, contentDescription = label) },
                            checked = settings.dragDropFlowHapticEnabled,
                            enabled = settings.dragDropFlowEnabled,
                            onCheckedChange = onHapticChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("actions_inline") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_inline_actions_title),
                            subtitle = stringResource(R.string.drag_drop_flow_inline_actions_summary),
                            icon = { label: String -> Icon(Icons.Outlined.Apps, contentDescription = label) },
                            checked = settings.dragDropFlowActionsInline,
                            enabled = settings.dragDropFlowEnabled,
                            onCheckedChange = onActionsInlineChange,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "drag_drop_flow_targets_section",
            title = sectionTargets,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_targets",
            items = buildList {
                if (onOpenTextSearchEngines != null) {
                    add(
                        settingsCardScopeItem("text_engines") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_text_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_text_engines_summary),
                                icon = { label: String -> Icon(Icons.Outlined.Search, contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenTextSearchEngines,
                            )
                        }
                    )
                }
                if (onOpenImageSearchEngines != null) {
                    add(
                        settingsCardScopeItem("image_engines") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_image_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_image_engines_summary),
                                icon = { label: String -> Icon(Icons.Outlined.ImageSearch, contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenImageSearchEngines,
                            )
                        }
                    )
                }
                if (onOpenFileShareEngines != null) {
                    add(
                        settingsCardScopeItem("file_targets") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_file_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_file_engines_summary),
                                icon = { label: String -> Icon(Icons.Outlined.FolderShared, contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenFileShareEngines,
                            )
                        }
                    )
                }
            }
        )

        settingsLazyTipCard(
            key = "drag_drop_flow_guide_tip",
            text = guideText,
        )
    }
}
